package com.cravewallet.app.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cravewallet.app.AppViewModel
import com.cravewallet.app.R
import com.cravewallet.app.data.AppState
import com.cravewallet.app.data.Analytics
import com.cravewallet.app.data.Currency
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.reminders.ReminderPlanner
import com.cravewallet.app.ui.AppActions
import com.cravewallet.app.ui.Routes
import com.cravewallet.app.ui.components.CwCard
import com.cravewallet.app.ui.components.CwIcon
import com.cravewallet.app.ui.components.CwTextButton
import com.cravewallet.app.ui.components.CwTopBar
import com.cravewallet.app.ui.components.ListRow
import com.cravewallet.app.ui.components.OutlinedGroup
import com.cravewallet.app.ui.components.PrimaryButton
import com.cravewallet.app.ui.components.RowDivider
import com.cravewallet.app.ui.components.SecondaryButton
import com.cravewallet.app.ui.components.SectionHeader
import com.cravewallet.app.ui.components.ServiceAvatar
import com.cravewallet.app.ui.components.StatusChip
import com.cravewallet.app.ui.theme.CwType
import com.cravewallet.app.ui.theme.Error
import com.cravewallet.app.ui.theme.OnSurface
import com.cravewallet.app.ui.theme.OnSurfaceVariant
import com.cravewallet.app.ui.theme.OutlineVariant
import com.cravewallet.app.ui.theme.Primary
import com.cravewallet.app.ui.theme.PrimaryContainer
import com.cravewallet.app.ui.theme.Success
import com.cravewallet.app.ui.theme.Surface
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Composable
fun DetailScreen(id: String, state: AppState, vm: AppViewModel, actions: AppActions) {
    val context = LocalContext.current
    val sub = state.subscriptions.firstOrNull { it.id == id }
    LaunchedEffect(sub == null) { if (sub == null) actions.back() }
    if (sub == null) return

    var menu by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val days = ChronoUnit.DAYS.between(today, sub.nextCharge)

    Column(Modifier.fillMaxSize()) {
        CwTopBar(stringResource(R.string.detail_title), onBack = actions::back, actions = {
            IconButton(onClick = { actions.openEdit(sub.id) }) { CwIcon(R.drawable.ic_edit, contentDescription = stringResource(R.string.common_edit)) }
            Box {
                IconButton(onClick = { menu = true }) { CwIcon(R.drawable.ic_more_vert, contentDescription = stringResource(R.string.common_more_options)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = Surface) {
                    if (!sub.cancelled) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (sub.isUnused) R.string.detail_mark_in_use else R.string.detail_mark_unused), style = CwType.Body) },
                            leadingIcon = { CwIcon(if (sub.isUnused) R.drawable.ic_check_circle else R.drawable.ic_visibility_off, size = 20.dp) },
                            onClick = {
                                menu = false
                                vm.setUnused(sub.id, !sub.isUnused)
                            },
                        )
                    }
                    DropdownMenuItem(
                        enabled = !state.backendMode,
                        text = { Text(stringResource(R.string.detail_delete_record), style = CwType.Body, color = Error) },
                        leadingIcon = { CwIcon(R.drawable.ic_delete, size = 20.dp, tint = Error) },
                        onClick = { menu = false; confirmDelete = true },
                    )
                }
            }
        })

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ServiceAvatar(sub.name, sub.category, size = 56.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(sub.name, style = CwType.Title, color = OnSurface)
                    Text("${stringResource(sub.category.labelRes)} · ${stringResource(sub.frequency.labelRes)}", style = CwType.Caption, color = OnSurfaceVariant)
                    StatusChip(state.status(sub))
                }
            }

            Spacer(Modifier.height(20.dp))
            CwCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val whenText = when {
                        sub.cancelled -> stringResource(R.string.detail_when_cancelled)
                        days == 0L -> stringResource(R.string.detail_when_today)
                        days == 1L -> stringResource(R.string.detail_when_tomorrow, Fmt.dayMonth(sub.nextCharge))
                        else -> stringResource(R.string.detail_when_in_days, Fmt.dayMonth(sub.nextCharge), days)
                    }
                    Text(whenText, style = CwType.Caption, color = OnSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(Fmt.pen(state.pen(sub), context), style = CwType.Amount, color = OnSurface)
                    if (sub.currency != Currency.PEN) {
                        Text(
                            stringResource(R.string.detail_rate_formula, Fmt.money(sub.amount, sub.currency, context), Fmt.rate(state.rates.rate(sub.currency), context)),
                            style = CwType.Caption,
                            fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant,
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = OutlineVariant)
                    val variation = Analytics.variation(state, sub)
                    if (variation != null && sub.currency != Currency.PEN) {
                        val (diff, last) = variation
                        val up = diff > 0.005
                        val same = abs(diff) <= 0.005
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CwIcon(if (up) R.drawable.ic_trending_up else R.drawable.ic_trending_down, size = 20.dp, tint = if (up) Error else Success)
                            Text(
                                when {
                                    same -> stringResource(R.string.detail_var_same, Fmt.rate(last.rate, context))
                                    up -> stringResource(R.string.detail_var_more, Fmt.pen(diff, context), Fmt.rate(last.rate, context))
                                    else -> stringResource(R.string.detail_var_less, Fmt.pen(-diff, context), Fmt.rate(last.rate, context))
                                },
                                style = CwType.Caption,
                                color = OnSurface,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CwIcon(R.drawable.ic_sync, size = 20.dp, tint = OnSurfaceVariant)
                        Text(
                            if (sub.currency == Currency.PEN) stringResource(R.string.detail_pen_no_fx)
                            else stringResource(R.string.detail_fx_updated, Fmt.relativeDateTime(state.rates.updatedAt, context = context)),
                            style = CwType.Caption,
                            color = OnSurfaceVariant,
                        )
                    }
                }
            }

            if (!sub.cancelled) {
                Spacer(Modifier.height(16.dp))
                val at = ReminderPlanner.reminderTime(sub, state.reminders)
                val channel = when {
                    sub.calendarEventId != null -> stringResource(R.string.detail_channel_calendar)
                    state.reminders.pushEnabled -> stringResource(R.string.detail_channel_push)
                    else -> stringResource(R.string.detail_channel_none)
                }
                OutlinedGroup {
                    ListRow(
                        title = stringResource(R.string.detail_reminder_title),
                        subtitle = "${Fmt.shortWeekday(at.toLocalDate()).replaceFirstChar { it.uppercase() }}, ${Fmt.time(at.hour, at.minute, context)} · $channel",
                        icon = R.drawable.ic_notifications_active,
                        iconBackground = PrimaryContainer,
                        iconTint = Primary,
                        onClick = { actions.nav.navigate(Routes.REMINDERS) },
                    )
                }
            }

            SectionHeader(stringResource(R.string.detail_history_title), modifier = Modifier.padding(top = 8.dp))
            if (sub.payments.isEmpty()) {
                Text(stringResource(R.string.detail_history_empty, Fmt.dayMonthYear(sub.nextCharge)), style = CwType.Body, color = OnSurfaceVariant)
            } else {
                OutlinedGroup {
                    sub.payments.take(6).forEachIndexed { i, p ->
                        if (i > 0) RowDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(Fmt.dayMonthYear(p.date), style = CwType.Body, color = OnSurface)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Fmt.pen(p.amountPen, context), style = CwType.BodyStrong, color = OnSurface)
                                if (p.currency != Currency.PEN) {
                                    Text(stringResource(R.string.detail_history_fx, Fmt.money(p.amount, p.currency, context), Fmt.rate(p.rate, context)), style = CwType.Caption, color = OnSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            if (sub.notes.isNotBlank()) {
                SectionHeader(stringResource(R.string.detail_notes_title), modifier = Modifier.padding(top = 8.dp))
                Text("\"${sub.notes}\"", style = CwType.Body, color = OnSurface)
            }

            Spacer(Modifier.height(20.dp))
            SecondaryButton(stringResource(R.string.common_edit), onClick = { actions.openEdit(sub.id) }, icon = R.drawable.ic_edit, height = 48.dp, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            if (sub.cancelled && !state.backendMode) {
                PrimaryButton(
                    stringResource(R.string.detail_reactivate_btn),
                    onClick = {
                        vm.setCancelled(sub.id, false)
                        actions.snack(context.getString(R.string.detail_reactivated_snack, sub.name))
                    },
                    icon = R.drawable.ic_restart_alt,
                    height = 48.dp,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (!sub.cancelled) {
                CwTextButton(
                    stringResource(R.string.detail_cancel_btn),
                    onClick = { confirmCancel = true },
                    icon = R.drawable.ic_cancel,
                    iconTint = Error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = stringResource(R.string.detail_confirm_cancel_title, sub.name),
            body = stringResource(R.string.detail_confirm_cancel_body, sub.name),
            confirm = stringResource(R.string.detail_cancel_btn),
            dismiss = stringResource(R.string.common_back),
            onConfirm = {
                confirmCancel = false
                vm.cancel(sub.id) {
                    if (state.backendMode) actions.snack(context.getString(R.string.detail_cancelled_remote_snack, sub.name))
                    else actions.snack(
                        context.getString(R.string.detail_cancelled_local_snack, sub.name, Fmt.pen(state.monthlyPen(sub), context)),
                        context.getString(R.string.common_undo),
                    ) {
                        vm.setCancelled(sub.id, false)
                    }
                }
            },
            onDismiss = { confirmCancel = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.detail_confirm_delete_title, sub.name),
            body = stringResource(R.string.detail_confirm_delete_body),
            confirm = stringResource(R.string.common_delete),
            dismiss = stringResource(R.string.common_back),
            onConfirm = {
                confirmDelete = false
                val backup = sub
                vm.delete(sub.id)
                actions.snack(
                    context.getString(R.string.detail_deleted_snack, sub.name),
                    context.getString(R.string.common_undo),
                ) { vm.restore(backup.copy(calendarEventId = null)) }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = CwType.Heading, color = OnSurface) },
        text = { Text(body, style = CwType.Body, color = OnSurface) },
        confirmButton = {
            PrimaryButton(
                confirm,
                onClick = onConfirm,
                height = 44.dp,
                shape = RoundedCornerShape(12.dp),
                container = if (destructive) Error else Primary,
                content = if (destructive) OnSurface else androidx.compose.ui.graphics.Color.White,
            )
        },
        dismissButton = { CwTextButton(dismiss, onClick = onDismiss) },
    )
}
