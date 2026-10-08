package com.cravewallet.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cravewallet.app.R
import com.cravewallet.app.data.Analytics
import com.cravewallet.app.data.AppState
import com.cravewallet.app.data.CategoryShare
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.data.Subscription
import com.cravewallet.app.ui.AppActions
import com.cravewallet.app.ui.Routes
import com.cravewallet.app.ui.components.CategoryIcon
import com.cravewallet.app.ui.components.CwIcon
import com.cravewallet.app.ui.components.EmptyState
import com.cravewallet.app.ui.components.InfoBanner
import com.cravewallet.app.ui.components.LinkText
import com.cravewallet.app.ui.components.PrimaryButton
import com.cravewallet.app.ui.components.ProgressTrack
import com.cravewallet.app.ui.components.SectionHeader
import com.cravewallet.app.ui.components.SeeAllCard
import com.cravewallet.app.ui.components.UpcomingCard
import com.cravewallet.app.ui.premium.PremiumReason
import com.cravewallet.app.ui.theme.Accent
import com.cravewallet.app.ui.theme.AccentContainer
import com.cravewallet.app.ui.theme.CwType
import com.cravewallet.app.ui.theme.OnPrimaryContainer
import com.cravewallet.app.ui.theme.OnSurface
import com.cravewallet.app.ui.theme.OnSurfaceVariant
import com.cravewallet.app.ui.theme.Primary
import com.cravewallet.app.ui.theme.PrimaryContainer
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(state: AppState, actions: AppActions) {
    val today = LocalDate.now()
    val upcoming = state.active.sortedBy { it.nextCharge }
    val alert = upcoming.firstOrNull { !it.isUnused && ChronoUnit.DAYS.between(today, it.nextCharge) <= 3 }
    val categories = Analytics.categories(state)
    val unused = state.active.filter { it.isUnused }.maxByOrNull { state.monthlyPen(it) }

    fun openAnalysis() = actions.goTab(Routes.ANALYSIS)
    fun openCategory(share: CategoryShare) {
        if (state.profile.premium) actions.nav.navigate(Routes.category(share.category))
        else actions.openPremium(PremiumReason.ANALYSIS)
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item { Header(state, onSearch = { actions.goTab(Routes.expenses(focusSearch = true)) }, onAvatar = { actions.goTab(Routes.PROFILE) }) }
        item { SummaryCard(state, onAnalysis = ::openAnalysis, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

        if (state.active.isEmpty()) {
            item {
                EmptyState(
                    icon = R.drawable.ic_account_balance_wallet,
                    title = "Aún no tienes gastos registrados.",
                    body = "Agrega tu primera suscripción y toma el control.",
                    modifier = Modifier.padding(top = 32.dp, start = 16.dp, end = 16.dp),
                    action = {
                        PrimaryButton(
                            "Agregar mi primera suscripción",
                            onClick = actions.openAdd,
                            icon = R.drawable.ic_add,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    },
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CwIcon(R.drawable.ic_info, size = 16.dp, tint = OnSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("Empieza por las que se cobran en dólares.", style = CwType.Caption, color = OnSurfaceVariant)
                }
            }
            return@LazyColumn
        }

        if (alert != null) {
            item {
                ChargeAlert(alert, state, onClick = { actions.nav.navigate(Routes.detail(alert.id)) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }

        item {
            SectionHeader("Próximos cobros", action = "Ver todos", onAction = { actions.goTab(Routes.expenses()) }, modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(upcoming.take(4), key = { it.id }) { sub ->
                    UpcomingCard(sub, state, onClick = { actions.nav.navigate(Routes.detail(sub.id)) })
                }
                item { SeeAllCard(state.active.size, onClick = { actions.goTab(Routes.expenses()) }) }
            }
        }

        item {
            SectionHeader("Por categoría", action = "Análisis", onAction = ::openAnalysis, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
        }
        items(categories, key = { it.category.name }) { share ->
            CategoryRow(share, onClick = { openCategory(share) })
        }

        if (unused != null) {
            item {
                val days = ChronoUnit.DAYS.between(unused.unusedSince, today)
                InfoBanner(
                    icon = R.drawable.ic_savings,
                    title = "${unused.name}: " + if (days > 0) "sin uso hace $days días" else "marcada sin uso hoy",
                    body = "Si la cancelas antes del ${Fmt.dayMonth(unused.nextCharge)}, ahorras ${Fmt.pen(state.pen(unused))} este mes.",
                    container = AccentContainer,
                    iconTint = Accent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    actions = { LinkText("Revisar", onClick = { actions.nav.navigate(Routes.detail(unused.id)) }) },
                )
            }
        }
    }
}

@Composable
private fun Header(state: AppState, onSearch: () -> Unit, onAvatar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Hola, ${state.profile.firstName}", style = CwType.Title, color = OnSurface, modifier = Modifier.semantics { heading() })
            Text(Fmt.longDate(LocalDate.now()), style = CwType.Caption, color = OnSurfaceVariant)
        }
        IconButton(onClick = onSearch) { CwIcon(R.drawable.ic_search, contentDescription = "Buscar") }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(PrimaryContainer)
                .clickable(role = Role.Button, onClickLabel = "Abrir perfil", onClick = onAvatar),
            contentAlignment = Alignment.Center,
        ) {
            Text(state.profile.initials, style = CwType.BodyStrong.copy(fontSize = 15.sp), fontWeight = FontWeight.SemiBold, color = OnPrimaryContainer)
        }
    }
}

@Composable
private fun SummaryCard(state: AppState, onAnalysis: () -> Unit, modifier: Modifier = Modifier) {
    val count = state.active.size
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Primary)
            .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Text("Gasto mensual en suscripciones", style = CwType.Body, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(Fmt.pen(state.monthlyTotal), style = CwType.Amount, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(
            "$count ${if (count == 1) "suscripción activa" else "suscripciones activas"} · todo en soles",
            style = CwType.Caption,
            color = Color.White.copy(alpha = 0.9f),
        )
        HorizontalDivider(Modifier.padding(top = 16.dp, end = 4.dp), color = Color.White.copy(alpha = 0.3f))
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            CwIcon(R.drawable.ic_currency_exchange, size = 18.dp, tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text(
                "TC S/ ${Fmt.rate(state.rates.usd)} · ${Fmt.relativeDateTime(state.rates.updatedAt)}",
                style = CwType.Caption,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            if (count > 0) LinkText("Ver análisis", onClick = onAnalysis, color = Color.White, trailing = R.drawable.ic_arrow_forward)
        }
    }
}

@Composable
private fun ChargeAlert(sub: Subscription, state: AppState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), sub.nextCharge)
    val title = when (days) {
        0L -> "Hoy te cobran ${sub.name.substringBefore(' ')}"
        1L -> "Mañana te cobran ${sub.name.substringBefore(' ')}"
        else -> "En $days días te cobran ${sub.name.substringBefore(' ')}"
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AccentContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Accent),
            contentAlignment = Alignment.Center,
        ) { CwIcon(R.drawable.ic_notifications_active_fill, size = 22.dp, tint = OnSurface) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = CwType.BodyLarge, fontWeight = FontWeight.Medium, color = OnSurface)
            Text(
                "${Fmt.pen(state.pen(sub))}. ¿Lo dejamos pasar? Revísalo antes del cobro.",
                style = CwType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = OnSurface,
            )
        }
        CwIcon(R.drawable.ic_chevron_right, tint = OnSurface)
    }
}

@Composable
private fun CategoryRow(share: CategoryShare, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIcon(share.category)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(share.category.label, style = CwType.BodyStrong, color = OnSurface)
            ProgressTrack(fraction = share.share.toFloat(), color = share.category.color, modifier = Modifier.fillMaxWidth(0.85f))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(84.dp)) {
            Text(Fmt.percent(share.share), style = CwType.BodyStrong, color = OnSurface)
            Text(Fmt.pen(share.total), style = CwType.Caption, color = OnSurfaceVariant)
        }
    }
}
