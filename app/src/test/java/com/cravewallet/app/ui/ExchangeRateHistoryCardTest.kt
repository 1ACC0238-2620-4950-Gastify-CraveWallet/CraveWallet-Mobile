package com.cravewallet.app.ui

import android.app.Application
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.cravewallet.app.data.ConversionCycle
import com.cravewallet.app.data.ExchangeRateHistory
import com.cravewallet.app.data.ExchangeRateVariation
import com.cravewallet.app.data.RateDirection
import com.cravewallet.app.ui.expenses.ExchangeRateHistoryCard
import com.cravewallet.app.ui.expenses.ExchangeRateHistoryCardTags
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExchangeRateHistoryCardTest {

    @get:Rule
    val compose = createComposeRule()

    private var previousLocale: Locale? = null

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun tearDown() {
        previousLocale?.let { Locale.setDefault(it) }
    }

    @Test
    fun loadingState_showsLoadingWithoutEmptyOrError() {
        setEnglishCardContent(
            history = null,
            loading = true,
            error = "Stale network error",
        )

        compose.onNodeWithTag(ExchangeRateHistoryCardTags.ROOT).assertIsDisplayed()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.TITLE)
            .assertIsDisplayed()
            .assertTextContains("Exchange rate history")
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.LOADING)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertTextContains("Loading", substring = true)

        compose.onNodeWithTag(ExchangeRateHistoryCardTags.EMPTY).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.ERROR).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.RETRY).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.VARIATION).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.cycle(0)).assertDoesNotExist()
    }

    @Test
    fun loadedState_withMultipleCyclesAndUpVariation_displaysCyclesAndUpSemantics() {
        val cycleOne = ConversionCycle(
            billingDate = LocalDate.of(2026, 3, 15),
            rate = BigDecimal("3.785"),
            amountPen = BigDecimal("151.40"),
        )
        val cycleTwo = ConversionCycle(
            billingDate = LocalDate.of(2026, 2, 15),
            rate = BigDecimal("3.694"),
            amountPen = BigDecimal("147.76"),
        )
        val history = ExchangeRateHistory(
            cycles = listOf(cycleOne, cycleTwo),
            variation = ExchangeRateVariation(
                percentChange = BigDecimal("2.46"),
                direction = RateDirection.UP,
            ),
        )

        setEnglishCardContent(
            history = history,
            loading = false,
            error = null,
        )

        compose.onNodeWithTag(ExchangeRateHistoryCardTags.TITLE).assertIsDisplayed()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.VARIATION)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertTextContains("Up", substring = true)
            .assertTextContains("2.46%", substring = true)
            .assertContentDescriptionContains("increased", substring = true)
            .assertContentDescriptionContains("2.46%", substring = true)

        compose.onNodeWithTag(ExchangeRateHistoryCardTags.cycle(0))
            .assertExists()
            .assertTextContains(formatEnglishDate(cycleOne.billingDate), substring = true)
            .assertTextContains("3.785", substring = true)
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.cycle(1))
            .assertExists()
            .assertTextContains(formatEnglishDate(cycleTwo.billingDate), substring = true)
            .assertTextContains("3.694", substring = true)

        compose.onNodeWithTag(ExchangeRateHistoryCardTags.LOADING).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.EMPTY).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.ERROR).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.RETRY).assertDoesNotExist()
    }

    @Test
    fun loadedState_withDownVariation_displaysDownTextAndSemantics() {
        val cycleOne = ConversionCycle(
            billingDate = LocalDate.of(2026, 4, 10),
            rate = BigDecimal("3.640"),
            amountPen = BigDecimal("109.20"),
        )
        val cycleTwo = ConversionCycle(
            billingDate = LocalDate.of(2026, 3, 10),
            rate = BigDecimal("3.705"),
            amountPen = BigDecimal("111.15"),
        )
        val history = ExchangeRateHistory(
            cycles = listOf(cycleOne, cycleTwo),
            variation = ExchangeRateVariation(
                percentChange = BigDecimal("-1.75"),
                direction = RateDirection.DOWN,
            ),
        )

        setEnglishCardContent(
            history = history,
            loading = false,
            error = null,
        )

        compose.onNodeWithTag(ExchangeRateHistoryCardTags.VARIATION)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertTextContains("Down", substring = true)
            .assertTextContains("1.75%", substring = true)
            .assertContentDescriptionContains("decreased", substring = true)
            .assertContentDescriptionContains("1.75%", substring = true)

        compose.onNodeWithTag(ExchangeRateHistoryCardTags.cycle(0))
            .assertExists()
            .assertTextContains(formatEnglishDate(cycleOne.billingDate), substring = true)
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.cycle(1))
            .assertExists()
            .assertTextContains(formatEnglishDate(cycleTwo.billingDate), substring = true)
    }

    @Test
    fun loadedState_withUnchangedVariation_displaysUnchangedTextAndSemantics() {
        val cycleOne = ConversionCycle(
            billingDate = LocalDate.of(2026, 5, 1),
            rate = BigDecimal("3.720"),
            amountPen = BigDecimal("74.40"),
        )
        val cycleTwo = ConversionCycle(
            billingDate = LocalDate.of(2026, 4, 1),
            rate = BigDecimal("3.720"),
            amountPen = BigDecimal("74.40"),
        )
        val history = ExchangeRateHistory(
            cycles = listOf(cycleOne, cycleTwo),
            variation = ExchangeRateVariation(
                percentChange = BigDecimal.ZERO,
                direction = RateDirection.UNCHANGED,
            ),
        )

        setEnglishCardContent(
            history = history,
            loading = false,
            error = null,
        )

        compose.onNodeWithTag(ExchangeRateHistoryCardTags.VARIATION)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertTextContains("Unchanged", substring = true)
            .assertTextContains("0.0%", substring = true)
            .assertContentDescriptionContains("unchanged", substring = true)

        compose.onNodeWithTag(ExchangeRateHistoryCardTags.cycle(0))
            .assertExists()
            .assertTextContains(formatEnglishDate(cycleOne.billingDate), substring = true)
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.cycle(1))
            .assertExists()
            .assertTextContains(formatEnglishDate(cycleTwo.billingDate), substring = true)
    }

    @Test
    fun emptyState_whenCyclesEmpty_displaysEmptyMessage() {
        setEnglishCardContent(
            history = ExchangeRateHistory(cycles = emptyList(), variation = null),
            loading = false,
            error = null,
        )

        compose.onNodeWithTag(ExchangeRateHistoryCardTags.EMPTY)
            .assertIsDisplayed()
            .assertTextContains("No exchange rate history", substring = true)
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.LOADING).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.ERROR).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.RETRY).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.VARIATION).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.cycle(0)).assertDoesNotExist()
    }

    @Test
    fun emptyState_whenHistoryNull_displaysEmptyMessage() {
        setEnglishCardContent(
            history = null,
            loading = false,
            error = null,
        )

        compose.onNodeWithTag(ExchangeRateHistoryCardTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.VARIATION).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.cycle(0)).assertDoesNotExist()
    }

    @Test
    fun errorState_displaysErrorMessageAndInvokesRetryCallback() {
        var retryCount = 0
        setEnglishCardContent(
            history = null,
            loading = false,
            error = "Service temporarily unavailable",
            onRetry = { retryCount++ },
        )

        compose.onNodeWithTag(ExchangeRateHistoryCardTags.ERROR)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertTextContains("Service temporarily unavailable")
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.RETRY)
            .assertIsDisplayed()
            .assertTextContains("Retry")
            .performClick()

        assertEquals(1, retryCount)
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.LOADING).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.EMPTY).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.VARIATION).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.cycle(0)).assertDoesNotExist()
    }

    @Test
    fun loadedState_withNullVariation_displaysCycleWithoutVariationBanner() {
        val singleCycle = ConversionCycle(
            billingDate = LocalDate.of(2026, 6, 20),
            rate = BigDecimal("3.75"),
            amountPen = BigDecimal("93.75"),
        )
        setEnglishCardContent(
            history = ExchangeRateHistory(cycles = listOf(singleCycle), variation = null),
            loading = false,
            error = null,
        )

        compose.onNodeWithTag(ExchangeRateHistoryCardTags.VARIATION).assertDoesNotExist()
        compose.onNodeWithTag(ExchangeRateHistoryCardTags.cycle(0))
            .assertExists()
            .assertTextContains(formatEnglishDate(singleCycle.billingDate), substring = true)
    }

    private fun setEnglishCardContent(
        history: ExchangeRateHistory?,
        loading: Boolean,
        error: String?,
        onRetry: () -> Unit = {},
    ) {
        val app = RuntimeEnvironment.getApplication()
        val englishConfig = Configuration(app.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        val englishResources = app.createConfigurationContext(englishConfig).resources
        compose.setContent {
            CompositionLocalProvider(
                LocalConfiguration provides englishConfig,
                LocalResources provides englishResources,
            ) {
                MaterialTheme {
                    ExchangeRateHistoryCard(
                        history = history,
                        loading = loading,
                        error = error,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }

    private fun formatEnglishDate(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.ENGLISH))
    }
}
