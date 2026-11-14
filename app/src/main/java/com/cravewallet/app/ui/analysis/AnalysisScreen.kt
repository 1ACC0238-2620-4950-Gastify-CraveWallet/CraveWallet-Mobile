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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
fun AnalysisScreen(state: AppState, vm: AppViewModel, actions: AppActions, onViewPremium: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        CwTopBar(stringResource(R.string.analysis_title), actions = {
            if (state.profile.premium) PremiumBadge(Modifier.padding(end = 12.dp))
        })
        when {
            !state.profile.premium -> LockedAnalysis(state, vm, actions, onViewPremium)
            state.subscriptions.isEmpty() -> EmptyAnalysis(actions)
            else -> AnalysisContent(state, actions, vm)
        }
    }
}

@Composable
private fun AnalysisContent(state: AppState, actions: AppActions, vm: AppViewModel) {
    val context = LocalContext.current
    val online by rememberIsOnline()
    var retrying by remember { mutableStateOf(false) }
    var range by rememberSaveable { mutableStateOf(Range.SEIS_MESES) }
    val bars = Analytics.bars(state, range, context = context)
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
                title = stringResource(R.string.analysis_offline_title),
                body = stringResource(R.string.analysis_offline_body, Fmt.relativeDateTime(state.rates.updatedAt, context = context)),
                modifier = Modifier.padding(bottom = 12.dp),
                actions = {
                    LinkText(stringResource(if (retrying) R.string.analysis_retrying else R.string.analysis_retry), onClick = {
                        retrying = true
                        vm.refreshRates { ok ->
                            retrying = false
                            actions.snack(context.getString(if (ok) R.string.analysis_rate_updated_snack else R.string.analysis_still_offline_snack))
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
                    label = { Text(stringResource(r.labelRes), style = CwType.BodyStrong) },
                )
            }
        }

        // Selected period total
        Spacer(Modifier.height(16.dp))
        val currentMonth = YearMonth.from(today)
        val periodMonth = sel?.month ?: currentMonth
        Text(
            if (range == Range.MES) "${Fmt.monthYear(currentMonth)} · ${sel?.label ?: ""}" else Fmt.monthYear(periodMonth),
            style = CwType.Caption,
            color = OnSurfaceVariant,
        )
        val total = if (range == Range.MES) Analytics.monthTotal(state, currentMonth).first else sel?.total ?: 0.0
        Text(Fmt.pen(if (range == Range.MES) sel?.total ?: 0.0 else total, context), style = CwType.Amount, color = OnSurface)
        if (range != Range.MES) {
            val prev = periodMonth.minusMonths(1)
            val prevTotal = Analytics.monthTotal(state, prev).first
            if (prevTotal > 0) {
                val diff = total - prevTotal
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CwIcon(if (diff >= 0) R.drawable.ic_trending_up else R.drawable.ic_trending_down, size = 18.dp, tint = if (diff > 0) Error else Success)
                    Text(
                        stringResource(
                            if (diff >= 0) R.string.analysis_diff_more else R.string.analysis_diff_less,
                            Fmt.pen(kotlin.math.abs(diff), context),
                            Fmt.monthLong(prev.monthValue),
                            Fmt.pen(prevTotal, context),
                        ),
                        style = CwType.Body,
                        color = OnSurface,
                    )
                }
            }
        } else {
            Text(stringResource(R.string.analysis_month_total, Fmt.pen(total, context)), style = CwType.Body, color = OnSurface)
        }

        Spacer(Modifier.height(16.dp))
        CwCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(if (range == Range.MES) R.string.analysis_chart_weekly else R.string.analysis_chart_monthly),
                    style = CwType.BodyStrong,
                    color = OnSurface,
                )
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
            Text(
                stringResource(if (range == Range.MES) R.string.analysis_tap_week else R.string.analysis_tap_month),
                style = CwType.Caption,
                color = OnSurfaceVariant,
            )
        }

        Analytics.insight(state, context)?.let { insight ->
            InfoBanner(
                icon = R.drawable.ic_insights,
                iconTint = Primary,
                container = PrimaryContainer,
                title = insight.title,
                body = insight.body,
                actions = { LinkText(stringResource(R.string.analysis_view_subscription), onClick = { actions.nav.navigate(Routes.detail(insight.subscriptionId)) }) },
            )
        }

        SectionHeader(stringResource(R.string.home_section_by_category), modifier = Modifier.padding(top = 8.dp))
        val shares = Analytics.categories(state)
        CwCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                DonutChart(shares, centerLabel = stringResource(R.string.analysis_donut_center_label), centerValue = Fmt.pen(state.monthlyTotal, context))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shares.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(s.category.color))
                            Text("${stringResource(s.category.labelRes)} · ${Fmt.percent(s.share)}", style = CwType.Caption, color = OnSurface)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        shares.forEach { s ->
            val countText = stringResource(
                if (s.subscriptions.size == 1) R.string.analysis_subs_count_one else R.string.analysis_subs_count_many,
                s.subscriptions.size,
            )
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
                    Text(stringResource(s.category.labelRes), style = CwType.BodyStrong, color = OnSurface)
                    Text("$countText · ${Fmt.percent(s.share)}", style = CwType.Caption, color = OnSurfaceVariant)
                }
                Text(Fmt.pen(s.total, context), style = CwType.BodyStrong, color = OnSurface)
                CwIcon(R.drawable.ic_chevron_right, tint = OnSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** N4 · No data to analyze. */
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
            title = stringResource(R.string.analysis_empty_title),
            body = stringResource(R.string.analysis_empty_body),
            action = {
                PrimaryButton(
                    stringResource(R.string.analysis_empty_cta),
                    onClick = { actions.goTab(Routes.expenses()) },
                    icon = R.drawable.ic_receipt_long,
                    modifier = Modifier.width(220.dp),
                )
            },
        )
    }
}

/** N5 · Analysis is Premium-only: blurred preview + benefits card. */
@Composable
private fun LockedAnalysis(state: AppState, vm: AppViewModel, actions: AppActions, onViewPremium: () -> Unit) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val pending = state.premiumStatus?.pending == true
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        // Blurred preview (on Android < 12 dimmed instead of blurred)
        val preview = Modifier
            .fillMaxWidth()
            .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(10.dp) else Modifier.alpha(0.25f))
        Column(preview) {
            CwCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val demo = Analytics.bars(com.cravewallet.app.data.SeedData.state(), Range.SEIS_MESES, context = context)
                    BarChart(demo, demo.lastIndex, onSelect = {}, showTooltip = false)
                }
            }
        }
        CwCard(Modifier.fillMaxWidth().padding(top = 72.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DialogIcon(R.drawable.ic_lock_fill, tint = OnSurface, background = SurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.analysis_locked_title), style = CwType.Heading, color = OnSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth()) {
                    CheckItem(stringResource(R.string.analysis_locked_benefit_1))
                    CheckItem(stringResource(R.string.analysis_locked_benefit_2))
                    CheckItem(stringResource(R.string.analysis_locked_benefit_3, state.active.size, AppState.FREE_PLAN_LIMIT))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (pending) stringResource(R.string.analysis_locked_pending)
                    else stringResource(R.string.analysis_locked_price, Fmt.pen(state.premiumPrice, context)),
                    style = CwType.Caption,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    stringResource(if (pending) R.string.analysis_locked_continue_pay else R.string.analysis_locked_view_premium),
                    onClick = onViewPremium,
                    icon = R.drawable.ic_workspace_premium,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
                if (pending) CwTextButton(stringResource(R.string.analysis_locked_refresh), onClick = { if (!busy) vm.refreshPremium() })
                CwTextButton(stringResource(R.string.common_not_now), onClick = { actions.goTab(Routes.HOME) })
            }
        }
    }
}
