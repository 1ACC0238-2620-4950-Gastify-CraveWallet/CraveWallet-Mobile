package com.cravewallet.app.ui.analysis

import android.os.Build
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cravewallet.app.R
import com.cravewallet.app.data.Analytics
import com.cravewallet.app.data.AppState
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.data.Range
import com.cravewallet.app.data.rememberIsOnline
import com.cravewallet.app.ui.AppActions
import com.cravewallet.app.ui.Routes
import com.cravewallet.app.ui.components.CategoryIcon
import com.cravewallet.app.ui.components.CheckItem
import com.cravewallet.app.ui.components.CwCard
import com.cravewallet.app.ui.components.CwIcon
import com.cravewallet.app.ui.components.CwTextButton
import com.cravewallet.app.ui.components.CwTopBar
import com.cravewallet.app.ui.components.DialogIcon
import com.cravewallet.app.ui.components.EmptyState
import com.cravewallet.app.ui.components.InfoBanner
import com.cravewallet.app.ui.components.LinkText
import com.cravewallet.app.ui.components.PremiumBadge
import com.cravewallet.app.ui.components.PrimaryButton
import com.cravewallet.app.ui.components.SectionHeader
import com.cravewallet.app.ui.premium.PremiumReason
import com.cravewallet.app.ui.theme.CwType
import com.cravewallet.app.ui.theme.Error
import com.cravewallet.app.ui.theme.OnPrimaryContainer
import com.cravewallet.app.ui.theme.OnSurface
import com.cravewallet.app.ui.theme.OnSurfaceVariant
import com.cravewallet.app.ui.theme.Outline
import com.cravewallet.app.ui.theme.Primary
import com.cravewallet.app.ui.theme.PrimaryContainer
import com.cravewallet.app.ui.theme.Success
import com.cravewallet.app.ui.theme.Surface
import com.cravewallet.app.ui.theme.SurfaceVariant
import com.cravewallet.app.AppViewModel
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun AnalysisScreen(state: AppState, vm: AppViewModel, actions: AppActions) {
    Column(Modifier.fillMaxSize()) {
        CwTopBar("Análisis", actions = {
            if (state.profile.premium) PremiumBadge(Modifier.padding(end = 12.dp))
        })
        when {
            !state.profile.premium -> LockedAnalysis(state, actions)
            state.subscriptions.isEmpty() -> EmptyAnalysis(actions)
            else -> AnalysisContent(state, actions, vm)
        }
    }
}

@Composable
private fun AnalysisContent(state: AppState, actions: AppActions, vm: AppViewModel) {
    val online by rememberIsOnline()
    var retrying by remember { mutableStateOf(false) }
    var range by rememberSaveable { mutableStateOf(Range.SEIS_MESES) }
    val bars = Analytics.bars(state, range)
    var selected by rememberSaveable(range) { mutableIntStateOf(bars.lastIndex) }
    val sel = bars.getOrNull(selected)
    val today = LocalDate.now()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        if (!online) {
            InfoBanner(
                icon = R.drawable.ic_wifi_off,
                title = "Sin conexión",
                body = "Mostramos tus datos guardados ${Fmt.relativeDateTime(state.rates.updatedAt)}. " +
                    "Revisamos el tipo de cambio en cuanto vuelvas a estar en línea.",
                modifier = Modifier.padding(bottom = 12.dp),
                actions = {
                    LinkText(if (retrying) "Reintentando…" else "Reintentar", onClick = {
                        retrying = true
                        vm.refreshRates { ok ->
                            retrying = false
                            actions.snack(if (ok) "Tipo de cambio actualizado." else "Seguimos sin conexión. Inténtalo en un momento.")
                        }
                    })
                },
            )
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Range.entries.forEachIndexed { i, r ->
                SegmentedButton(
                    selected = range == r,
                    onClick = { range = r },
                    shape = SegmentedButtonDefaults.itemShape(i, Range.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = PrimaryContainer,
                        activeContentColor = OnPrimaryContainer,
                        activeBorderColor = Outline,
                        inactiveContainerColor = Surface,
                        inactiveContentColor = OnSurface,
                        inactiveBorderColor = Outline,
                    ),
                    label = { Text(r.label, style = CwType.BodyStrong) },
                )
            }
        }

        // Total del periodo seleccionado
        Spacer(Modifier.height(16.dp))
        val currentMonth = YearMonth.from(today)
        val periodMonth = sel?.month ?: currentMonth
        Text(
            if (range == Range.MES) "${Fmt.monthYear(currentMonth)} · ${sel?.label ?: ""}" else Fmt.monthYear(periodMonth),
            style = CwType.Caption,
            color = OnSurfaceVariant,
        )
        val total = if (range == Range.MES) Analytics.monthTotal(state, currentMonth).first else sel?.total ?: 0.0
        Text(Fmt.pen(if (range == Range.MES) sel?.total ?: 0.0 else total), style = CwType.Amount, color = OnSurface)
        if (range != Range.MES) {
            val prev = periodMonth.minusMonths(1)
            val prevTotal = Analytics.monthTotal(state, prev).first
            if (prevTotal > 0) {
                val diff = total - prevTotal
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CwIcon(if (diff >= 0) R.drawable.ic_trending_up else R.drawable.ic_trending_down, size = 18.dp, tint = if (diff > 0) Error else Success)
                    Text(
                        "${Fmt.pen(kotlin.math.abs(diff))} ${if (diff >= 0) "más" else "menos"} que ${Fmt.monthLong(prev.monthValue)} (${Fmt.pen(prevTotal)})",
                        style = CwType.Body,
                        color = OnSurface,
                    )
                }
            }
        } else {
            Text("Total del mes: ${Fmt.pen(total)}", style = CwType.Body, color = OnSurface)
        }

        Spacer(Modifier.height(16.dp))
        CwCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(if (range == Range.MES) "Cobros por semana en soles" else "Gasto mensual en soles", style = CwType.BodyStrong, color = OnSurface)
                Spacer(Modifier.height(36.dp))
                BarChart(
                    bars = bars,
                    selected = selected,
                    onSelect = { selected = it },
                    showTooltip = selected != bars.lastIndex || range == Range.MES,
                )
            }
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            CwIcon(R.drawable.ic_touch_app, size = 16.dp, tint = OnSurfaceVariant)
            Text(if (range == Range.MES) "Toca una barra para ver esa semana." else "Toca una barra para ver ese mes.", style = CwType.Caption, color = OnSurfaceVariant)
        }

        Analytics.insight(state)?.let { insight ->
            InfoBanner(
                icon = R.drawable.ic_insights,
                iconTint = Primary,
                container = PrimaryContainer,
                title = insight.title,
                body = insight.body,
                actions = { LinkText("Ver suscripción", onClick = { actions.nav.navigate(Routes.detail(insight.subscriptionId)) }) },
            )
        }

        SectionHeader("Por categoría", modifier = Modifier.padding(top = 8.dp))
        val shares = Analytics.categories(state)
        CwCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                DonutChart(shares, centerLabel = "al mes", centerValue = Fmt.pen(state.monthlyTotal))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shares.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(s.category.color))
                            Text("${s.category.label} · ${Fmt.percent(s.share)}", style = CwType.Caption, color = OnSurface)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        shares.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .clickable { actions.nav.navigate(Routes.category(s.category)) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CategoryIcon(s.category)
                Column(Modifier.weight(1f)) {
                    Text(s.category.label, style = CwType.BodyStrong, color = OnSurface)
                    Text("${s.subscriptions.size} ${if (s.subscriptions.size == 1) "suscripción" else "suscripciones"} · ${Fmt.percent(s.share)}", style = CwType.Caption, color = OnSurfaceVariant)
                }
                Text(Fmt.pen(s.total), style = CwType.BodyStrong, color = OnSurface)
                CwIcon(R.drawable.ic_chevron_right, tint = OnSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}


/** N4 · Sin datos para analizar. */
@Composable
private fun EmptyAnalysis(actions: AppActions) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CwCard(Modifier.fillMaxWidth(), elevated = false, border = androidx.compose.foundation.BorderStroke(1.dp, com.cravewallet.app.ui.theme.OutlineVariant)) {
            EmptyBarsPlaceholder(Modifier.padding(horizontal = 24.dp, vertical = 24.dp))
        }
        Spacer(Modifier.height(32.dp))
        EmptyState(
            icon = R.drawable.ic_bar_chart,
            title = "Aún no hay datos para analizar",
            body = "Registra al menos un mes de gastos para ver tu análisis.",
            action = {
                PrimaryButton("Ir a Gastos", onClick = { actions.goTab(Routes.expenses()) }, icon = R.drawable.ic_receipt_long, modifier = Modifier.width(220.dp))
            },
        )
    }
}

/** N5 · Análisis solo para Premium: vista previa desenfocada + tarjeta con beneficios. */
@Composable
private fun LockedAnalysis(state: AppState, actions: AppActions) {
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        // Vista previa desenfocada (en Android < 12 se atenúa en lugar de desenfocar)
        val preview = Modifier
            .fillMaxWidth()
            .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(10.dp) else Modifier.alpha(0.25f))
        Column(preview) {
            CwCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val demo = Analytics.bars(com.cravewallet.app.data.SeedData.state(), Range.SEIS_MESES)
                    BarChart(demo, demo.lastIndex, onSelect = {}, showTooltip = false)
                }
            }
        }
        CwCard(Modifier.fillMaxWidth().padding(top = 72.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DialogIcon(R.drawable.ic_lock_fill, tint = OnSurface, background = SurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("Análisis es parte de Premium", style = CwType.Heading, color = OnSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth()) {
                    CheckItem("Gasto mensual y por categoría en soles")
                    CheckItem("Mapa de calor de tus suscripciones")
                    CheckItem("Suscripciones sin límite (hoy: ${state.active.size} de 5)")
                }
                Spacer(Modifier.height(8.dp))
                Text("S/ 9.99 al mes, en soles. Cancela cuando quieras.", style = CwType.Caption, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Ver Premium", onClick = { actions.openPremium(PremiumReason.ANALYSIS) }, icon = R.drawable.ic_workspace_premium, modifier = Modifier.fillMaxWidth())
                CwTextButton("Ahora no", onClick = { actions.goTab(Routes.HOME) })
            }
        }
    }
}
