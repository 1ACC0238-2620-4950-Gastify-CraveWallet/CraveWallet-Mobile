package com.cravewallet.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cravewallet.app.AppViewModel
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
    val today=LocalDate.now(ZoneId.of("America/Lima"))
    var period by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var summary by remember { mutableStateOf<JSONObject?>(null) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Comida") }
    var date by rememberSaveable { mutableStateOf(today.toString()) }
    var limit by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Preserve the id and payload on an uncertain network result; changing any field creates a new request.
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFingerprint by rememberSaveable { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    suspend fun reload() {
        summary=vm.deliverySummary(period.year,period.monthValue)
        limit=summary?.let { if(it.isNull("spendingLimit")) "" else it.getDouble("spendingLimit").toString() }.orEmpty()
    }
    fun run(block:suspend ()->Unit) {
        if(busy) return
        scope.launch {
            busy=true;error=null;notice=null
            try { block() }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { error=e.message ?: "No se pudo completar la solicitud." }
            finally { busy=false }
        }
    }
    LaunchedEffect(period) {
        busy=true;error=null;summary=null
        try { reload() } catch(e:CancellationException) { throw e } catch(e:Exception) { error=e.message } finally { busy=false }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Gastos de delivery",style=CwType.Title,color=OnSurface)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={period=period.minusMonths(1)},enabled=!busy) {Text("Anterior")}
            Text("${period.monthValue}/${period.year}",style=CwType.Heading)
            TextButton(onClick={period=period.plusMonths(1)},enabled=!busy) {Text("Siguiente")}
        }
        error?.let {Text(it,color=Error)}
        notice?.let {Text(it,color=Primary)}
        summary?.let { s ->
            Text("Gastado: ${Fmt.pen(s.getDouble("total"))}",style=CwType.Heading)
            Text(if(s.isNull("remaining")) "Sin límite mensual" else "Saldo: ${Fmt.pen(s.getDouble("remaining"))}")
            if(s.getBoolean("exceeded")) Text("Superaste tu presupuesto.",color=Error)
            val categories=s.getJSONObject("totalsByCategory")
            categories.keys().forEach { key -> Text("$key: ${Fmt.pen(categories.getDouble(key))}") }
        }
        TextButton(onClick={run {reload()}},enabled=!busy) {Text("Actualizar resumen")}
        OutlinedTextField(limit,{limit=it},label={Text("Presupuesto mensual en PEN (vacío = sin límite)")},enabled=!busy,modifier=Modifier.fillMaxWidth())
        Button(onClick={run {
            val value=if(limit.isBlank()) null else limit.replace(',','.').toDoubleOrNull()
                ?: throw IllegalArgumentException("Escribe un presupuesto válido.")
            require(value==null || (value.isFinite() && value>0)) {"El presupuesto debe ser mayor a cero."}
            vm.deliveryBudget(period.year,period.monthValue,value)
            notice="Presupuesto guardado."
            try {reload()} catch(e:CancellationException) {throw e} catch(e:Exception) {error="Guardado. No se pudo actualizar el resumen."}
        }},enabled=!busy) {Text("Guardar presupuesto")}
        HorizontalDivider()
        Text("Registrar gasto",style=CwType.Heading)
        OutlinedTextField(merchant,{merchant=it},label={Text("Comercio")},enabled=!busy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(amount,{amount=it},label={Text("Monto en PEN")},enabled=!busy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(category,{category=it},label={Text("Categoría")},enabled=!busy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(date,{date=it},label={Text("Fecha (aaaa-mm-dd)")},enabled=!busy,modifier=Modifier.fillMaxWidth())
        Button(onClick={run {
            val value=amount.replace(',','.').toDoubleOrNull() ?: throw IllegalArgumentException("Escribe un monto válido.")
            require(value.isFinite() && value>0 && merchant.isNotBlank() && category.isNotBlank()) {"Completa comercio, categoría y monto positivo."}
            val expenseDate=LocalDate.parse(date)
            require(!expenseDate.isAfter(today)) {"La fecha no puede ser futura."}
            val fingerprint=listOf(merchant.trim(),value.toString(),category.trim(),date).joinToString("|")
            val id=if(pendingFingerprint==fingerprint && pendingId!=null) pendingId!! else UUID.randomUUID().toString()
            pendingId=id;pendingFingerprint=fingerprint
            vm.deliveryExpense(id,merchant.trim(),value,category.trim(),date)
            pendingId=null;pendingFingerprint=null;merchant="";amount="";notice="Gasto guardado."
            period=expenseDate.withDayOfMonth(1)
            try {reload()} catch(e:CancellationException) {throw e} catch(e:Exception) {error="Gasto guardado. No se pudo actualizar el resumen."}
        }},enabled=!busy) {Text(if(busy) "Procesando…" else "Guardar gasto")}
    }
}
