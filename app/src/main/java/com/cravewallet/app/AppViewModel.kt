package com.cravewallet.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cravewallet.app.data.Connectivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import com.cravewallet.app.data.AccessMode
import com.cravewallet.app.data.AppState
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.data.LeadTime
import com.cravewallet.app.data.Profile
import com.cravewallet.app.data.ReminderSettings
import com.cravewallet.app.data.Subscription
import com.cravewallet.app.reminders.CalendarHelper
import com.cravewallet.app.reminders.ReminderPlanner
import com.cravewallet.app.reminders.ReminderScheduler
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime

/** Acciones de la app. Cada cambio de datos mantiene sincronizados los avisos push y el calendario. */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as CraveApplication).repository
    private val context get() = getApplication<Application>()

    val state: StateFlow<AppState> = repo.state
    val access = repo.access
    val connected get() = repo.connected
    val serverUrl get() = repo.api.baseUrl
    private val operations = Mutex()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value=null }
    private fun operation(block: suspend () -> Unit) {
        viewModelScope.launch {
            operations.withLock {
                _busy.value=true; _error.value=null
                try { block() }
                catch(e: CancellationException) { throw e }
                catch(e: Exception) { repo.checkSession(); _error.value=e.message ?: "No se pudo conectar. Comprueba el servidor e inténtalo nuevamente." }
                finally { _busy.value=false }
            }
        }
    }
    fun authenticate(email: String,password: String,register: Boolean,url: String) = operation {
        repo.api.configure(url); repo.authenticate(email.trim(),password,register)
    }
    fun demo() { repo.demo(); ReminderScheduler.syncAll(context,repo.current); refreshRates() }
    fun logout() = operation {
        val ids=repo.current.subscriptions.map {it.id}
        repo.logout()
        ids.forEach {ReminderScheduler.cancel(context,it)}
    }
    fun refresh() = operation { repo.refresh(); ReminderScheduler.syncAll(context,repo.current) }
    fun saveAsync(sub: Subscription,lead: LeadTime?,calendar: Boolean,onSuccess:(Boolean)->Unit) = operation {
        val saved=if(connected) repo.saveRemote(sub) else sub
        if(connected) repo.markTotalsPending()
        val created=save(saved,lead,calendar)
        if(connected) {
            // The mutation is confirmed; failed totals refresh must not turn it into a false failed save.
            try {repo.refresh()} catch(e:CancellationException) {throw e} catch(e:Exception) {_error.value="Guardado. No se pudo actualizar el resumen; pulsa Actualizar."}
        }
        onSuccess(created)
    }
    fun cancel(id:String,onSuccess:()->Unit) = operation {
        val previousEvent=repo.subscription(id)?.calendarEventId
        if(connected) repo.cancelRemote(id) else setCancelled(id,true)
        CalendarHelper.deleteEvent(context,previousEvent)
        ReminderScheduler.cancel(context,id)
        if(connected) { try {repo.refresh()} catch(e:CancellationException) {throw e} catch(e:Exception) {_error.value="Cancelada. No se pudo actualizar el resumen."} }
        onSuccess()
    }
    private suspend fun backendRequest(method:String,path:String,body:org.json.JSONObject?=null):org.json.JSONObject {
        try {return repo.api.request(method,path,body)} catch(e:Exception) {repo.checkSession();throw e}
    }
    suspend fun deliverySummary(year:Int,month:Int) = backendRequest("GET","/api/v1/delivery-expenses/summary?year=$year&month=$month")
    suspend fun deliveryBudget(year:Int,month:Int,limit:Double?) = backendRequest("PUT","/api/v1/delivery-expenses/budget",org.json.JSONObject().put("year",year).put("month",month).put("spendingLimit",limit ?: org.json.JSONObject.NULL))
    suspend fun deliveryExpense(requestId:String,merchant:String,amount:Double,category:String,date:String) = backendRequest("POST","/api/v1/delivery-expenses",org.json.JSONObject().put("requestId",requestId).put("merchant",merchant).put("amount",java.math.BigDecimal.valueOf(amount)).put("category",category).put("expenseDate",date))

    init {
        if(connected) refresh()
        else if(repo.access.value == AccessMode.DEMO) { ReminderScheduler.syncAll(context,repo.current); refreshRates() }
    }

    fun subscription(id: String): Subscription? = repo.subscription(id)

    fun newId(): String = repo.newId()

    val canAddMore: Boolean
        get() = repo.current.profile.premium || repo.current.active.size < AppState.FREE_PLAN_LIMIT

    /**
     * Guarda una suscripción nueva o editada.
     * @param addToCalendar crea el evento en el calendario (requiere permiso ya concedido).
     * @return true si el evento de calendario se creó.
     */
    private fun save(sub: Subscription, lead: LeadTime?, addToCalendar: Boolean): Boolean {
        val previous = repo.subscription(sub.id)
        CalendarHelper.deleteEvent(context, previous?.calendarEventId)
        var toSave = sub.copy(leadTime = lead, calendarEventId = null)
        var created = false
        if (addToCalendar && CalendarHelper.hasPermission(context)) {
            val state = repo.current
            val at = ReminderPlanner.reminderTime(toSave, state.reminders)
            val eventId = CalendarHelper.insertEvent(context, toSave, Fmt.pen(state.pen(toSave)), at)
            if (eventId != null) {
                toSave = toSave.copy(calendarEventId = eventId)
                created = true
            }
        }
        repo.upsert(toSave)
        syncReminder(toSave)
        return created
    }

    /** Deshace un alta: borra la suscripción y su evento. */
    fun undoAdd(id: String) {
        val sub = repo.subscription(id) ?: return
        CalendarHelper.deleteEvent(context, sub.calendarEventId)
        ReminderScheduler.cancel(context, id)
        repo.remove(id)
    }

    fun restore(sub: Subscription) {
        repo.upsert(sub)
        syncReminder(sub)
    }

    fun delete(id: String) {
        if(connected) { _error.value="La eliminación no está disponible. Puedes cancelar la suscripción para conservar su registro."; return }
        val sub = repo.subscription(id) ?: return
        CalendarHelper.deleteEvent(context, sub.calendarEventId)
        ReminderScheduler.cancel(context, id)
        repo.remove(id)
    }

    fun setUnused(id: String, unused: Boolean) = repo.setUnused(id, unused)

    fun setCancelled(id: String, cancelled: Boolean) {
        if(connected) { if(cancelled) cancel(id) {} else _error.value="La reactivación no está disponible en el servidor."; return }
        val sub = repo.subscription(id) ?: return
        if (cancelled) {
            CalendarHelper.deleteEvent(context, sub.calendarEventId)
            repo.update(id) { it.copy(cancelled = true, calendarEventId = null) }
            ReminderScheduler.cancel(context, id)
        } else {
            repo.setCancelled(id, false)
            repo.subscription(id)?.let { syncReminder(it) }
        }
    }

    /**
     * Guarda los ajustes de recordatorios. La anticipación elegida aquí aplica a todas las suscripciones
     * activas, y los eventos de calendario se crean, mueven o borran según corresponda.
     */
    fun saveReminders(settings: ReminderSettings) {
        val previous = repo.current.reminders
        val timingChanged = previous.leadTime != settings.leadTime || previous.hour != settings.hour || previous.minute != settings.minute
        repo.setReminders(settings)
        repo.current.active.forEach { sub ->
            val base = if (previous.leadTime != settings.leadTime) sub.copy(leadTime = null) else sub
            val updated = when {
                !settings.calendarEnabled -> {
                    CalendarHelper.deleteEvent(context, sub.calendarEventId)
                    base.copy(calendarEventId = null)
                }
                CalendarHelper.hasPermission(context) && (sub.calendarEventId == null || timingChanged) -> {
                    CalendarHelper.deleteEvent(context, sub.calendarEventId)
                    val state = repo.current
                    val at = ReminderPlanner.reminderTime(base, settings)
                    base.copy(calendarEventId = CalendarHelper.insertEvent(context, base, Fmt.pen(state.pen(base)), at))
                }
                else -> base
            }
            if (updated != sub) repo.upsert(updated)
        }
        ReminderScheduler.syncAll(context, repo.current)
    }

    fun setPremium(premium: Boolean) { if(connected) _error.value="Premium todavía no está disponible." else repo.setPremium(premium) }

    fun setProfile(profile: Profile) { if(connected) _error.value="La edición de nombre y correo aún no está disponible." else repo.setProfile(profile) }

    fun setUsdRate(rate: Double) { if(!connected) repo.setRates(repo.current.rates.copy(usd=rate,updatedAt=LocalDateTime.now())) }

    /** Descarga el tipo de cambio del día. Si no hay conexión, se mantiene el último guardado. */
    fun refreshRates(onResult: (Boolean) -> Unit = {}) {
        if(connected) {
            operation {
                try {repo.refresh();onResult(repo.current.rates.usd.isFinite())}
                catch(e:CancellationException) {throw e}
                catch(e:Exception) {onResult(false);throw e}
            }
            return
        }
        viewModelScope.launch {
            val rates=Connectivity.fetchRates()
            if(repo.access.value == AccessMode.DEMO) {
                if(rates!=null) repo.setRates(rates)
                onResult(rates!=null)
            }
        }
    }

    fun resetDemo() {
        if(connected) return
        repo.current.subscriptions.forEach { ReminderScheduler.cancel(context, it.id) }
        repo.resetDemo()
        ReminderScheduler.syncAll(context, repo.current)
    }

    fun startEmpty() {
        if(connected) return
        repo.current.subscriptions.forEach { ReminderScheduler.cancel(context, it.id) }
        repo.startEmpty()
    }

    private fun syncReminder(sub: Subscription) {
        val state = repo.current
        if (state.reminders.pushEnabled && !sub.cancelled) ReminderScheduler.schedule(context, state, sub)
        else ReminderScheduler.cancel(context, sub.id)
    }
}
