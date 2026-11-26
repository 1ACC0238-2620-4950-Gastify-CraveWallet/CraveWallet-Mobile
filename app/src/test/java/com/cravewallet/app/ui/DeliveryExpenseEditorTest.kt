package com.cravewallet.app.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.cravewallet.app.data.DeliveryExpenseRecord
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class DeliveryExpenseEditorTest {
    @get:Rule val compose = createComposeRule()
    private val record = DeliveryExpenseRecord("record-1", "Shop", BigDecimal("12.50"), "Food", LocalDate.of(2019, 11, 3))

    @Test fun validCorrectionPreservesTheIdAndAcceptsHistoricalDates() {
        var saved: DeliveryExpenseRecord? = null
        compose.setContent { MaterialTheme { DeliveryExpenseEditor(record, false, null, {}, { saved = it }) } }
        compose.onNodeWithTag("delivery-edit-amount").performTextReplacement("20,25")
        compose.onNodeWithTag("delivery-save-edit").performClick()
        compose.runOnIdle {
            assertEquals("record-1", saved!!.id)
            assertEquals(BigDecimal("20.25"), saved!!.amount)
            assertEquals(record.date, saved!!.date)
        }
    }

    @Test fun invalidAmountIsRejectedWithoutSendingAMutation() {
        var saved = false
        compose.setContent { MaterialTheme { DeliveryExpenseEditor(record, false, null, {}, { saved = true }) } }
        compose.onNodeWithTag("delivery-edit-amount").performTextReplacement("-2")
        compose.onNodeWithTag("delivery-save-edit").performClick()
        compose.runOnIdle { assertFalse(saved) }
    }

    @Test fun pendingSaveDisablesTheAction() {
        compose.setContent { MaterialTheme { DeliveryExpenseEditor(record, true, null, {}, {}) } }
        compose.onNodeWithTag("delivery-save-edit").assertIsNotEnabled()
        compose.onNodeWithTag("delivery-edit-amount").assertIsNotEnabled()
    }
}
