package com.cravewallet.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cravewallet.app.R
import com.cravewallet.app.ui.theme.CwType
import com.cravewallet.app.ui.theme.Error
import com.cravewallet.app.ui.theme.OnSurface
import com.cravewallet.app.ui.theme.OnSurfaceVariant
import com.cravewallet.app.ui.theme.Outline
import com.cravewallet.app.ui.theme.OutlineVariant
import com.cravewallet.app.ui.theme.Primary
import com.cravewallet.app.ui.theme.PrimaryContainer
import com.cravewallet.app.ui.theme.Surface

private fun Modifier.fieldFrame(focused: Boolean, error: Boolean) = this
    .fillMaxWidth()
    .height(56.dp)
    .clip(RoundedCornerShape(8.dp))
    .background(Surface)
    .border(
        width = if (focused || error) 2.dp else 1.dp,
        color = when {
            error -> Error
            focused -> Primary
            else -> Outline
        },
        shape = RoundedCornerShape(8.dp),
    )

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = CwType.BodyStrong, color = OnSurface)
}

@Composable
private fun FieldError(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        CwIcon(R.drawable.ic_error, size = 16.dp, tint = Error)
        Text(text, style = CwType.Caption, color = Error)
    }
}

/** Campo de texto del Design System: etiqueta arriba, borde de 1 dp, foco y error con 2 dp. */
@Composable
fun CwTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    @DrawableRes trailingIcon: Int? = null,
    focusRequester: FocusRequester? = null,
    singleLine: Boolean = true,
    maxLength: Int? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(label)
        BasicTextField(
            value = value,
            onValueChange = { if (maxLength == null || it.length <= maxLength) onValueChange(it) },
            singleLine = singleLine,
            textStyle = CwType.BodyLarge.copy(color = OnSurface),
            cursorBrush = SolidColor(Primary),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions.Default,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .semantics {
                    contentDescription = label
                    if (error != null) error(error)
                },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fieldFrame(focused, error != null)
                        .padding(start = 16.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, style = CwType.BodyLarge, color = OnSurfaceVariant)
                        inner()
                    }
                    if (error != null) {
                        Spacer(Modifier.width(8.dp))
                        CwIcon(R.drawable.ic_error_fill, size = 20.dp, tint = Error)
                    }
                    if (trailingIcon != null) {
                        Spacer(Modifier.width(8.dp))
                        CwIcon(trailingIcon, tint = OnSurfaceVariant)
                    }
                }
            },
        )
        if (error != null) FieldError(error)
    }
}

/** Campo de solo lectura que abre un selector (fecha, opciones). */
@Composable
fun CwPickerField(
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes trailingIcon: Int = R.drawable.ic_expand_more,
    error: String? = null,
    focused: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(label)
        Row(
            Modifier
                .fieldFrame(focused, error != null)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .semantics { if (error != null) error(error) }
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value ?: placeholder,
                style = CwType.BodyLarge,
                color = if (value == null) OnSurfaceVariant else OnSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (error != null) {
                CwIcon(R.drawable.ic_error_fill, size = 20.dp, tint = Error)
                Spacer(Modifier.width(8.dp))
            }
            CwIcon(trailingIcon, tint = OnSurfaceVariant)
        }
        if (error != null) FieldError(error)
    }
}

/** Opción de un menú desplegable: código corto + descripción opcional. */
data class Choice<T>(val value: T, val title: String, val subtitle: String? = null)

/** Campo desplegable (Moneda, Frecuencia, Categoría) con menú anclado debajo. */
@Composable
fun <T> CwDropdownField(
    label: String,
    selected: T?,
    choices: List<Choice<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    display: (Choice<T>) -> String = { it.title },
    menuWidth: Dp? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var fieldWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val current = choices.firstOrNull { it.value == selected }
    Box(modifier.onSizeChanged { fieldWidth = it.width }) {
        CwPickerField(
            label = label,
            value = current?.let(display),
            placeholder = placeholder,
            onClick = { expanded = true },
            trailingIcon = if (expanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_expand_more,
            error = error,
            focused = expanded,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Surface)
                .width(menuWidth ?: with(density) { fieldWidth.toDp() }),
            shape = RoundedCornerShape(8.dp),
            containerColor = Surface,
        ) {
            choices.forEach { choice ->
                val isSelected = choice.value == selected
                DropdownMenuItem(
                    modifier = Modifier.background(if (isSelected) PrimaryContainer else Color.Transparent),
                    text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(choice.title, style = CwType.BodyStrong, color = OnSurface)
                            if (choice.subtitle != null) Text(choice.subtitle, style = CwType.Body, color = OnSurface)
                        }
                    },
                    trailingIcon = if (isSelected) {
                        { CwIcon(R.drawable.ic_check, size = 20.dp, tint = Primary) }
                    } else null,
                    onClick = {
                        onSelect(choice.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Barra de búsqueda con ícono de lupa. */
@Composable
fun CwSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = CwType.BodyLarge.copy(color = OnSurface),
        cursorBrush = SolidColor(Primary),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fieldFrame(focused, false)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CwIcon(R.drawable.ic_search, tint = OnSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = CwType.BodyLarge, color = OnSurfaceVariant, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Borrar búsqueda") { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) { CwIcon(R.drawable.ic_close, size = 20.dp, tint = OnSurfaceVariant) }
                }
            }
        },
    )
}

/** Stepper horizontal de 3 pasos del alta. */
@Composable
fun Stepper(current: Int, labels: List<String>, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(48.dp)) {
        // Conectores
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 11.dp)
                // centro del círculo (16 + 48) + radio (12) + separación (8)
                .padding(horizontal = 84.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            repeat(labels.size - 1) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (i + 1 < current) Primary else OutlineVariant),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { i, label ->
                val step = i + 1
                val done = step < current
                val active = step == current
                Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (done || active) Primary else Surface)
                            .then(if (!done && !active) Modifier.border(1.5.dp, OnSurfaceVariant, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) CwIcon(R.drawable.ic_check, size = 16.dp, tint = Color.White)
                        else Text("$step", style = CwType.Caption, color = if (active) Color.White else OnSurfaceVariant, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    }
                    Text(
                        label,
                        style = CwType.Caption,
                        color = if (done || active) OnSurface else OnSurfaceVariant,
                        fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                    )
                }
            }
        }
    }
}
