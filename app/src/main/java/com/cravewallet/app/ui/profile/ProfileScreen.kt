package com.cravewallet.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cravewallet.app.AppViewModel
import com.cravewallet.app.R
import com.cravewallet.app.data.AppState
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.data.Profile
import com.cravewallet.app.reminders.CalendarHelper
import com.cravewallet.app.ui.AppActions
import com.cravewallet.app.ui.Routes
import com.cravewallet.app.ui.components.CwCard
import com.cravewallet.app.ui.components.CwTextButton
import com.cravewallet.app.ui.components.CwTextField
import com.cravewallet.app.ui.components.CwTopBar
import com.cravewallet.app.ui.components.ListRow
import com.cravewallet.app.ui.components.PremiumBadge
import com.cravewallet.app.ui.components.PrimaryButton
import com.cravewallet.app.ui.components.RowDivider
import com.cravewallet.app.ui.expenses.ConfirmDialog
import com.cravewallet.app.ui.premium.PremiumReason
import com.cravewallet.app.ui.theme.CwType
import com.cravewallet.app.ui.theme.OnSurface
import com.cravewallet.app.ui.theme.OnSurfaceVariant
import com.cravewallet.app.ui.theme.Primary
import com.cravewallet.app.ui.theme.Surface
import com.cravewallet.app.ui.theme.SurfaceVariant
import java.time.LocalDate
import java.time.ZoneId

private enum class ProfileDialog { RATE, ACCOUNT, PREMIUM_CANCEL, PRIVACY, RESET, EMPTY }

/** P1 · Profile. */
@Composable
fun ProfileScreen(state: AppState, vm: AppViewModel, actions: AppActions) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    val premiumStatus = state.premiumStatus
    val periodEnd = premiumStatus?.periodEndsAt?.atZone(ZoneId.of("America/Lima"))?.toLocalDate()
    val r = state.reminders
    val calendarText = stringResource(if (r.calendarEnabled && CalendarHelper.hasPermission(context)) R.string.profile_cal_active else R.string.profile_cal_inactive)
    val pushText = stringResource(if (r.pushEnabled) R.string.profile_push_active else R.string.profile_push_inactive)

    Column(Modifier.fillMaxSize()) {
        CwTopBar(stringResource(R.string.profile_title))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            CwCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Primary),
                        contentAlignment = Alignment.Center,
                    ) { Text(state.profile.initials, style = CwType.Heading, color = Color.White) }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(state.profile.name, style = CwType.Heading, color = OnSurface)
                        Text(state.profile.email, style = CwType.Caption, color = OnSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        if (state.profile.premium) PremiumBadge()
                        else Text(
                            stringResource(R.string.profile_free_plan),
                            style = CwType.Caption,
                            color = OnSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            GroupLabel(stringResource(R.string.profile_group_preferences))
            CwCard(Modifier.fillMaxWidth()) {
                ListRow(
                    stringResource(R.string.profile_row_reminders),
                    subtitle = "$calendarText · $pushText",
                    icon = R.drawable.ic_notifications,
                    onClick = { actions.nav.navigate(Routes.REMINDERS) },
                )
                RowDivider()
                ListRow(
                    stringResource(R.string.profile_row_ref_currency),
                    subtitle = stringResource(R.string.profile_row_ref_currency_sub),
                    icon = R.drawable.ic_currency_exchange,
                    onClick = {
                        actions.snack(
                            context.getString(
                                if (state.backendMode) R.string.profile_snack_currency_remote else R.string.profile_snack_currency_local
                            )
                        )
                    },
                )
                RowDivider()
                ListRow(
                    stringResource(R.string.profile_row_fx),
                    subtitle = stringResource(R.string.profile_row_fx_sub, Fmt.rate(state.rates.usd, context), Fmt.relativeDateTime(state.rates.updatedAt, context = context)),
                    icon = R.drawable.ic_sync,
                    onClick = { if (state.backendMode) vm.refreshRates() else dialog = ProfileDialog.RATE },
                )
            }

            GroupLabel(stringResource(R.string.profile_group_account))
            CwCard(Modifier.fillMaxWidth()) {
                ListRow(
                    stringResource(R.string.profile_row_account_data),
                    subtitle = stringResource(R.string.profile_row_account_data_sub),
                    icon = R.drawable.ic_person,
                    onClick = {
                        if (state.backendMode) actions.snack(context.getString(R.string.profile_snack_edit_unavailable))
                        else dialog = ProfileDialog.ACCOUNT
                    },
                )
                RowDivider()
                val monthlyPrice = stringResource(R.string.profile_plan_price_month, Fmt.pen(state.premiumPrice, context))
                val planSubtitle = when {
                    premiumStatus?.pending == true -> stringResource(R.string.profile_plan_pending)
                    state.profile.premium && !state.backendMode -> {
                        val renewal = LocalDate.now().withDayOfMonth(1).plusMonths(1)
                        stringResource(R.string.profile_plan_renews, monthlyPrice, Fmt.dayMonth(renewal))
                    }
                    state.profile.premium && periodEnd != null -> {
                        val date = Fmt.dayMonth(periodEnd)
                        if (premiumStatus.cancelAtPeriodEnd) stringResource(R.string.profile_plan_access_until, date)
                        else stringResource(R.string.profile_plan_valid_until, monthlyPrice, date)
                    }
                    state.profile.premium -> monthlyPrice
                    else -> stringResource(R.string.profile_plan_free_limit, AppState.FREE_PLAN_LIMIT)
                }
                ListRow(
                    stringResource(R.string.profile_row_premium),
                    subtitle = planSubtitle,
                    icon = R.drawable.ic_workspace_premium,
                    onClick = {
                        if (state.profile.premium && premiumStatus?.cancelAtPeriodEnd != true) dialog = ProfileDialog.PREMIUM_CANCEL
                        else actions.openPremium(PremiumReason.PLAN)
                    },
                )
                RowDivider()
                ListRow(
                    stringResource(R.string.profile_row_privacy),
                    subtitle = stringResource(R.string.profile_row_privacy_sub),
                    icon = R.drawable.ic_shield,
                    onClick = { dialog = ProfileDialog.PRIVACY },
                )
            }

            if (state.backendMode) {
                GroupLabel(stringResource(R.string.profile_group_server))
                CwCard(Modifier.fillMaxWidth()) {
                    ListRow(stringResource(R.string.profile_row_refresh), subtitle = stringResource(R.string.profile_row_refresh_sub), icon = R.drawable.ic_sync, onClick = vm::refresh)
                    RowDivider()
                    ListRow(stringResource(R.string.profile_row_logout), subtitle = state.profile.email, icon = R.drawable.ic_logout, onClick = vm::logout)
                }
                state.rateAttribution?.let { Text(stringResource(R.string.profile_rate_attribution, it), style = CwType.Caption, modifier = Modifier.padding(vertical = 8.dp)) }
            } else {
                GroupLabel(stringResource(R.string.profile_group_demo))
                CwCard(Modifier.fillMaxWidth()) {
                    ListRow(
                        stringResource(R.string.profile_row_reset_demo),
                        subtitle = stringResource(R.string.profile_row_reset_demo_sub),
                        icon = R.drawable.ic_restart_alt,
                        onClick = { dialog = ProfileDialog.RESET },
                    )
                    RowDivider()
                    ListRow(
                        stringResource(R.string.profile_row_start_empty),
                        subtitle = stringResource(R.string.profile_row_start_empty_sub),
                        icon = R.drawable.ic_logout,
                        onClick = { dialog = ProfileDialog.EMPTY },
                    )
                }
                TextButtonForLogout(vm)
            }
            Text(
                stringResource(R.string.profile_version_footer),
                style = CwType.Caption,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally),
            )
        }
    }

    when (dialog) {
        ProfileDialog.RATE -> RateDialog(state, vm, actions) { dialog = null }
        ProfileDialog.ACCOUNT -> AccountDialog(
            state.profile,
            onSave = {
                vm.setProfile(it)
                dialog = null
                actions.snack(context.getString(R.string.profile_snack_account_updated))
            },
        ) { dialog = null }
        ProfileDialog.PREMIUM_CANCEL -> ConfirmDialog(
            title = stringResource(R.string.profile_cancel_premium_title),
            body = if (state.backendMode) stringResource(R.string.profile_cancel_premium_body_remote)
            else stringResource(R.string.profile_cancel_premium_body_local, AppState.FREE_PLAN_LIMIT),
            confirm = stringResource(R.string.profile_cancel_premium_confirm),
            dismiss = stringResource(R.string.profile_cancel_premium_keep),
            onConfirm = {
                dialog = null
                vm.cancelPremium {
                    actions.snack(context.getString(if (state.backendMode) R.string.profile_snack_renewal_cancelled else R.string.profile_snack_back_to_free))
                }
            },
            onDismiss = { dialog = null },
        )
        ProfileDialog.PRIVACY -> InfoDialog(
            stringResource(R.string.profile_row_privacy),
            stringResource(if (state.backendMode) R.string.profile_privacy_body_remote else R.string.profile_privacy_body_local),
        ) { dialog = null }
        ProfileDialog.RESET -> ConfirmDialog(
            title = stringResource(R.string.profile_reset_title),
            body = stringResource(R.string.profile_reset_body),
            confirm = stringResource(R.string.profile_reset_confirm),
            dismiss = stringResource(R.string.common_back),
            destructive = false,
            onConfirm = { vm.resetDemo(); dialog = null; actions.goTab(Routes.HOME) },
            onDismiss = { dialog = null },
        )
        ProfileDialog.EMPTY -> ConfirmDialog(
            title = stringResource(R.string.profile_empty_title),
            body = stringResource(R.string.profile_empty_body),
            confirm = stringResource(R.string.profile_empty_confirm),
            dismiss = stringResource(R.string.common_back),
            onConfirm = { vm.startEmpty(); dialog = null; actions.goTab(Routes.HOME) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = CwType.Body, color = OnSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun RateDialog(state: AppState, vm: AppViewModel, actions: AppActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(Fmt.rate(state.rates.usd, context)) }
    var loading by remember { mutableStateOf(false) }
    val value = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text(stringResource(R.string.profile_row_fx), style = CwType.Heading, color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.profile_rate_dialog_body, Fmt.relativeDateTime(state.rates.updatedAt, context = context)),
                    style = CwType.Body,
                    color = OnSurface,
                )
                CwTextField(
                    label = stringResource(R.string.profile_rate_field_label),
                    value = text,
                    onValueChange = { text = it },
                    keyboardType = KeyboardType.Decimal,
                    error = if (value == null || value <= 0) stringResource(R.string.profile_rate_err_invalid) else null,
                )
                CwTextButton(
                    stringResource(if (loading) R.string.profile_rate_updating else R.string.profile_rate_update_now),
                    icon = R.drawable.ic_sync,
                    color = Primary,
                    onClick = {
                        loading = true
                        vm.refreshRates { ok ->
                            loading = false
                            if (ok) {
                                onDismiss()
                                actions.snack(context.getString(R.string.analysis_rate_updated_snack))
                            } else actions.snack(context.getString(R.string.profile_rate_update_failed_snack))
                        }
                    },
                )
            }
        },
        confirmButton = {
            PrimaryButton(stringResource(R.string.common_save), enabled = value != null && value > 0, height = 44.dp, shape = RoundedCornerShape(12.dp), onClick = {
                vm.setUsdRate(value ?: return@PrimaryButton)
                onDismiss()
                actions.snack(context.getString(R.string.profile_rate_set_snack, Fmt.rate(value, context)))
            })
        },
        dismissButton = { CwTextButton(stringResource(R.string.common_cancel), onClick = onDismiss) },
    )
}

@Composable
private fun AccountDialog(profile: Profile, onSave: (Profile) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(profile.name) }
    var email by remember { mutableStateOf(profile.email) }
    val valid = name.isNotBlank() && email.contains('@')
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text(stringResource(R.string.profile_row_account_data), style = CwType.Heading, color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CwTextField(stringResource(R.string.profile_field_name), name, { name = it }, maxLength = 40)
                CwTextField(stringResource(R.string.profile_field_email), email, { email = it }, keyboardType = KeyboardType.Email, maxLength = 60)
            }
        },
        confirmButton = {
            PrimaryButton(stringResource(R.string.common_save), enabled = valid, height = 44.dp, shape = RoundedCornerShape(12.dp), onClick = {
                onSave(profile.copy(name = name.trim(), email = email.trim()))
            })
        },
        dismissButton = { CwTextButton(stringResource(R.string.common_cancel), onClick = onDismiss) },
    )
}

@Composable
fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = CwType.Heading, color = OnSurface) },
        text = { Text(body, style = CwType.Body, color = OnSurface) },
        confirmButton = { CwTextButton(stringResource(R.string.common_got_it), color = Primary, onClick = onDismiss) },
    )
}

@Composable
private fun TextButtonForLogout(vm: AppViewModel) {
    CwTextButton(stringResource(R.string.profile_exit_demo), onClick = vm::logout)
}
