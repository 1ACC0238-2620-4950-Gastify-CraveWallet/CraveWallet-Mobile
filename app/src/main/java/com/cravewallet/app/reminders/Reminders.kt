package com.cravewallet.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cravewallet.app.MainActivity
import com.cravewallet.app.R
import com.cravewallet.app.data.AppState
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.data.LeadTime
import com.cravewallet.app.data.ReminderSettings
import com.cravewallet.app.data.Subscription
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TimeZone

/** Próximo aviso calculado para una suscripción. */
data class UpcomingReminder(val subscription: Subscription, val at: LocalDateTime)

object ReminderPlanner {
    fun reminderTime(sub: Subscription, settings: ReminderSettings): LocalDateTime {
        val lead = sub.leadTime ?: settings.leadTime
        return sub.nextCharge.minusDays(lead.days).atTime(settings.hour, settings.minute)
    }

    fun reminderTime(nextCharge: LocalDate, lead: LeadTime, settings: ReminderSettings): LocalDateTime =
        nextCharge.minusDays(lead.days).atTime(settings.hour, settings.minute)

    fun upcoming(state: AppState): List<UpcomingReminder> =
        state.active.map { UpcomingReminder(it, reminderTime(it, state.reminders)) }.sortedBy { it.at }
}

object Notifications {
    const val CHANNEL_ID = "reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_reminders_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** El usuario puede tener el permiso pero haber apagado las notificaciones en Ajustes. */
    fun areEnabled(context: Context): Boolean =
        hasPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun show(context: Context, id: Int, title: String, text: String, subscriptionId: String) {
        if (!areEnabled(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_SUBSCRIPTION_ID, subscriptionId)
        }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications_active_fill)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // El permiso se revocó entre la verificación y el aviso.
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val name = inputData.getString(KEY_NAME) ?: return Result.failure()
        val amount = inputData.getString(KEY_AMOUNT).orEmpty()
        val chargeDate = LocalDate.parse(inputData.getString(KEY_DATE))
        val days = ChronoUnit.DAYS.between(LocalDate.now(), chargeDate)
        val whenText = when (days) {
            0L -> "Hoy"
            1L -> "Mañana"
            else -> "En $days días"
        }
        Notifications.show(
            applicationContext,
            id.hashCode(),
            "$whenText te cobran $name",
            "$amount el ${Fmt.dayMonth(chargeDate)}. ¿Lo dejamos pasar? Revísalo antes del cobro.",
            id,
        )
        return Result.success()
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_AMOUNT = "amount"
        const val KEY_DATE = "date"
    }
}

/** Programa (o cancela) los avisos push con WorkManager según los ajustes actuales. */
object ReminderScheduler {
    private fun workName(id: String) = "reminder-$id"

    fun syncAll(context: Context, state: AppState) {
        val wm = WorkManager.getInstance(context)
        state.subscriptions.forEach { sub ->
            if (!state.reminders.pushEnabled || sub.cancelled) {
                wm.cancelUniqueWork(workName(sub.id))
            } else {
                schedule(context, state, sub)
            }
        }
    }

    fun schedule(context: Context, state: AppState, sub: Subscription) {
        val at = ReminderPlanner.reminderTime(sub, state.reminders)
        val delay = Duration.between(LocalDateTime.now(), at)
        val wm = WorkManager.getInstance(context)
        if (delay.isNegative) {
            wm.cancelUniqueWork(workName(sub.id))
            return
        }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_ID to sub.id,
                    ReminderWorker.KEY_NAME to sub.name,
                    ReminderWorker.KEY_AMOUNT to Fmt.pen(state.pen(sub)),
                    ReminderWorker.KEY_DATE to sub.nextCharge.toString(),
                ),
            )
            .build()
        wm.enqueueUniqueWork(workName(sub.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }
}

/** Crea un único evento por cobro en el calendario del teléfono. No lee ni cambia otros eventos. */
object CalendarHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    data class CalendarInfo(val id: Long, val account: String, val name: String)

    fun primaryCalendar(context: Context): CalendarInfo? {
        if (!hasPermission(context)) return null
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection,
                "${CalendarContract.Calendars.VISIBLE} = 1", null, null,
            )?.use { c ->
                var best: CalendarInfo? = null
                while (c.moveToNext()) {
                    val access = c.getInt(4)
                    if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                    val info = CalendarInfo(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty())
                    if (c.getInt(3) == 1) return@use info
                    if (best == null) best = info
                }
                best
            }
        }.getOrNull()
    }

    private const val LOCAL_ACCOUNT = "CraveWallet"

    /**
     * Si el teléfono no tiene ninguna cuenta de calendario (p. ej. un emulador sin cuenta de Google),
     * crea un calendario local "CraveWallet" para poder agendar los cobros igual.
     */
    private fun createLocalCalendar(context: Context): CalendarInfo? = runCatching {
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, LOCAL_ACCOUNT)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, LOCAL_ACCOUNT)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF3B4FD8.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        val id = context.contentResolver.insert(uri, values)?.lastPathSegment?.toLong() ?: return@runCatching null
        CalendarInfo(id, "en este teléfono", LOCAL_ACCOUNT)
    }.getOrNull()

    /** Calendario donde se guardan los eventos: el principal del teléfono o, si no hay, uno local. */
    fun targetCalendar(context: Context): CalendarInfo? =
        primaryCalendar(context) ?: if (hasPermission(context)) createLocalCalendar(context) else null

    /** Inserta el evento y devuelve su id, o null si no hay calendario disponible. */
    fun insertEvent(context: Context, sub: Subscription, amountText: String, at: LocalDateTime): Long? {
        val calendar = targetCalendar(context) ?: return null
        val start = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendar.id)
            put(CalendarContract.Events.TITLE, "Cobro de ${sub.name}: $amountText")
            put(
                CalendarContract.Events.DESCRIPTION,
                "CraveWallet: ${sub.name} se cobra el ${Fmt.weekdayDate(sub.nextCharge)}. Revísalo antes del cobro.",
            )
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, start + 30 * 60 * 1000)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        return runCatching {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
            val eventId = uri.lastPathSegment?.toLong() ?: return null
            context.contentResolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, 0)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                },
            )
            eventId
        }.getOrNull()
    }

    fun deleteEvent(context: Context, eventId: Long?) {
        if (eventId == null || !hasPermission(context)) return
        runCatching {
            context.contentResolver.delete(
                android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null,
            )
        }
    }
}
