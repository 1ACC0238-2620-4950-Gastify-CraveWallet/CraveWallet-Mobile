package com.cravewallet.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cravewallet.app.data.Connectivity
import kotlinx.coroutines.launch
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

    init {
        ReminderScheduler.syncAll(context, repo.current)
        refreshRates()
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
    fun save(sub: Subscription, lead: LeadTime?, addToCalendar: Boolean): Boolean {
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
        val sub = repo.subscription(id) ?: return
        CalendarHelper.deleteEvent(context, sub.calendarEventId)
        ReminderScheduler.cancel(context, id)
        repo.remove(id)
    }

    fun setUnused(id: String, unused: Boolean) = repo.setUnused(id, unused)

    fun setCancelled(id: String, cancelled: Boolean) {
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

    fun setPremium(premium: Boolean) = repo.setPremium(premium)

    fun setProfile(profile: Profile) = repo.setProfile(profile)

    fun setUsdRate(rate: Double) = repo.setRates(repo.current.rates.copy(usd = rate, updatedAt = LocalDateTime.now()))

    /** Descarga el tipo de cambio del día. Si no hay conexión, se mantiene el último guardado. */
    fun refreshRates(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val rates = Connectivity.fetchRates()
            if (rates != null) repo.setRates(rates)
            onResult(rates != null)
        }
    }

    fun resetDemo() {
        repo.current.subscriptions.forEach { ReminderScheduler.cancel(context, it.id) }
        repo.resetDemo()
        ReminderScheduler.syncAll(context, repo.current)
    }

    fun startEmpty() {
        repo.current.subscriptions.forEach { ReminderScheduler.cancel(context, it.id) }
        repo.startEmpty()
    }

    private fun syncReminder(sub: Subscription) {
        val state = repo.current
        if (state.reminders.pushEnabled && !sub.cancelled) ReminderScheduler.schedule(context, state, sub)
        else ReminderScheduler.cancel(context, sub.id)
    }
}
