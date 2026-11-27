package com.cravewallet.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cravewallet.app.AppViewModel
import com.cravewallet.app.R
import com.cravewallet.app.data.Fmt
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
    val today = LocalDate.now(ZoneId.of("America/Lima"))
    val defaultCategory = stringResource(R.string.delivery_default_category)
    var period by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var summary by remember { mutableStateOf<JSONObject?>(null) }
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
        summary = vm.deliverySummary(period.year, period.monthValue)
        limit = summary?.let { if (it.isNull("spendingLimit")) "" else it.getDouble("spendingLimit").toString() }.orEmpty()
    }
    fun run(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true; error = null; notice = null
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: context.getString(R.string.delivery_err_generic) }
            finally { busy = false }
        }
    }
    LaunchedEffect(period) {
        busy = true; error = null; summary = null
        try { reload() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message } finally { busy = false }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.delivery_title), style = CwType.Title, color = OnSurface)
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
                    ?: throw IllegalArgumentException(context.getString(R.string.delivery_err_invalid_budget))
                require(value == null || (value.isFinite() && value > 0)) { context.getString(R.string.delivery_err_positive_budget) }
                vm.deliveryBudget(period.year, period.monthValue, value)
                notice = context.getString(R.string.delivery_budget_saved)
                try { reload() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = context.getString(R.string.delivery_err_saved_no_refresh) }
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
                val value = amount.replace(',', '.').toDoubleOrNull() ?: throw IllegalArgumentException(context.getString(R.string.delivery_err_invalid_amount))
                require(value.isFinite() && value > 0 && merchant.isNotBlank() && category.isNotBlank()) { context.getString(R.string.delivery_err_incomplete) }
                val expenseDate = LocalDate.parse(date)
                require(!expenseDate.isAfter(today)) { context.getString(R.string.delivery_err_future_date) }
                val fingerprint = listOf(merchant.trim(), value.toString(), category.trim(), date).joinToString("|")
                val id = if (pendingFingerprint == fingerprint && pendingId != null) pendingId!! else UUID.randomUUID().toString()
                pendingId = id; pendingFingerprint = fingerprint
                vm.deliveryExpense(id, merchant.trim(), value, category.trim(), date)
                pendingId = null; pendingFingerprint = null; merchant = ""; amount = ""; notice = context.getString(R.string.delivery_expense_saved)
                period = expenseDate.withDayOfMonth(1)
                try { reload() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = context.getString(R.string.delivery_err_expense_no_refresh) }
            }
        }, enabled = !busy) { Text(stringResource(if (busy) R.string.delivery_processing else R.string.delivery_save_expense)) }
    }
}
