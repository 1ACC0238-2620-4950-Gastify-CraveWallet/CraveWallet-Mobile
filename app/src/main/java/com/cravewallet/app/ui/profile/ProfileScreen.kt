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
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

private enum class ProfileDialog { RATE, ACCOUNT, PREMIUM_CANCEL, PRIVACY, RESET, EMPTY }

/** P1 · Perfil. */
@Composable
fun ProfileScreen(state: AppState, vm: AppViewModel, actions: AppActions) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    val r = state.reminders
    val calendarText = if (r.calendarEnabled && CalendarHelper.hasPermission(context)) "Calendario activo" else "Calendario desactivado"
    val pushText = if (r.pushEnabled) "push activado" else "push desactivado"

    Column(Modifier.fillMaxSize()) {
        CwTopBar("Perfil")
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
                            "Plan gratuito",
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

            GroupLabel("Preferencias")
            CwCard(Modifier.fillMaxWidth()) {
                ListRow(
                    "Recordatorios",
                    subtitle = "$calendarText · $pushText",
                    icon = R.drawable.ic_notifications,
                    onClick = { actions.nav.navigate(Routes.REMINDERS) },
                )
                RowDivider()
                ListRow(
                    "Moneda de referencia",
                    subtitle = "Soles (PEN)",
                    icon = R.drawable.ic_currency_exchange,
                    onClick = { actions.snack("Todos los montos se muestran en soles. Los cobros en dólares o euros se convierten con el tipo de cambio del día.") },
                )
                RowDivider()
                ListRow(
                    "Tipo de cambio",
                    subtitle = "Automático · S/ ${Fmt.rate(state.rates.usd)} ${Fmt.relativeDateTime(state.rates.updatedAt)}",
                    icon = R.drawable.ic_sync,
                    onClick = { dialog = ProfileDialog.RATE },
                )
            }

            GroupLabel("Cuenta")
            CwCard(Modifier.fillMaxWidth()) {
                ListRow("Datos de la cuenta", subtitle = "Nombre y correo", icon = R.drawable.ic_person, onClick = { dialog = ProfileDialog.ACCOUNT })
                RowDivider()
                val renewal = LocalDate.now().withDayOfMonth(1).plusMonths(1)
                ListRow(
                    "Plan Premium",
                    subtitle = if (state.profile.premium) "${Fmt.pen(AppState.PREMIUM_PRICE)} al mes · se renueva el ${Fmt.dayMonth(renewal)}"
                    else "Plan gratuito · hasta ${AppState.FREE_PLAN_LIMIT} suscripciones",
                    icon = R.drawable.ic_workspace_premium,
                    onClick = {
                        if (state.profile.premium) dialog = ProfileDialog.PREMIUM_CANCEL else actions.openPremium(PremiumReason.PLAN)
                    },
                )
                RowDivider()
                ListRow("Ayuda y privacidad", subtitle = "No pedimos acceso a tu banco", icon = R.drawable.ic_shield, onClick = { dialog = ProfileDialog.PRIVACY })
            }

            GroupLabel("Demostración")
            CwCard(Modifier.fillMaxWidth()) {
                ListRow(
                    "Restablecer datos de ejemplo",
                    subtitle = "Renzo · 7 suscripciones · Premium",
                    icon = R.drawable.ic_restart_alt,
                    onClick = { dialog = ProfileDialog.RESET },
                )
                RowDivider()
                ListRow(
                    "Empezar sin suscripciones",
                    subtitle = "Camila · plan gratuito, sin datos",
                    icon = R.drawable.ic_logout,
                    onClick = { dialog = ProfileDialog.EMPTY },
                )
            }
            Text(
                "CraveWallet 1.0 · Prototipo académico",
                style = CwType.Caption,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally),
            )
        }
    }

    when (dialog) {
        ProfileDialog.RATE -> RateDialog(state, vm, actions) { dialog = null }
        ProfileDialog.ACCOUNT -> AccountDialog(state.profile, onSave = { vm.setProfile(it); dialog = null; actions.snack("Datos actualizados.") }) { dialog = null }
        ProfileDialog.PREMIUM_CANCEL -> ConfirmDialog(
            title = "¿Cancelar Premium?",
            body = "Volverás al plan gratuito: hasta ${AppState.FREE_PLAN_LIMIT} suscripciones y sin Análisis. Tus datos se mantienen.",
            confirm = "Cancelar Premium",
            dismiss = "Mantener",
            onConfirm = { vm.setPremium(false); dialog = null; actions.snack("Volviste al plan gratuito.") },
            onDismiss = { dialog = null },
        )
        ProfileDialog.PRIVACY -> InfoDialog(
            "Ayuda y privacidad",
            "CraveWallet no se conecta a tu banco ni a tus tarjetas: tú registras cada suscripción. " +
                "Los datos se guardan solo en este teléfono. El permiso de calendario se usa únicamente para crear " +
                "un evento antes de cada cobro, y las notificaciones solo para avisarte de tus cobros.",
        ) { dialog = null }
        ProfileDialog.RESET -> ConfirmDialog(
            title = "¿Restablecer los datos de ejemplo?",
            body = "Se reemplazarán tus suscripciones actuales por las 7 de ejemplo.",
            confirm = "Restablecer",
            dismiss = "Volver",
            destructive = false,
            onConfirm = { vm.resetDemo(); dialog = null; actions.goTab(Routes.HOME) },
            onDismiss = { dialog = null },
        )
        ProfileDialog.EMPTY -> ConfirmDialog(
            title = "¿Empezar sin suscripciones?",
            body = "Se borrarán todas las suscripciones y pasarás al plan gratuito.",
            confirm = "Empezar de cero",
            dismiss = "Volver",
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
    var text by remember { mutableStateOf(Fmt.rate(state.rates.usd)) }
    var loading by remember { mutableStateOf(false) }
    val value = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Tipo de cambio", style = CwType.Heading, color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Se actualiza solo cada vez que abres la app con conexión. Última actualización: ${Fmt.relativeDateTime(state.rates.updatedAt)}.",
                    style = CwType.Body,
                    color = OnSurface,
                )
                CwTextField(
                    label = "1 USD en soles",
                    value = text,
                    onValueChange = { text = it },
                    keyboardType = KeyboardType.Decimal,
                    error = if (value == null || value <= 0) "Ingresa un valor válido." else null,
                )
                CwTextButton(if (loading) "Actualizando…" else "Actualizar ahora", icon = R.drawable.ic_sync, color = Primary, onClick = {
                    loading = true
                    vm.refreshRates { ok ->
                        loading = false
                        if (ok) {
                            onDismiss()
                            actions.snack("Tipo de cambio actualizado.")
                        } else actions.snack("No pudimos actualizarlo. Revisa tu conexión.")
                    }
                })
            }
        },
        confirmButton = {
            PrimaryButton("Guardar", enabled = value != null && value > 0, height = 44.dp, shape = RoundedCornerShape(12.dp), onClick = {
                vm.setUsdRate(value ?: return@PrimaryButton)
                onDismiss()
                actions.snack("Tipo de cambio fijado en S/ ${Fmt.rate(value)}.")
            })
        },
        dismissButton = { CwTextButton("Cancelar", onClick = onDismiss) },
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
        title = { Text("Datos de la cuenta", style = CwType.Heading, color = OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CwTextField("Nombre", name, { name = it }, maxLength = 40)
                CwTextField("Correo", email, { email = it }, keyboardType = KeyboardType.Email, maxLength = 60)
            }
        },
        confirmButton = {
            PrimaryButton("Guardar", enabled = valid, height = 44.dp, shape = RoundedCornerShape(12.dp), onClick = {
                onSave(profile.copy(name = name.trim(), email = email.trim()))
            })
        },
        dismissButton = { CwTextButton("Cancelar", onClick = onDismiss) },
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
        confirmButton = { CwTextButton("Entendido", color = Primary, onClick = onDismiss) },
    )
}
