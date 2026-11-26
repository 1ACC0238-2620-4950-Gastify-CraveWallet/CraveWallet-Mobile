package com.cravewallet.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cravewallet.app.AppViewModel
import com.cravewallet.app.R
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.data.DeliveryExpenseRecord
import com.cravewallet.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Composable
fun DeliveryScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val today = LocalDate.now(ZoneId.of("America/Lima"))
    val defaultCategory = stringResource(R.string.delivery_default_category)
    var period by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var summary by remember { mutableStateOf<JSONObject?>(null) }
    var expenses by remember { mutableStateOf<List<DeliveryExpenseRecord>>(emptyList()) }
    var editing by remember { mutableStateOf<DeliveryExpenseRecord?>(null) }
    var deleting by remember { mutableStateOf<DeliveryExpenseRecord?>(null) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(defaultCategory) }
    var date by rememberSaveable { mutableStateOf(today.toString()) }
    var limit by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Preserve the id and payload on an uncertain network result; changing any field creates a new request.
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFingerprint by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun reload() {
        val newSummary = vm.deliverySummary(period.year, period.monthValue)
        val newExpenses = vm.deliveryExpenses(period.year, period.monthValue)
        summary = newSummary
        expenses = newExpenses
        limit = summary?.let { if (it.isNull("spendingLimit")) "" else it.getDouble("spendingLimit").toString() }.orEmpty()
    }
    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; notice = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: resources.getString(R.string.delivery_err_generic) }
            finally { busy = false }
        }
    }
    LaunchedEffect(period) {
        busy = true; error = null; summary = null; expenses = emptyList()
        try { reload() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message } finally { busy = false }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.delivery_title), style = CwType.Title, color = OnSurface)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { period = period.minusMonths(1) }, enabled = !busy) { Text(stringResource(R.string.delivery_prev)) }
            Text("${period.monthValue}/${period.year}", style = CwType.Heading)
            TextButton(onClick = { period = period.plusMonths(1) }, enabled = !busy) { Text(stringResource(R.string.delivery_next)) }
        }
        error?.let { Text(it, color = Error) }
        notice?.let { Text(it, color = Primary) }
        summary?.let { s ->
            Text(stringResource(R.string.delivery_spent, Fmt.pen(s.getDouble("total"), context)), style = CwType.Heading)
            Text(if (s.isNull("remaining")) stringResource(R.string.delivery_no_limit) else stringResource(R.string.delivery_remaining, Fmt.pen(s.getDouble("remaining"), context)))
            if (s.getBoolean("exceeded")) Text(stringResource(R.string.delivery_exceeded), color = Error)
            val categories = s.getJSONObject("totalsByCategory")
            categories.keys().forEach { key -> Text("$key: ${Fmt.pen(categories.getDouble(key), context)}") }
        }
        TextButton(onClick = { run { reload() } }, enabled = !busy) { Text(stringResource(R.string.delivery_refresh)) }
        OutlinedTextField(limit, { limit = it }, label = { Text(stringResource(R.string.delivery_budget_label)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            run {
                val value = if (limit.isBlank()) null else limit.replace(',', '.').toDoubleOrNull()
                    ?: throw IllegalArgumentException(resources.getString(R.string.delivery_err_invalid_budget))
                require(value == null || (value.isFinite() && value > 0)) { resources.getString(R.string.delivery_err_positive_budget) }
                vm.deliveryBudget(period.year, period.monthValue, value)
                notice = resources.getString(R.string.delivery_budget_saved)
                try { reload() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = resources.getString(R.string.delivery_err_saved_no_refresh) }
            }
        }, enabled = !busy) { Text(stringResource(R.string.delivery_save_budget)) }
        HorizontalDivider()
        Text(stringResource(R.string.delivery_record_title), style = CwType.Heading)
        OutlinedTextField(merchant, { merchant = it }, label = { Text(stringResource(R.string.delivery_merchant_label)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it }, label = { Text(stringResource(R.string.delivery_amount_label)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(category, { category = it }, label = { Text(stringResource(R.string.delivery_category_label)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, label = { Text(stringResource(R.string.delivery_date_label)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            run {
                val value = amount.replace(',', '.').toDoubleOrNull() ?: throw IllegalArgumentException(resources.getString(R.string.delivery_err_invalid_amount))
                require(value.isFinite() && value > 0 && merchant.isNotBlank() && category.isNotBlank()) { resources.getString(R.string.delivery_err_incomplete) }
                val expenseDate = LocalDate.parse(date)
                require(!expenseDate.isAfter(today)) { resources.getString(R.string.delivery_err_future_date) }
                val fingerprint = listOf(merchant.trim(), value.toString(), category.trim(), date).joinToString("|")
                val id = if (pendingFingerprint == fingerprint && pendingId != null) pendingId!! else UUID.randomUUID().toString()
                pendingId = id; pendingFingerprint = fingerprint
                vm.deliveryExpense(id, merchant.trim(), value, category.trim(), date)
                pendingId = null; pendingFingerprint = null; merchant = ""; amount = ""; notice = resources.getString(R.string.delivery_expense_saved)
                period = expenseDate.withDayOfMonth(1)
                try { reload() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = resources.getString(R.string.delivery_err_expense_no_refresh) }
            }
        }, enabled = !busy) { Text(stringResource(if (busy) R.string.delivery_processing else R.string.delivery_save_expense)) }
        HorizontalDivider()
        Text(stringResource(R.string.delivery_expenses_month), style = CwType.Heading)
        if (expenses.isEmpty() && !busy) Text(stringResource(R.string.delivery_no_expenses))
        expenses.forEach { expense ->
            Card(Modifier.fillMaxWidth().testTag("delivery-record-${expense.id}")) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(expense.merchant, style = CwType.Heading)
                    Text("${expense.category} · ${Fmt.dayMonthYear(expense.date)}")
                    Text(Fmt.pen(expense.amount.toDouble(), context))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { error = null; editing = expense }, enabled = !busy,
                            modifier = Modifier.testTag("delivery-edit-${expense.id}")) { Text(stringResource(R.string.common_edit)) }
                        TextButton(onClick = { error = null; deleting = expense }, enabled = !busy,
                            modifier = Modifier.testTag("delivery-delete-${expense.id}")) { Text(stringResource(R.string.common_delete)) }
                    }
                }
            }
        }
    }
    editing?.let { expense ->
        DeliveryExpenseEditor(expense, busy, error, onDismiss = { if (!busy) editing = null }, onSave = { corrected ->
            run {
                vm.correctDeliveryExpense(corrected)
                editing = null
                notice = resources.getString(R.string.delivery_expense_updated)
                try { reload() } catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = resources.getString(R.string.delivery_err_saved_no_refresh) }
            }
        })
    }
    deleting?.let { expense ->
        AlertDialog(
            onDismissRequest = { if (!busy) deleting = null },
            title = { Text(stringResource(R.string.delivery_delete_title)) },
            text = { Column { Text(stringResource(R.string.delivery_delete_confirm, expense.merchant)); error?.let { Text(it, color = Error) } } },
            confirmButton = { TextButton(onClick = {
                run {
                    vm.deleteDeliveryExpense(expense.id)
                    deleting = null
                    notice = resources.getString(R.string.delivery_expense_deleted)
                    try { reload() } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = resources.getString(R.string.delivery_err_saved_no_refresh) }
                }
            }, enabled = !busy, modifier = Modifier.testTag("delivery-confirm-delete")) { Text(stringResource(R.string.common_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }, enabled = !busy) { Text(stringResource(R.string.common_back)) } },
        )
    }
}

@Composable
internal fun DeliveryExpenseEditor(expense: DeliveryExpenseRecord, busy: Boolean, serverError: String?, onDismiss: () -> Unit, onSave: (DeliveryExpenseRecord) -> Unit) {
    val resources = LocalResources.current
    var merchant by rememberSaveable(expense.id) { mutableStateOf(expense.merchant) }
    var amount by rememberSaveable(expense.id) { mutableStateOf(expense.amount.toPlainString()) }
    var category by rememberSaveable(expense.id) { mutableStateOf(expense.category) }
    var date by rememberSaveable(expense.id) { mutableStateOf(expense.date.toString()) }
    var validationError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.delivery_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(merchant, { merchant = it }, label = { Text(stringResource(R.string.delivery_merchant_label)) }, enabled = !busy, modifier = Modifier.testTag("delivery-edit-merchant"))
                OutlinedTextField(amount, { amount = it }, label = { Text(stringResource(R.string.delivery_amount_label)) }, enabled = !busy, modifier = Modifier.testTag("delivery-edit-amount"))
                OutlinedTextField(category, { category = it }, label = { Text(stringResource(R.string.delivery_category_label)) }, enabled = !busy)
                OutlinedTextField(date, { date = it }, label = { Text(stringResource(R.string.delivery_date_label)) }, enabled = !busy, modifier = Modifier.testTag("delivery-edit-date"))
                (validationError ?: serverError)?.let { Text(it, color = Error) }
            }
        },
        confirmButton = { TextButton(onClick = {
            val parsedAmount = amount.replace(',', '.').toBigDecimalOrNull()
            val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
            validationError = when {
                merchant.isBlank() || category.isBlank() || parsedAmount == null || parsedAmount.signum() <= 0 || parsedAmount.scale() > 2 -> resources.getString(R.string.delivery_err_incomplete)
                parsedDate == null -> resources.getString(R.string.delivery_err_invalid_date)
                parsedDate.isAfter(LocalDate.now(ZoneId.of("America/Lima"))) -> resources.getString(R.string.delivery_err_future_date)
                else -> null
            }
            if (validationError == null && parsedAmount != null && parsedDate != null)
                onSave(expense.copy(merchant = merchant.trim(), amount = parsedAmount, category = category.trim(), date = parsedDate))
        }, enabled = !busy, modifier = Modifier.testTag("delivery-save-edit")) { Text(stringResource(R.string.delivery_save_changes)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.common_back)) } },
    )
}
