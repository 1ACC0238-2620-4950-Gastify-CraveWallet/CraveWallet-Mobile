package com.cravewallet.app.ui.expenses

import androidx.annotation.StringRes
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

private enum class SortBy(@param:StringRes val labelRes: Int) {
    NEXT(R.string.expenses_sort_next),
    AMOUNT(R.string.expenses_sort_amount),
    NAME(R.string.expenses_sort_name),
}

@Composable
fun ExpensesScreen(state: AppState, vm: AppViewModel, actions: AppActions, focusSearch: Boolean) {
    val context = LocalContext.current
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
            val q = query.trim()
            (q.isBlank() || s.name.contains(q, true) || s.category.legacyLabel.contains(q, true) || s.category.label(context).contains(q, true)) &&
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
        CwTopBar(stringResource(R.string.expenses_title), actions = {
            Box {
                IconButton(onClick = { sortMenu = true }) { CwIcon(R.drawable.ic_sort, contentDescription = stringResource(R.string.common_sort)) }
                SortMenu(sortMenu, sort, onSelect = { sort = it; sortMenu = false }, onDismiss = { sortMenu = false })
            }
        })

        if (state.subscriptions.isEmpty()) {
            EmptyState(
                icon = R.drawable.ic_receipt_long,
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.home_empty_body),
                modifier = Modifier.padding(top = 48.dp, start = 16.dp, end = 16.dp),
                action = {
                    PrimaryButton(
                        stringResource(R.string.home_empty_cta),
                        onClick = actions.openAdd,
                        icon = R.drawable.ic_add,
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    )
                },
            )
            return@Column
        }

        CwSearchBar(query, { query = it }, stringResource(R.string.expenses_search_placeholder), Modifier.padding(horizontal = 16.dp), focusRequester = focus)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CwChip(stringResource(R.string.expenses_filter_all), selected = category == null && status == null, showCheck = true) { category = null; status = null }
            }
            item {
                Box {
                    CwChip(
                        status?.let { stringResource(it.labelRes) } ?: stringResource(R.string.expenses_filter_status),
                        selected = status != null,
                        trailing = R.drawable.ic_expand_more,
                    ) { statusMenu = true }
                    DropdownMenu(statusMenu, onDismissRequest = { statusMenu = false }, containerColor = Surface) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.expenses_filter_all_statuses), style = CwType.Body) }, onClick = { status = null; statusMenu = false })
                        listOf(SubStatus.ACTIVA, SubStatus.COBRO_HOY, SubStatus.PRONTO, SubStatus.SIN_USAR, SubStatus.CANCELADA).forEach { s ->
                            DropdownMenuItem(
                                text = { Text(stringResource(s.labelRes), style = CwType.Body) },
                                leadingIcon = { CwIcon(s.icon, size = 18.dp, tint = s.container) },
                                trailingIcon = if (status == s) { { CwIcon(R.drawable.ic_check, size = 18.dp, tint = Primary) } } else null,
                                onClick = { status = s; statusMenu = false },
                            )
                        }
                    }
                }
            }
            items(Category.entries) { c ->
                CwChip(stringResource(c.labelRes), selected = category == c) { category = if (category == c) null else c }
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
            Text(stringResource(R.string.expenses_active_summary, active.size, Fmt.pen(state.monthlyTotal, context)), style = CwType.Caption, color = OnSurfaceVariant)
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
                    Text(stringResource(sort.labelRes), style = CwType.BodyStrong, color = Primary)
                }
            }
        }

        if (filtered.isEmpty()) {
            Text(
                if (query.isNotBlank()) stringResource(R.string.expenses_empty_search, query.trim())
                else stringResource(R.string.expenses_empty_filter),
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
                        actionLabel = stringResource(if (unused) R.string.expenses_swipe_in_use else R.string.expenses_swipe_unused),
                        actionIcon = if (unused) R.drawable.ic_check_circle else R.drawable.ic_visibility_off,
                        onAction = {
                            vm.setUnused(sub.id, !unused)
                            actions.snack(
                                context.getString(if (unused) R.string.expenses_snack_in_use else R.string.expenses_snack_unused, sub.name),
                                action = context.getString(R.string.common_undo),
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
        Text(stringResource(R.string.expenses_sort_by), style = CwType.Caption, color = OnSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        SortBy.entries.forEach { s ->
            DropdownMenuItem(
                text = { Text(stringResource(s.labelRes), style = CwType.Body, fontWeight = if (s == current) FontWeight.SemiBold else FontWeight.Normal) },
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
