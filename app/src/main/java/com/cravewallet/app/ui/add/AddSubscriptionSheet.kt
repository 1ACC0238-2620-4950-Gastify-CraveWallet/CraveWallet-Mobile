package com.cravewallet.app.ui.add

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cravewallet.app.AppViewModel
import com.cravewallet.app.R
import com.cravewallet.app.data.AppState
import com.cravewallet.app.data.Catalog
import com.cravewallet.app.data.Category
import com.cravewallet.app.data.Currency
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.data.Frequency
import com.cravewallet.app.data.KnownService
import com.cravewallet.app.data.LeadTime
import com.cravewallet.app.data.Subscription
import com.cravewallet.app.reminders.CalendarHelper
import com.cravewallet.app.reminders.ReminderPlanner
import com.cravewallet.app.ui.AppActions
import com.cravewallet.app.ui.Routes
import com.cravewallet.app.ui.components.Choice
import com.cravewallet.app.ui.components.CwCard
import com.cravewallet.app.ui.components.CwDropdownField
import com.cravewallet.app.ui.components.CwIcon
import com.cravewallet.app.ui.components.CwPickerField
import com.cravewallet.app.ui.components.CwSearchBar
import com.cravewallet.app.ui.components.CwSwitch
import com.cravewallet.app.ui.components.CwTextButton
import com.cravewallet.app.ui.components.CwTextField
import com.cravewallet.app.ui.components.DialogIcon
import com.cravewallet.app.ui.components.InfoBanner
import com.cravewallet.app.ui.components.PrimaryButton
import com.cravewallet.app.ui.components.SecondaryButton
import com.cravewallet.app.ui.components.ServiceAvatar
import com.cravewallet.app.ui.components.SheetOverlay
import com.cravewallet.app.ui.components.Stepper
import com.cravewallet.app.ui.components.TonalButton
import com.cravewallet.app.ui.expenses.CwChip
import com.cravewallet.app.ui.theme.CwType
import com.cravewallet.app.ui.theme.Error
import com.cravewallet.app.ui.theme.OnSurface
import com.cravewallet.app.ui.theme.OnSurfaceVariant
import com.cravewallet.app.ui.theme.OutlineVariant
import com.cravewallet.app.ui.theme.Primary
import com.cravewallet.app.ui.theme.PrimaryContainer
import com.cravewallet.app.ui.theme.Surface
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Petición para abrir el alta: [editId] null = nueva suscripción. */
data class AddRequest(val editId: String?)

/** Borrador del formulario. */
private data class Draft(
    val name: String = "",
    val amount: String = "",
    val currency: Currency = Currency.PEN,
    val frequency: Frequency = Frequency.MENSUAL,
    val nextCharge: LocalDate? = null,
    val category: Category? = null,
    val notes: String = "",
) {
    val amountValue: Double? get() = amount.replace(',', '.').toDoubleOrNull()

    fun errors(today: LocalDate): Map<String, String> = buildMap {
        if (name.isBlank()) put("name", "Ingresa el nombre del servicio.")
        val a = amountValue
        if (a == null || a <= 0) put("amount", "Ingresa un monto mayor a 0.")
        when {
            nextCharge == null -> put("date", "Elige la fecha del próximo cobro.")
            nextCharge.isBefore(today) -> put("date", "La fecha no puede ser pasada.")
        }
        if (category == null) put("category", "Elige una categoría.")
    }
}

private val steps = listOf("Servicio", "Detalles", "Recordatorio")

@Composable
fun AddSubscriptionSheet(
    request: AddRequest,
    state: AppState,
    vm: AppViewModel,
    actions: AppActions,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val editing = remember(request) { request.editId?.let { vm.subscription(it) } }
    val initial = remember(request) {
        editing?.let {
            Draft(it.name, Fmt.rate(it.amount), it.currency, it.frequency, it.nextCharge, it.category, it.notes)
        } ?: Draft()
    }
    var draft by remember(request) { mutableStateOf(initial) }
    var step by rememberSaveable(request) { mutableStateOf(if (editing != null) 2 else 1) }
    var showErrors by remember { mutableStateOf(false) }
    var lead by remember { mutableStateOf(editing?.leadTime ?: state.reminders.leadTime) }
    var calendar by remember { mutableStateOf(editing?.calendarEventId != null || (editing == null && state.reminders.calendarEnabled)) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var calendarDialog by remember { mutableStateOf(false) }
    val dirty = draft != initial
    val today = LocalDate.now()

    fun requestClose() {
        if (dirty) confirmDiscard = true else onClose()
    }

    fun finish(withCalendar: Boolean) {
        val amount = draft.amountValue ?: return
        val next = draft.nextCharge ?: return
        val id = editing?.id ?: vm.newId()
        val sub = Subscription(
            id = id,
            name = draft.name.trim(),
            amount = amount,
            currency = draft.currency,
            frequency = draft.frequency,
            nextCharge = next,
            category = draft.category ?: Category.OTROS,
            notes = draft.notes.trim(),
            unusedSince = editing?.unusedSince,
            cancelled = editing?.cancelled ?: false,
            startDate = editing?.startDate ?: next,
            payments = editing?.payments ?: emptyList(),
            createdAt = editing?.createdAt ?: LocalDateTime.now(),
        )
        val created = vm.save(sub, lead, withCalendar)
        onClose()
        val name = sub.name
        when {
            editing != null -> actions.snack("Cambios guardados.")
            created -> {
                val at = ReminderPlanner.reminderTime(next, lead, state.reminders)
                actions.snack("$name agregado. Te avisaremos el ${Fmt.dayMonth(at.toLocalDate())}.", "Deshacer") { vm.undoAdd(id) }
            }
            withCalendar || calendar -> actions.snack(
                "$name agregado. Sin calendario: te avisaremos por notificación.",
                "Ajustes",
            ) { actions.nav.navigate(Routes.REMINDERS) }
            else -> actions.snack("$name agregado.", "Deshacer") { vm.undoAdd(id) }
        }
    }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        finish(withCalendar = result.values.all { it })
    }

    fun submit() {
        if (calendar && !CalendarHelper.hasPermission(context)) calendarDialog = true
        else finish(withCalendar = calendar)
    }

    SheetOverlay(onDismissRequest = { if (step == 3) step = 2 else requestClose() }) {
        // Encabezado
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = ::requestClose) { CwIcon(R.drawable.ic_close, contentDescription = "Cerrar") }
            Spacer(Modifier.width(4.dp))
            Text(if (editing != null) "Editar gasto" else "Agregar gasto", style = CwType.Title, color = OnSurface, modifier = Modifier.weight(1f))
            Text("Paso $step de 3", style = CwType.Caption, color = OnSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Stepper(step, steps)

        when (step) {
            1 -> StepService(
                onPick = { svc ->
                    draft = draft.copy(
                        name = svc.name,
                        amount = Fmt.rate(svc.amount),
                        currency = svc.currency,
                        category = svc.category,
                    )
                    step = 2
                },
                onManual = { typed ->
                    draft = draft.copy(name = typed)
                    step = 2
                },
            )

            2 -> StepDetails(
                draft = draft,
                state = state,
                showErrors = showErrors,
                onChange = { draft = it },
                onDiscard = ::requestClose,
                onContinue = {
                    if (draft.errors(today).isEmpty()) {
                        showErrors = false
                        step = 3
                    } else showErrors = true
                },
            )

            3 -> StepReminder(
                draft = draft,
                state = state,
                lead = lead,
                onLead = { lead = it },
                calendar = calendar,
                onCalendar = { calendar = it },
                onBack = { step = 2 },
                onSubmit = ::submit,
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = Surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text(if (editing != null) "¿Descartar los cambios?" else "¿Descartar este gasto?", style = CwType.Heading, color = OnSurface) },
            text = {
                val who = draft.name.ifBlank { "este gasto" }
                Text("Se perderán los datos de $who que ingresaste.", style = CwType.Body, color = OnSurface)
            },
            confirmButton = {
                PrimaryButton("Descartar", onClick = { confirmDiscard = false; onClose() }, height = 44.dp, shape = RoundedCornerShape(12.dp))
            },
            dismissButton = { CwTextButton("Seguir editando", onClick = { confirmDiscard = false }) },
        )
    }

    if (calendarDialog) {
        CalendarPermissionDialog(
            onAllow = {
                calendarDialog = false
                calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            },
            onLater = {
                calendarDialog = false
                finish(withCalendar = false)
            },
        )
    }
}

// region Paso 1 · Servicio

@Composable
private fun ColumnScope.StepService(onPick: (KnownService) -> Unit, onManual: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = Catalog.search(query)
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        CwSearchBar(query, { query = it }, "Buscar servicio (ej. Netflix)")
        Spacer(Modifier.height(18.dp))
        Text(if (query.isBlank()) "Populares en Perú" else "Resultados", style = CwType.Heading, color = OnSurface)
        Spacer(Modifier.height(10.dp))
        if (results.isEmpty()) {
            Text("No encontramos «${query.trim()}» en la lista.", style = CwType.Body, color = OnSurfaceVariant)
        }
        results.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { svc -> ServiceTile(svc, Modifier.weight(1f)) { onPick(svc) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = OutlineVariant)
        Text("¿No está en la lista? Regístralo a mano.", style = CwType.Body, color = OnSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        TonalButton(
            if (results.isEmpty() && query.isNotBlank()) "Agregar «${query.trim()}» a mano" else "Ingresar manualmente",
            onClick = { onManual(if (results.isEmpty()) query.trim() else "") },
            icon = R.drawable.ic_edit,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ServiceTile(svc: KnownService, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, OutlineVariant, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ServiceAvatar(svc.name, svc.category)
        Spacer(Modifier.height(8.dp))
        Text(svc.name, style = CwType.Caption, color = OnSurface, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// endregion

// region Paso 2 · Detalles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.StepDetails(
    draft: Draft,
    state: AppState,
    showErrors: Boolean,
    onChange: (Draft) -> Unit,
    onDiscard: () -> Unit,
    onContinue: () -> Unit,
) {
    val today = LocalDate.now()
    val errors = if (showErrors) draft.errors(today) else emptyMap()
    var datePicker by remember { mutableStateOf(false) }

    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Completa los datos. Los campos con * son obligatorios.",
            style = CwType.Body,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (errors.isNotEmpty()) {
            InfoBanner(
                icon = R.drawable.ic_error,
                iconTint = Error,
                title = if (errors.size == 1) "Falta 1 dato obligatorio" else "Faltan ${errors.size} datos obligatorios",
                body = "Revisa los campos marcados para continuar.",
                container = Error.copy(alpha = 0.10f),
            )
        }
        CwTextField(
            label = "Nombre del servicio *",
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            placeholder = "Ej. Notion Plus",
            error = errors["name"],
            maxLength = 40,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CwTextField(
                label = "Monto *",
                value = draft.amount,
                onValueChange = { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) onChange(draft.copy(amount = v)) },
                placeholder = "Ej. 79.90",
                error = errors["amount"],
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1.6f),
                maxLength = 9,
            )
            CwDropdownField(
                label = "Moneda",
                selected = draft.currency,
                choices = Currency.entries.map { Choice(it, it.code, it.label) },
                onSelect = { onChange(draft.copy(currency = it)) },
                modifier = Modifier.weight(1f),
                menuWidth = 232.dp,
            )
        }
        val amount = draft.amountValue
        if (draft.currency != Currency.PEN && amount != null && amount > 0) {
            val monthly = amount * state.rates.rate(draft.currency) / draft.frequency.months
            InfoBanner(
                icon = R.drawable.ic_currency_exchange,
                iconTint = Primary,
                container = PrimaryContainer,
                title = "≈ ${Fmt.pen(monthly)} al mes",
                body = "Con el tipo de cambio de hoy (S/ ${Fmt.rate(state.rates.rate(draft.currency))}, ${Fmt.clock(state.rates.updatedAt)}). Lo actualizamos cada día.",
            )
        }
        CwDropdownField(
            label = "Frecuencia",
            selected = draft.frequency,
            choices = Frequency.entries.map { Choice(it, it.label) },
            onSelect = { onChange(draft.copy(frequency = it)) },
        )
        CwPickerField(
            label = "Próximo cobro *",
            value = draft.nextCharge?.let(Fmt::input),
            placeholder = "dd/mm/aaaa",
            onClick = { datePicker = true },
            trailingIcon = R.drawable.ic_calendar_month,
            error = errors["date"],
            focused = datePicker,
        )
        CwDropdownField(
            label = "Categoría *",
            selected = draft.category,
            choices = Category.entries.map { Choice(it, it.label) },
            onSelect = { onChange(draft.copy(category = it)) },
            placeholder = "Elige una categoría",
            error = errors["category"],
        )
        CwTextField(
            label = "Notas (opcional)",
            value = draft.notes,
            onValueChange = { onChange(draft.copy(notes = it)) },
            placeholder = "Máx. 120 caracteres",
            maxLength = 120,
            imeAction = ImeAction.Done,
        )
        Spacer(Modifier.height(8.dp))
    }
    BottomActions {
        SecondaryButton("Descartar", onClick = onDiscard, modifier = Modifier.weight(1f))
        PrimaryButton("Continuar", onClick = onContinue, modifier = Modifier.weight(1f))
    }

    if (datePicker) {
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (draft.nextCharge ?: today.plusMonths(1)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                CwTextButton("Aceptar", color = Primary, onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        onChange(draft.copy(nextCharge = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    datePicker = false
                })
            },
            dismissButton = { CwTextButton("Cancelar", onClick = { datePicker = false }) },
            colors = DatePickerDefaults.colors(containerColor = Surface),
        ) {
            DatePicker(
                state = pickerState,
                title = { Text("Próximo cobro", style = CwType.BodyStrong, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                colors = DatePickerDefaults.colors(
                    containerColor = Surface,
                    selectedDayContainerColor = Primary,
                    todayDateBorderColor = Primary,
                    todayContentColor = Primary,
                ),
            )
        }
    }
}

// endregion

// region Paso 3 · Recordatorio

@Composable
private fun ColumnScope.StepReminder(
    draft: Draft,
    state: AppState,
    lead: LeadTime,
    onLead: (LeadTime) -> Unit,
    calendar: Boolean,
    onCalendar: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    val next = draft.nextCharge ?: LocalDate.now()
    val amount = draft.amountValue ?: 0.0
    val pen = amount * state.rates.rate(draft.currency)
    val at = ReminderPlanner.reminderTime(next, lead, state.reminders)

    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        CwCard(Modifier.fillMaxWidth(), elevated = false, border = BorderStroke(1.dp, OutlineVariant)) {
            Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ServiceAvatar(draft.name, draft.category)
                    Column(Modifier.weight(1f)) {
                        Text(draft.name, style = CwType.BodyLarge, fontWeight = FontWeight.Medium, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${draft.category?.label ?: ""} · ${draft.frequency.label}", style = CwType.Caption, color = OnSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Fmt.pen(pen), style = CwType.Heading.copy(fontFamily = com.cravewallet.app.ui.theme.Inter, fontWeight = FontWeight.SemiBold), color = OnSurface)
                        if (draft.currency != Currency.PEN) Text(Fmt.money(amount, draft.currency), style = CwType.Caption, color = OnSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = OutlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CwIcon(R.drawable.ic_event, size = 20.dp, tint = OnSurfaceVariant)
                    Text("Próximo cobro: ${Fmt.weekdayDate(next)}", style = CwType.Body, color = OnSurface)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("¿Con cuánta anticipación te avisamos?", style = CwType.Body, color = OnSurface)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(listOf(LeadTime.ONE_DAY, LeadTime.THREE_DAYS, LeadTime.SEVEN_DAYS, LeadTime.SAME_DAY)) { option ->
                CwChip(option.chip, selected = option == lead, showCheck = true) { onLead(option) }
            }
        }
        Spacer(Modifier.height(16.dp))
        CwCard(Modifier.fillMaxWidth(), elevated = false, border = BorderStroke(1.dp, OutlineVariant)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { onCalendar(!calendar) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PrimaryContainer)
                        .padding(9.dp),
                ) { CwIcon(R.drawable.ic_calendar_month, size = 22.dp, tint = Primary) }
                Column(Modifier.weight(1f)) {
                    Text("Agendar en mi calendario", style = CwType.BodyStrong, color = OnSurface)
                    Text(
                        "Evento el ${Fmt.shortWeekday(at.toLocalDate())}, ${Fmt.time(at.hour, at.minute)}",
                        style = CwType.Caption,
                        color = OnSurfaceVariant,
                    )
                }
                CwSwitch(calendar, onCheckedChange = onCalendar)
            }
        }
        Spacer(Modifier.height(16.dp))
        InfoBanner(
            icon = R.drawable.ic_notifications,
            body = if (state.reminders.pushEnabled) "También te avisaremos con una notificación push en este teléfono."
            else "Las notificaciones push se configuran en Perfil › Recordatorios.",
        )
        Spacer(Modifier.height(16.dp))
    }
    BottomActions {
        SecondaryButton("Atrás", onClick = onBack, modifier = Modifier.weight(1f))
        PrimaryButton(if (calendar) "Activar recordatorio" else "Guardar", onClick = onSubmit, modifier = Modifier.weight(1.3f))
    }
}

// endregion

@Composable
private fun BottomActions(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(Modifier.background(Surface).navigationBarsPadding().imePadding()) {
        HorizontalDivider(color = OutlineVariant)
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** A7 · Explica para qué se usa el calendario antes de pedir el permiso del sistema. */
@Composable
fun CalendarPermissionDialog(onAllow: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = Surface,
        shape = RoundedCornerShape(24.dp),
        icon = { DialogIcon(R.drawable.ic_calendar_month) },
        title = { Text("¿Permitir acceso a tu calendario?", style = CwType.Heading, color = OnSurface, textAlign = TextAlign.Center) },
        text = {
            Text(
                "CraveWallet solo crea un evento antes de cada cobro. No lee ni cambia tus otros eventos.",
                style = CwType.Body,
                color = OnSurface,
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PrimaryButton("Permitir", onClick = onAllow, height = 48.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                CwTextButton("Ahora no", onClick = onLater, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}
