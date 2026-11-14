package com.cravewallet.app.ui.premium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cravewallet.app.R
import com.cravewallet.app.data.AppState
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.ui.components.CheckItem
import com.cravewallet.app.ui.components.CwTextButton
import com.cravewallet.app.ui.components.DialogIcon
import com.cravewallet.app.ui.components.PrimaryButton
import com.cravewallet.app.ui.components.ProgressTrack
import com.cravewallet.app.ui.components.SheetOverlay
import com.cravewallet.app.ui.theme.Accent
import com.cravewallet.app.ui.theme.AccentContainer
import com.cravewallet.app.ui.theme.CwType
import com.cravewallet.app.ui.theme.OnSurface
import com.cravewallet.app.ui.theme.OnSurfaceVariant

enum class PremiumReason { LIMIT, ANALYSIS, PLAN }

/** A9 · Free plan limit / Premium plan information. */
@Composable
fun PremiumSheet(
    reason: PremiumReason,
    state: AppState,
    busy: Boolean,
    onActivate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val limit = AppState.FREE_PLAN_LIMIT
    val used = state.active.size
    SheetOverlay(onDismissRequest = onDismiss, fullHeight = false) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DialogIcon(R.drawable.ic_workspace_premium, tint = Accent, background = AccentContainer, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                when (reason) {
                    PremiumReason.LIMIT -> stringResource(R.string.premium_title_limit, used, limit)
                    PremiumReason.ANALYSIS -> stringResource(R.string.premium_title_analysis)
                    PremiumReason.PLAN -> stringResource(if (state.profile.premium) R.string.premium_title_active else R.string.premium_title_default)
                },
                style = CwType.Heading,
                color = OnSurface,
                textAlign = TextAlign.Center,
            )
            if (reason == PremiumReason.LIMIT) {
                Spacer(Modifier.height(12.dp))
                ProgressTrack(fraction = used.toFloat() / limit, color = Accent, modifier = Modifier.fillMaxWidth(0.8f))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.premium_free_plan_usage, used, limit), style = CwType.Caption, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CheckItem(stringResource(R.string.premium_benefit_unlimited))
                CheckItem(stringResource(R.string.premium_benefit_analysis))
                CheckItem(stringResource(R.string.premium_benefit_soles))
            }
            Spacer(Modifier.height(24.dp))
            val priceFormatted = Fmt.pen(state.premiumPrice, context)
            if (state.profile.premium) {
                Text(
                    stringResource(R.string.premium_active_manage_hint, priceFormatted),
                    style = CwType.Caption,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                CwTextButton(stringResource(R.string.common_close), onClick = onDismiss)
            } else {
                PrimaryButton(
                    when {
                        state.premiumStatus?.pending == true -> stringResource(R.string.analysis_locked_continue_pay)
                        state.backendMode -> stringResource(R.string.premium_btn_go_to_pay, priceFormatted)
                        else -> stringResource(R.string.premium_btn_view, priceFormatted)
                    },
                    onClick = onActivate,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    icon = R.drawable.ic_workspace_premium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (state.backendMode) R.string.premium_checkout_mp_note else R.string.premium_demo_note),
                    style = CwType.Label,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                CwTextButton(stringResource(R.string.common_not_now), onClick = onDismiss)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
