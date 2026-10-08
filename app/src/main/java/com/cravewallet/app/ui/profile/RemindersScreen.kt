package com.cravewallet.app.ui.profile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cravewallet.app.AppViewModel
import com.cravewallet.app.R
import com.cravewallet.app.data.AppState
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.data.LeadTime
import com.cravewallet.app.reminders.CalendarHelper
import com.cravewallet.app.reminders.Notifications
import com.cravewallet.app.reminders.ReminderPlanner
import com.cravewallet.app.ui.AppActions
import com.cravewallet.app.ui.add.CalendarPermissionDialog
import com.cravewallet.app.ui.components.CwCard
import com.cravewallet.app.ui.components.CwIcon
import com.cravewallet.app.ui.components.CwSwitch
import com.cravewallet.app.ui.components.CwTextButton
import com.cravewallet.app.ui.components.CwTopBar
import com.cravewallet.app.ui.components.DialogIcon
import com.cravewallet.app.ui.components.InfoBanner
import com.cravewallet.app.ui.components.LinkText
import com.cravewallet.app.ui.components.ListRow
import com.cravewallet.app.ui.components.PrimaryButton
import com.cravewallet.app.ui.components.RowDivider
import com.cravewallet.app.ui.components.SecondaryButton
import com.cravewallet.app.ui.components.SheetOverlay
import com.cravewallet.app.ui.expenses.ConfirmDialog
import com.cravewallet.app.ui.theme.CwType
import com.cravewallet.app.ui.theme.OnSurface
import com.cravewallet.app.ui.theme.OnSurfaceVariant
import com.cravewallet.app.ui.theme.OutlineVariant
import com.cravewallet.app.ui.theme.Primary
import com.cravewallet.app.ui.theme.Surface
import com.cravewallet.app.ui.theme.SurfaceVariant
import com.cravewallet.app.ui.theme.Warning

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** P2–P9 · Recordatorios: canales, anticipación, hora y próximos avisos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(state: AppState, vm: AppViewModel, actions: AppActions) {
    val context = LocalContext.current
    val saved = state.reminders
    var draft by remember { mutableStateOf(saved) }
    var justSaved by remember { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(Notifications.areEnabled(context)) }
    var calendarAllowed by remember { mutableStateOf(CalendarHelper.hasPermission(context)) }
    var blocked by remember { mutableStateOf(false) }
    var pushDialog by remember { mutableStateOf(false) }
    var calendarDialog by remember { mutableStateOf(false) }
    var leadSheet by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    var leaveDialog by remember { mutableStateOf(false) }
    val dirty = draft != saved

    // Al volver de Ajustes del sistema, revisar de nuevo los permisos
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = Notifications.areEnabled(context)
        calendarAllowed = CalendarHelper.hasPermission(context)
        if (notificationsAllowed) blocked = false
        else if (saved.pushEnabled) blocked = true
        onPauseOrDispose { }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = Notifications.areEnabled(context)
        if (granted && notificationsAllowed) {
            draft = draft.copy(pushEnabled = true)
            justSaved = false
        } else {
            val activity = context.findActivity()
            val canAskAgain = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
            if (!canAskAgain) blocked = true
            else actions.snack("Sin permiso no podemos enviarte avisos push.")
        }
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        calendarAllowed = result.values.all { it }
        if (calendarAllowed) draft = draft.copy(calendarEnabled = true)
        else actions.snack("Sin acceso al calendario. Te avisaremos solo por notificación.")
    }

    fun setPush(on: Boolean) {
        justSaved = false
        if (!on) { draft = draft.copy(pushEnabled = false); return }
        when {
            notificationsAllowed -> draft = draft.copy(pushEnabled = true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.hasPermission(context) -> pushDialog = true
            else -> blocked = true // permiso concedido pero notificaciones apagadas en Ajustes
        }
    }

    fun setCalendar(on: Boolean) {
        justSaved = false
        if (!on) { draft = draft.copy(calendarEnabled = false); return }
        if (calendarAllowed) draft = draft.copy(calendarEnabled = true) else calendarDialog = true
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent) }
    }

    fun save() {
        val previous = saved
        vm.saveReminders(draft)
        justSaved = true
        val lead = draft.leadTime
        val text = if (lead == LeadTime.SAME_DAY) "Listo. Te avisaremos el mismo día de cada cobro."
        else "Listo. Te avisaremos ${lead.label.substringBefore(" (").lowercase()} de cada cobro."
        actions.snack(text, "Deshacer") {
            vm.saveReminders(previous)
            draft = previous
            justSaved = false
        }
    }

    BackHandler(enabled = dirty) { leaveDialog = true }

    Column(Modifier.fillMaxSize()) {
        CwTopBar("Recordatorios", onBack = { if (dirty) leaveDialog = true else actions.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Te avisamos antes de cada cobro para que decidas a tiempo si lo pagas o lo cancelas.",
                style = CwType.Body,
                color = OnSurface,
            )

            if (blocked && !notificationsAllowed) {
                InfoBanner(
                    icon = R.drawable.ic_notifications_off,
                    title = "Las notificaciones están bloqueadas",
                    body = "Actívalas en los ajustes del teléfono para recibir avisos push." +
                        if (draft.calendarEnabled) " El evento en tu calendario sigue activo." else "",
                    container = Warning.copy(alpha = 0.25f),
                    modifier = Modifier.padding(top = 12.dp),
                    actions = {
                        LinkText("Abrir ajustes", onClick = ::openNotificationSettings)
                        LinkText("Ahora no", onClick = { blocked = false })
                    },
                )
            }

            Label("Canales")
            CwCard(Modifier.fillMaxWidth()) {
                val calendarInfo = remember(calendarAllowed) { CalendarHelper.primaryCalendar(context) }
                ListRow(
                    "Evento en calendario",
                    subtitle = when {
                        !calendarAllowed -> "Sin acceso al calendario del teléfono"
                        calendarInfo != null && calendarInfo.account == calendarInfo.name -> "${calendarInfo.name} · en este teléfono"
                        calendarInfo != null -> "${calendarInfo.name.ifBlank { "Calendario" }} · ${calendarInfo.account}"
                        else -> "Crearemos el calendario «CraveWallet» en este teléfono"
                    },
                    icon = R.drawable.ic_calendar_month,
                    onClick = { setCalendar(!draft.calendarEnabled) },
                    trailing = { CwSwitch(draft.calendarEnabled && calendarAllowed, onCheckedChange = ::setCalendar) },
                )
                RowDivider()
                ListRow(
                    "Notificación push",
                    subtitle = when {
                        !notificationsAllowed && blocked -> "Bloqueada en los ajustes del teléfono"
                        draft.pushEnabled && notificationsAllowed -> "Activada en este teléfono"
                        else -> "Desactivada"
                    },
                    icon = if (draft.pushEnabled && notificationsAllowed) R.drawable.ic_notifications_active else R.drawable.ic_notifications_off,
                    onClick = { setPush(!draft.pushEnabled) },
                    trailing = { CwSwitch(draft.pushEnabled && notificationsAllowed, onCheckedChange = ::setPush) },
                )
            }

            Label("Anticipación")
            CwCard(Modifier.fillMaxWidth()) {
                ListRow("¿Cuándo avisarte?", subtitle = draft.leadTime.short, icon = R.drawable.ic_alarm, onClick = { leadSheet = true })
                RowDivider()
                ListRow("Hora del aviso", subtitle = Fmt.time(draft.hour, draft.minute), icon = R.drawable.ic_schedule, onClick = { timeDialog = true })
            }

            val upcoming = ReminderPlanner.upcoming(state.copy(reminders = draft))
            Label("Próximos avisos (${upcoming.size})")
            CwCard(Modifier.fillMaxWidth()) {
                if (upcoming.isEmpty()) {
                    Text("No tienes suscripciones activas.", style = CwType.Body, color = OnSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
                upcoming.forEachIndexed { i, u ->
                    if (i > 0) RowDivider()
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) { CwIcon(R.drawable.ic_alarm, size = 22.dp) }
                        Column(Modifier.weight(1f)) {
                            Text(u.subscription.name, style = CwType.BodyStrong, color = OnSurface)
                            val past = u.at.isBefore(java.time.LocalDateTime.now())
                            Text(
                                if (past) "El aviso era el ${Fmt.shortWeekday(u.at.toLocalDate())}: ya pasó"
                                else "Aviso ${Fmt.shortWeekday(u.at.toLocalDate())}, ${Fmt.time(u.at.hour, u.at.minute)}",
                                style = CwType.Caption,
                                color = OnSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.pen(state.pen(u.subscription)), style = CwType.BodyStrong, color = OnSurface)
                            Text("cobro ${Fmt.dayMonth(u.subscription.nextCharge)}", style = CwType.Caption, color = OnSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Column(Modifier.background(Surface)) {
            HorizontalDivider(color = OutlineVariant)
            PrimaryButton(
                text = if (justSaved && !dirty) "Cambios guardados" else "Guardar cambios",
                icon = if (justSaved && !dirty) R.drawable.ic_check else null,
                onClick = ::save,
                enabled = dirty,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }

    if (pushDialog) {
        AlertDialog(
            onDismissRequest = { pushDialog = false },
            containerColor = Surface,
            shape = RoundedCornerShape(24.dp),
            icon = { DialogIcon(R.drawable.ic_notifications) },
            title = { Text("¿Permitir que CraveWallet te envíe notificaciones?", style = CwType.Heading, color = OnSurface, textAlign = TextAlign.Center) },
            text = { Text("Te avisaremos antes de cada cobro, aunque no abras tu calendario.", style = CwType.Body, color = OnSurface, textAlign = TextAlign.Center) },
            confirmButton = {
                Column(Modifier.fillMaxWidth()) {
                    PrimaryButton("Permitir", height = 48.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), onClick = {
                        pushDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("No permitir", height = 48.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), onClick = { pushDialog = false })
                }
            },
        )
    }

    if (calendarDialog) {
        CalendarPermissionDialog(
            onAllow = {
                calendarDialog = false
                calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            },
            onLater = { calendarDialog = false },
        )
    }

    if (leadSheet) {
        LeadTimeSheet(
            current = draft.leadTime,
            onApply = { draft = draft.copy(leadTime = it); justSaved = false; leadSheet = false },
            onDismiss = { leadSheet = false },
        )
    }

    if (timeDialog) {
        val timeState = rememberTimePickerState(initialHour = draft.hour, initialMinute = draft.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            containerColor = Surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Hora del aviso", style = CwType.Heading, color = OnSurface) },
            text = {
                TimePicker(
                    state = timeState,
                    colors = TimePickerDefaults.colors(
                        selectorColor = Primary,
                        timeSelectorSelectedContainerColor = com.cravewallet.app.ui.theme.PrimaryContainer,
                        periodSelectorSelectedContainerColor = com.cravewallet.app.ui.theme.PrimaryContainer,
                    ),
                )
            },
            confirmButton = {
                CwTextButton("Aceptar", color = Primary, onClick = {
                    draft = draft.copy(hour = timeState.hour, minute = timeState.minute)
                    justSaved = false
                    timeDialog = false
                })
            },
            dismissButton = { CwTextButton("Cancelar", onClick = { timeDialog = false }) },
        )
    }

    if (leaveDialog) {
        ConfirmDialog(
            title = "¿Salir sin guardar?",
            body = "Tienes cambios en tus recordatorios que todavía no guardaste.",
            confirm = "Salir sin guardar",
            dismiss = "Seguir aquí",
            onConfirm = { leaveDialog = false; draft = saved; actions.back() },
            onDismiss = { leaveDialog = false },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = CwType.Body, color = OnSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

/** P5 / P6 · Elegir la anticipación. */
@Composable
private fun LeadTimeSheet(current: LeadTime, onApply: (LeadTime) -> Unit, onDismiss: () -> Unit) {
    var choice by remember { mutableStateOf(current) }
    SheetOverlay(onDismissRequest = onDismiss, fullHeight = false) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text("¿Cuándo avisarte?", style = CwType.Title, color = OnSurface)
            Text("Aplica a todas tus suscripciones activas.", style = CwType.Caption, color = OnSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            LeadTime.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable(role = Role.RadioButton) { choice = option },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(option.label, style = CwType.Body, color = OnSurface, modifier = Modifier.weight(1f))
                    RadioButton(
                        selected = choice == option,
                        onClick = { choice = option },
                        colors = RadioButtonDefaults.colors(selectedColor = Primary, unselectedColor = OnSurfaceVariant),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            InfoBanner(icon = R.drawable.ic_info, body = "Para cobros en dólares, 3 días te dan tiempo de revisar tu saldo.")
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Cancelar", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton("Aplicar", onClick = { onApply(choice) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
