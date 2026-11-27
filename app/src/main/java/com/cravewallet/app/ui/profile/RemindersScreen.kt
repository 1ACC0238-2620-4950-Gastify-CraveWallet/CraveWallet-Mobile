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
import androidx.compose.ui.res.stringResource
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

/** P2–P9 · Reminders: channels, lead time, time of day, and upcoming reminders. */
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

    // When returning from system Settings, re-check permissions
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
            else actions.snack(context.getString(R.string.reminders_no_push_perm_snack))
        }
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        calendarAllowed = result.values.all { it }
        if (calendarAllowed) draft = draft.copy(calendarEnabled = true)
        else actions.snack(context.getString(R.string.reminders_no_cal_perm_snack))
    }

    fun setPush(on: Boolean) {
        justSaved = false
        if (!on) { draft = draft.copy(pushEnabled = false); return }
        when {
            notificationsAllowed -> draft = draft.copy(pushEnabled = true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.hasPermission(context) -> pushDialog = true
            else -> blocked = true // permission granted but notifications disabled in system Settings
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
        val text = if (lead == LeadTime.SAME_DAY) context.getString(R.string.reminders_saved_same_day_snack)
        else context.getString(R.string.reminders_saved_lead_snack, lead.label(context).substringBefore(" (").lowercase())
        actions.snack(text, context.getString(R.string.common_undo)) {
            vm.saveReminders(previous)
            draft = previous
            justSaved = false
        }
    }

    BackHandler(enabled = dirty) { leaveDialog = true }

    Column(Modifier.fillMaxSize()) {
        CwTopBar(stringResource(R.string.reminders_title), onBack = { if (dirty) leaveDialog = true else actions.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                stringResource(R.string.reminders_intro),
                style = CwType.Body,
                color = OnSurface,
            )

            if (blocked && !notificationsAllowed) {
                InfoBanner(
                    icon = R.drawable.ic_notifications_off,
                    title = stringResource(R.string.reminders_blocked_title),
                    body = stringResource(R.string.reminders_blocked_body) +
                        if (draft.calendarEnabled) stringResource(R.string.reminders_blocked_cal_active_suffix) else "",
                    container = Warning.copy(alpha = 0.25f),
                    modifier = Modifier.padding(top = 12.dp),
                    actions = {
                        LinkText(stringResource(R.string.reminders_open_settings), onClick = ::openNotificationSettings)
                        LinkText(stringResource(R.string.common_not_now), onClick = { blocked = false })
                    },
                )
            }

            Label(stringResource(R.string.reminders_group_channels))
            CwCard(Modifier.fillMaxWidth()) {
                val calendarInfo = remember(calendarAllowed) { CalendarHelper.primaryCalendar(context) }
                ListRow(
                    stringResource(R.string.reminders_cal_row_title),
                    subtitle = when {
                        !calendarAllowed -> stringResource(R.string.reminders_cal_no_access)
                        calendarInfo != null && calendarInfo.account == calendarInfo.name -> stringResource(R.string.reminders_cal_on_this_phone, calendarInfo.name)
                        calendarInfo != null -> "${calendarInfo.name.ifBlank { stringResource(R.string.reminders_cal_default_name) }} · ${calendarInfo.account}"
                        else -> stringResource(R.string.reminders_cal_will_create)
                    },
                    icon = R.drawable.ic_calendar_month,
                    onClick = { setCalendar(!draft.calendarEnabled) },
                    trailing = { CwSwitch(draft.calendarEnabled && calendarAllowed, onCheckedChange = ::setCalendar) },
                )
                RowDivider()
                ListRow(
                    stringResource(R.string.reminders_push_row_title),
                    subtitle = when {
                        !notificationsAllowed && blocked -> stringResource(R.string.reminders_push_blocked_sub)
                        draft.pushEnabled && notificationsAllowed -> stringResource(R.string.reminders_push_active_sub)
                        else -> stringResource(R.string.reminders_push_inactive_sub)
                    },
                    icon = if (draft.pushEnabled && notificationsAllowed) R.drawable.ic_notifications_active else R.drawable.ic_notifications_off,
                    onClick = { setPush(!draft.pushEnabled) },
                    trailing = { CwSwitch(draft.pushEnabled && notificationsAllowed, onCheckedChange = ::setPush) },
                )
            }

            Label(stringResource(R.string.reminders_group_lead))
            CwCard(Modifier.fillMaxWidth()) {
                ListRow(stringResource(R.string.reminders_when_row_title), subtitle = stringResource(draft.leadTime.shortRes), icon = R.drawable.ic_alarm, onClick = { leadSheet = true })
                RowDivider()
                ListRow(stringResource(R.string.reminders_time_row_title), subtitle = Fmt.time(draft.hour, draft.minute, context), icon = R.drawable.ic_schedule, onClick = { timeDialog = true })
            }

            val upcoming = ReminderPlanner.upcoming(state.copy(reminders = draft))
            Label(stringResource(R.string.reminders_group_upcoming, upcoming.size))
            CwCard(Modifier.fillMaxWidth()) {
                if (upcoming.isEmpty()) {
                    Text(stringResource(R.string.reminders_empty_active), style = CwType.Body, color = OnSurfaceVariant, modifier = Modifier.padding(16.dp))
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
                                if (past) stringResource(R.string.reminders_item_past, Fmt.shortWeekday(u.at.toLocalDate()))
                                else stringResource(R.string.reminders_item_upcoming, Fmt.shortWeekday(u.at.toLocalDate()), Fmt.time(u.at.hour, u.at.minute, context)),
                                style = CwType.Caption,
                                color = OnSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.pen(state.pen(u.subscription), context), style = CwType.BodyStrong, color = OnSurface)
                            Text(stringResource(R.string.reminders_item_charge_date, Fmt.dayMonth(u.subscription.nextCharge)), style = CwType.Caption, color = OnSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Column(Modifier.background(Surface)) {
            HorizontalDivider(color = OutlineVariant)
            PrimaryButton(
                text = stringResource(if (justSaved && !dirty) R.string.reminders_btn_saved else R.string.reminders_btn_save),
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
            title = { Text(stringResource(R.string.reminders_push_perm_title), style = CwType.Heading, color = OnSurface, textAlign = TextAlign.Center) },
            text = { Text(stringResource(R.string.reminders_push_perm_body), style = CwType.Body, color = OnSurface, textAlign = TextAlign.Center) },
            confirmButton = {
                Column(Modifier.fillMaxWidth()) {
                    PrimaryButton(stringResource(R.string.common_allow), height = 48.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), onClick = {
                        pushDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(stringResource(R.string.reminders_push_perm_deny), height = 48.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), onClick = { pushDialog = false })
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
            title = { Text(stringResource(R.string.reminders_time_row_title), style = CwType.Heading, color = OnSurface) },
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
                CwTextButton(stringResource(R.string.common_accept), color = Primary, onClick = {
                    draft = draft.copy(hour = timeState.hour, minute = timeState.minute)
                    justSaved = false
                    timeDialog = false
                })
            },
            dismissButton = { CwTextButton(stringResource(R.string.common_cancel), onClick = { timeDialog = false }) },
        )
    }

    if (leaveDialog) {
        ConfirmDialog(
            title = stringResource(R.string.reminders_leave_title),
            body = stringResource(R.string.reminders_leave_body),
            confirm = stringResource(R.string.reminders_leave_confirm),
            dismiss = stringResource(R.string.reminders_leave_stay),
            onConfirm = { leaveDialog = false; draft = saved; actions.back() },
            onDismiss = { leaveDialog = false },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = CwType.Body, color = OnSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

/** P5 / P6 · Choose lead time. */
@Composable
private fun LeadTimeSheet(current: LeadTime, onApply: (LeadTime) -> Unit, onDismiss: () -> Unit) {
    var choice by remember { mutableStateOf(current) }
    SheetOverlay(onDismissRequest = onDismiss, fullHeight = false) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.reminders_when_row_title), style = CwType.Title, color = OnSurface)
            Text(stringResource(R.string.reminders_lead_sheet_subtitle), style = CwType.Caption, color = OnSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            LeadTime.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable(role = Role.RadioButton) { choice = option },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(option.labelRes), style = CwType.Body, color = OnSurface, modifier = Modifier.weight(1f))
                    RadioButton(
                        selected = choice == option,
                        onClick = { choice = option },
                        colors = RadioButtonDefaults.colors(selectedColor = Primary, unselectedColor = OnSurfaceVariant),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            InfoBanner(icon = R.drawable.ic_info, body = stringResource(R.string.reminders_lead_sheet_tip))
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.common_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.common_apply), onClick = { onApply(choice) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
