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

/** A9 · Límite del plan gratuito / información del plan Premium. */
@Composable
fun PremiumSheet(
    reason: PremiumReason,
    state: AppState,
    onActivate: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                    PremiumReason.LIMIT -> "Llegaste a $used de $limit suscripciones"
                    PremiumReason.ANALYSIS -> "Análisis es parte de Premium"
                    PremiumReason.PLAN -> if (state.profile.premium) "Tienes CraveWallet Premium" else "CraveWallet Premium"
                },
                style = CwType.Heading,
                color = OnSurface,
                textAlign = TextAlign.Center,
            )
            if (reason == PremiumReason.LIMIT) {
                Spacer(Modifier.height(12.dp))
                ProgressTrack(fraction = used.toFloat() / limit, color = Accent, modifier = Modifier.fillMaxWidth(0.8f))
                Spacer(Modifier.height(6.dp))
                Text("$used de $limit en el plan gratuito", style = CwType.Caption, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CheckItem("Suscripciones sin límite")
                CheckItem("Análisis mensual y por categoría")
                CheckItem("Precio en soles, sin tipo de cambio")
            }
            Spacer(Modifier.height(24.dp))
            if (state.profile.premium) {
                Text(
                    "${Fmt.pen(AppState.PREMIUM_PRICE)} al mes. Cancela cuando quieras desde Perfil.",
                    style = CwType.Caption,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                CwTextButton("Cerrar", onClick = onDismiss)
            } else {
                PrimaryButton(
                    "Ver Premium · ${Fmt.pen(AppState.PREMIUM_PRICE)} al mes",
                    onClick = onActivate,
                    modifier = Modifier.fillMaxWidth(),
                    icon = R.drawable.ic_workspace_premium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Prototipo académico: no se realiza ningún cobro real.",
                    style = CwType.Label,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                CwTextButton("Ahora no", onClick = onDismiss)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
