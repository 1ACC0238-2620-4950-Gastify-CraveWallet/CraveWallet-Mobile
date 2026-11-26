package com.cravewallet.app.ui.expenses

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cravewallet.app.R
import com.cravewallet.app.data.ConversionCycle
import com.cravewallet.app.data.ExchangeRateHistory
import com.cravewallet.app.data.ExchangeRateVariation
import com.cravewallet.app.data.RateDirection
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

private const val PEN_CURRENCY_CODE = "PEN"

object ExchangeRateHistoryCardTags {
    const val ROOT = "fx_history_card"
    const val TITLE = "fx_history_title"
    const val LOADING = "fx_history_loading"
    const val EMPTY = "fx_history_empty"
    const val ERROR = "fx_history_error"
    const val RETRY = "fx_history_retry"
    const val VARIATION = "fx_history_variation"
    const val CYCLE_PREFIX = "fx_history_cycle_"

    fun cycle(index: Int): String = "$CYCLE_PREFIX$index"
}

@Composable
fun ExchangeRateHistoryCard(
    history: ExchangeRateHistory?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val locale = remember(resources, resources.configuration) {
        currentLocale(resources)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ExchangeRateHistoryCardTags.ROOT),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.fx_history_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(ExchangeRateHistoryCardTags.TITLE),
            )

            when {
                loading -> LoadingSection()
                error != null -> ErrorSection(error = error, onRetry = onRetry)
                history == null || history.cycles.isEmpty() -> EmptySection()
                else -> {
                    history.variation?.let { variation ->
                        VariationBanner(
                            variation = variation,
                            locale = locale,
                            resources = resources,
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        history.cycles.forEachIndexed { index, cycle ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            CycleRow(
                                cycle = cycle,
                                index = index,
                                locale = locale,
                                resources = resources,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ExchangeRateHistoryCardTags.LOADING)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(R.string.fx_history_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorSection(
    error: String,
    onRetry: () -> Unit,
) {
    val message = error.ifBlank { stringResource(R.string.fx_history_error_fallback) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ExchangeRateHistoryCardTags.ERROR)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                },
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag(ExchangeRateHistoryCardTags.RETRY),
        ) {
            Text(text = stringResource(R.string.fx_history_retry))
        }
    }
}

@Composable
private fun EmptySection() {
    Text(
        text = stringResource(R.string.fx_history_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ExchangeRateHistoryCardTags.EMPTY),
    )
}

@Composable
private fun VariationBanner(
    variation: ExchangeRateVariation,
    locale: Locale,
    resources: Resources,
) {
    val formattedPercent = remember(variation.percentChange, locale) {
        formatPercentMagnitude(variation.percentChange, locale)
    }
    val variationLabel = stringResource(R.string.fx_history_variation_label)
    val variationText = when (variation.direction) {
        RateDirection.UP -> stringResource(R.string.fx_history_variation_up, formattedPercent)
        RateDirection.DOWN -> stringResource(R.string.fx_history_variation_down, formattedPercent)
        RateDirection.UNCHANGED -> stringResource(R.string.fx_history_variation_unchanged, formattedPercent)
    }
    val variationDescription = remember(resources, variation.direction, formattedPercent) {
        formatVariationContentDescription(resources, variation.direction, formattedPercent)
    }
    val (containerColor, contentColor) = when (variation.direction) {
        RateDirection.UP -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        RateDirection.DOWN -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        RateDirection.UNCHANGED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ExchangeRateHistoryCardTags.VARIATION)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = variationDescription
            },
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = variationLabel,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = variationText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CycleRow(
    cycle: ConversionCycle,
    index: Int,
    locale: Locale,
    resources: Resources,
) {
    val formattedDate = remember(cycle.billingDate, locale) {
        formatBillingDate(cycle.billingDate, locale)
    }
    val formattedRate = remember(cycle.rate, locale) {
        formatExchangeRate(cycle.rate, locale)
    }
    val formattedAmount = remember(cycle.amountPen, locale) {
        formatPenAmount(cycle.amountPen, locale)
    }
    val cycleDescription = remember(resources, formattedDate, formattedRate, formattedAmount) {
        formatCycleContentDescription(resources, formattedDate, formattedRate, formattedAmount)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ExchangeRateHistoryCardTags.cycle(index))
            .semantics(mergeDescendants = true) {
                contentDescription = cycleDescription
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.fx_history_rate_label, formattedRate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun currentLocale(resources: Resources): Locale {
    val locales = resources.configuration.locales
    return if (!locales.isEmpty) {
        locales[0]
    } else {
        Locale.getDefault()
    }
}

private fun formatBillingDate(date: LocalDate, locale: Locale): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return date.format(formatter)
}

private fun formatExchangeRate(rate: BigDecimal, locale: Locale): String {
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 4
    }
    return formatter.format(rate)
}

private fun formatPenAmount(amountPen: BigDecimal, locale: Locale): String {
    val formatter = NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance(PEN_CURRENCY_CODE)
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return formatter.format(amountPen)
}

private fun formatPercentMagnitude(percentChange: BigDecimal, locale: Locale): String {
    val formatter = NumberFormat.getPercentInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 2
    }
    return formatter.format(percentChange.abs().movePointLeft(2))
}

private fun formatVariationContentDescription(
    resources: Resources,
    direction: RateDirection,
    formattedPercent: String,
): String {
    val resId = when (direction) {
        RateDirection.UP -> R.string.fx_history_variation_cd_up
        RateDirection.DOWN -> R.string.fx_history_variation_cd_down
        RateDirection.UNCHANGED -> R.string.fx_history_variation_cd_unchanged
    }
    return resources.getString(resId, formattedPercent)
}

private fun formatCycleContentDescription(
    resources: Resources,
    formattedDate: String,
    formattedRate: String,
    formattedAmount: String,
): String {
    return resources.getString(
        R.string.fx_history_cycle_cd,
        formattedDate,
        formattedRate,
        formattedAmount,
    )
}
