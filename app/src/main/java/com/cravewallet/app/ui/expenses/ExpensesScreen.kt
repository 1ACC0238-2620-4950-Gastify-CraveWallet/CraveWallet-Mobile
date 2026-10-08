package com.cravewallet.app.ui.expenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cravewallet.app.AppViewModel
import com.cravewallet.app.R
import com.cravewallet.app.data.AppState
import com.cravewallet.app.data.Category
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.data.SubStatus
import com.cravewallet.app.ui.AppActions
import com.cravewallet.app.ui.Routes
import com.cravewallet.app.ui.components.CwIcon
import com.cravewallet.app.ui.components.CwSearchBar
import com.cravewallet.app.ui.components.CwTopBar
import com.cravewallet.app.ui.components.EmptyState
import com.cravewallet.app.ui.components.PrimaryButton
import com.cravewallet.app.ui.components.SubscriptionCard
import com.cravewallet.app.ui.components.SwipeActionCard
import com.cravewallet.app.ui.theme.CwType
import com.cravewallet.app.ui.theme.OnPrimaryContainer
import com.cravewallet.app.ui.theme.OnSurface
import com.cravewallet.app.ui.theme.OnSurfaceVariant
import com.cravewallet.app.ui.theme.Outline
import com.cravewallet.app.ui.theme.Primary
import com.cravewallet.app.ui.theme.PrimaryContainer
import com.cravewallet.app.ui.theme.Surface

private enum class SortBy(val label: String) { NEXT("Próximo cobro"), AMOUNT("Monto"), NAME("Nombre") }

@Composable
fun ExpensesScreen(state: AppState, vm: AppViewModel, actions: AppActions, focusSearch: Boolean) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var status by rememberSaveable { mutableStateOf<SubStatus?>(null) }
    var sort by rememberSaveable { mutableStateOf(SortBy.NEXT) }
    var sortMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { focus.requestFocus() } }

    val filtered = state.subscriptions
        .filter { s ->
            (query.isBlank() || s.name.contains(query.trim(), true) || s.category.label.contains(query.trim(), true)) &&
                (category == null || s.category == category) &&
                (status == null || state.status(s) == status)
        }
        .sortedWith(
            compareBy<com.cravewallet.app.data.Subscription> { it.cancelled }.then(
                when (sort) {
                    SortBy.NEXT -> compareBy { it.nextCharge }
                    SortBy.AMOUNT -> compareByDescending { state.monthlyPen(it) }
                    SortBy.NAME -> compareBy { it.name.lowercase() }
                },
            ),
        )

    Column(Modifier.fillMaxSize()) {
        CwTopBar("Gastos", actions = {
            Box {
                IconButton(onClick = { sortMenu = true }) { CwIcon(R.drawable.ic_sort, contentDescription = "Ordenar") }
                SortMenu(sortMenu, sort, onSelect = { sort = it; sortMenu = false }, onDismiss = { sortMenu = false })
            }
        })

        if (state.subscriptions.isEmpty()) {
            EmptyState(
                icon = R.drawable.ic_receipt_long,
                title = "Aún no tienes gastos registrados.",
                body = "Agrega tu primera suscripción y toma el control.",
                modifier = Modifier.padding(top = 48.dp, start = 16.dp, end = 16.dp),
                action = {
                    PrimaryButton("Agregar mi primera suscripción", onClick = actions.openAdd, icon = R.drawable.ic_add, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth())
                },
            )
            return@Column
        }

        CwSearchBar(query, { query = it }, "Buscar por nombre o categoría", Modifier.padding(horizontal = 16.dp), focusRequester = focus)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CwChip("Todas", selected = category == null && status == null, showCheck = true) { category = null; status = null }
            }
            item {
                Box {
                    CwChip(
                        status?.label ?: "Estado",
                        selected = status != null,
                        trailing = R.drawable.ic_expand_more,
                    ) { statusMenu = true }
                    DropdownMenu(statusMenu, onDismissRequest = { statusMenu = false }, containerColor = Surface) {
                        DropdownMenuItem(text = { Text("Todos los estados", style = CwType.Body) }, onClick = { status = null; statusMenu = false })
                        listOf(SubStatus.ACTIVA, SubStatus.COBRO_HOY, SubStatus.PRONTO, SubStatus.SIN_USAR, SubStatus.CANCELADA).forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label, style = CwType.Body) },
                                leadingIcon = { CwIcon(s.icon, size = 18.dp, tint = s.container) },
                                trailingIcon = if (status == s) { { CwIcon(R.drawable.ic_check, size = 18.dp, tint = Primary) } } else null,
                                onClick = { status = s; statusMenu = false },
                            )
                        }
                    }
                }
            }
            items(Category.entries) { c ->
                CwChip(c.label, selected = category == c) { category = if (category == c) null else c }
            }
        }

        val active = state.active
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${active.size} activas · ${Fmt.pen(state.monthlyTotal)} al mes", style = CwType.Caption, color = OnSurfaceVariant)
            Box {
                Row(
                    Modifier
                        .height(44.dp)
                        .clickable { sortMenu = true }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CwIcon(R.drawable.ic_sort, size = 18.dp, tint = Primary)
                    Text(sort.label, style = CwType.BodyStrong, color = Primary)
                }
            }
        }

        if (filtered.isEmpty()) {
            Text(
                if (query.isNotBlank()) "No encontramos «${query.trim()}». Prueba con otro nombre o categoría." else "No hay suscripciones con este filtro.",
                style = CwType.Body,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.id }) { sub ->
                if (sub.cancelled) {
                    SubscriptionCard(sub, state, onClick = { actions.nav.navigate(Routes.detail(sub.id)) })
                } else {
                    val unused = sub.isUnused
                    SwipeActionCard(
                        actionLabel = if (unused) "En uso" else "Sin usar",
                        actionIcon = if (unused) R.drawable.ic_check_circle else R.drawable.ic_visibility_off,
                        onAction = {
                            vm.setUnused(sub.id, !unused)
                            actions.snack(
                                if (unused) "${sub.name} vuelve a estar en uso." else "${sub.name} marcada como sin usar.",
                                action = "Deshacer",
                            ) { vm.setUnused(sub.id, unused) }
                        },
                    ) {
                        SubscriptionCard(sub, state, onClick = { actions.nav.navigate(Routes.detail(sub.id)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(expanded: Boolean, current: SortBy, onSelect: (SortBy) -> Unit, onDismiss: () -> Unit) {
    DropdownMenu(expanded, onDismissRequest = onDismiss, containerColor = Surface) {
        Text("Ordenar por", style = CwType.Caption, color = OnSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        SortBy.entries.forEach { s ->
            DropdownMenuItem(
                text = { Text(s.label, style = CwType.Body, fontWeight = if (s == current) FontWeight.SemiBold else FontWeight.Normal) },
                trailingIcon = if (s == current) { { CwIcon(R.drawable.ic_check, size = 18.dp, tint = Primary) } } else null,
                onClick = { onSelect(s) },
            )
        }
    }
}

@Composable
fun CwChip(
    label: String,
    selected: Boolean,
    showCheck: Boolean = false,
    trailing: Int? = null,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = CwType.BodyStrong) },
        leadingIcon = if (selected && showCheck) {
            { CwIcon(R.drawable.ic_check, size = 18.dp, tint = OnPrimaryContainer) }
        } else null,
        trailingIcon = trailing?.let { { CwIcon(it, size = 18.dp, tint = if (selected) OnPrimaryContainer else OnSurface) } },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Surface,
            labelColor = OnSurface,
            selectedContainerColor = PrimaryContainer,
            selectedLabelColor = OnPrimaryContainer,
        ),
        border = if (selected) null else BorderStroke(1.dp, Outline),
    )
}
