package com.cravewallet.app.ui.analysis

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cravewallet.app.R
import com.cravewallet.app.data.Analytics
import com.cravewallet.app.data.AppState
import com.cravewallet.app.data.Bar
import com.cravewallet.app.data.Category
import com.cravewallet.app.data.Currency
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.ui.AppActions
import com.cravewallet.app.ui.Routes
import com.cravewallet.app.ui.components.CategoryIcon
import com.cravewallet.app.ui.components.CwCard
import com.cravewallet.app.ui.components.CwTopBar
import com.cravewallet.app.ui.components.InfoBanner
import com.cravewallet.app.ui.components.ProgressTrack
import com.cravewallet.app.ui.components.RowDivider
import com.cravewallet.app.ui.components.ServiceAvatar
import com.cravewallet.app.ui.theme.CwType
import com.cravewallet.app.ui.theme.OnSurface
import com.cravewallet.app.ui.theme.OnSurfaceVariant
import com.cravewallet.app.ui.theme.Primary
import com.cravewallet.app.ui.theme.PrimaryContainer
import java.time.LocalDate
import java.time.YearMonth

/** N3 · Category detail: total, last 6 months, and constituent subscriptions. */
@Composable
fun CategoryDetailScreen(category: Category, state: AppState, actions: AppActions) {
    val context = LocalContext.current
    // Detail is part of Analysis (Premium): if the plan changed, go back
    LaunchedEffect(state.profile.premium) {
        if (!state.profile.premium) actions.back()
    }
    if (!state.profile.premium) return
    val today = LocalDate.now()
    val current = YearMonth.from(today)
    val subs = state.active.filter { it.category == category }.sortedByDescending { state.monthlyPen(it) }
    val total = subs.sumOf { state.monthlyPen(it) }
    val share = if (state.monthlyTotal > 0) total / state.monthlyTotal else 0.0
    val bars = (5 downTo 0).map { back ->
        val m = current.minusMonths(back.toLong())
        val t = Analytics.categoryMonthTotal(state, category, m, today)
        Bar(Fmt.monthShortCap(m.monthValue), t, 0, m, "${Fmt.monthShortCap(m.monthValue)} · ${Fmt.pen(t, context)}")
    }
    var selected by rememberSaveable { mutableIntStateOf(bars.lastIndex) }
    val categoryLabel = stringResource(category.labelRes)

    Column(Modifier.fillMaxSize()) {
        CwTopBar(categoryLabel, onBack = actions::back)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(category, size = 48.dp)
                Column {
                    Text(Fmt.pen(total, context), style = CwType.Amount, color = OnSurface)
                    Text(
                        stringResource(
                            if (subs.size == 1) R.string.cat_detail_subtitle_one else R.string.cat_detail_subtitle_many,
                            Fmt.percent(share),
                            Fmt.monthLong(current.monthValue),
                            subs.size,
                        ),
                        style = CwType.Caption,
                        color = OnSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            CwCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.cat_detail_last_6_months), style = CwType.BodyStrong, color = OnSurface)
                    Spacer(Modifier.height(32.dp))
                    BarChart(bars, selected, onSelect = { selected = it }, chartHeight = 110.dp, showTooltip = selected != bars.lastIndex, tooltipDetail = { null })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(Analytics.trend(bars.map { it.total }, categoryLabel, context), style = CwType.Body, color = OnSurface)
            Spacer(Modifier.height(12.dp))

            if (subs.isNotEmpty()) {
                CwCard(Modifier.fillMaxWidth()) {
                    subs.forEachIndexed { i, sub ->
                        if (i > 0) RowDivider()
                        val monthly = state.monthlyPen(sub)
                        val part = if (total > 0) monthly / total else 0.0
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { actions.nav.navigate(Routes.detail(sub.id)) }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ServiceAvatar(sub.name, sub.category)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(sub.name, style = CwType.BodyStrong, color = OnSurface)
                                ProgressTrack(part.toFloat(), color = category.color, modifier = Modifier.fillMaxWidth(0.9f))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Fmt.pen(monthly, context), style = CwType.BodyStrong, color = OnSurface)
                                Text(
                                    if (sub.currency != Currency.PEN) Fmt.money(sub.amount, sub.currency, context) else Fmt.percent(part),
                                    style = CwType.Caption,
                                    color = OnSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Foreign currency subscriptions notice
            subs.filter { it.currency != Currency.PEN }.forEach { sub ->
                val variation = Analytics.variation(state, sub) ?: return@forEach
                val (diff, last) = variation
                InfoBanner(
                    icon = R.drawable.ic_currency_exchange,
                    iconTint = Primary,
                    container = PrimaryContainer,
                    title = stringResource(R.string.cat_detail_fx_title, sub.name, Analytics.currencyName(sub.currency, context)),
                    body = stringResource(
                        if (diff >= 0) R.string.cat_detail_fx_more else R.string.cat_detail_fx_less,
                        Fmt.pen(kotlin.math.abs(diff), context),
                        Fmt.monthLong(last.date.monthValue),
                        Fmt.rate(last.rate, context),
                        Fmt.rate(state.rates.rate(sub.currency), context),
                    ),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
