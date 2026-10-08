package com.cravewallet.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cravewallet.app.AppViewModel
import com.cravewallet.app.R
import com.cravewallet.app.data.AppState
import com.cravewallet.app.data.Category
import com.cravewallet.app.data.SubStatus
import com.cravewallet.app.ui.add.AddRequest
import com.cravewallet.app.ui.add.AddSubscriptionSheet
import com.cravewallet.app.ui.analysis.AnalysisScreen
import com.cravewallet.app.ui.analysis.CategoryDetailScreen
import com.cravewallet.app.ui.components.CwIcon
import com.cravewallet.app.ui.expenses.DetailScreen
import com.cravewallet.app.ui.expenses.ExpensesScreen
import com.cravewallet.app.ui.home.HomeScreen
import com.cravewallet.app.ui.premium.PremiumReason
import com.cravewallet.app.ui.premium.PremiumSheet
import com.cravewallet.app.ui.profile.ProfileScreen
import com.cravewallet.app.ui.profile.RemindersScreen
import com.cravewallet.app.ui.theme.Accent
import com.cravewallet.app.ui.theme.Background
import com.cravewallet.app.ui.theme.CwType
import com.cravewallet.app.ui.theme.OnPrimaryContainer
import com.cravewallet.app.ui.theme.OnSurface
import com.cravewallet.app.ui.theme.OnSurfaceVariant
import com.cravewallet.app.ui.theme.PrimaryContainer
import com.cravewallet.app.ui.theme.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val EXPENSES = "expenses?focus={focus}"
    const val ANALYSIS = "analysis"
    const val PROFILE = "profile"
    const val DETAIL = "detail/{id}"
    const val CATEGORY = "category/{name}"
    const val REMINDERS = "reminders"

    fun expenses(focusSearch: Boolean = false) = "expenses?focus=$focusSearch"
    fun detail(id: String) = "detail/$id"
    fun category(c: Category) = "category/${c.name}"
}

private enum class Tab(val label: String, val route: String, @param:DrawableRes val icon: Int, @param:DrawableRes val selectedIcon: Int) {
    INICIO("Inicio", Routes.HOME, R.drawable.ic_home, R.drawable.ic_home_fill),
    GASTOS("Gastos", Routes.EXPENSES, R.drawable.ic_receipt_long, R.drawable.ic_receipt_long_fill),
    ANALISIS("Análisis", Routes.ANALYSIS, R.drawable.ic_bar_chart, R.drawable.ic_bar_chart_fill),
    PERFIL("Perfil", Routes.PROFILE, R.drawable.ic_person, R.drawable.ic_person_fill);

    companion object {
        fun of(route: String?): Tab = when {
            route == null -> INICIO
            route.startsWith("expenses") || route.startsWith("detail") -> GASTOS
            route.startsWith("analysis") || route.startsWith("category") -> ANALISIS
            route.startsWith("profile") || route.startsWith("reminders") -> PERFIL
            else -> INICIO
        }
    }
}

/** Utilidades compartidas por todas las pantallas: navegación, snackbar, alta y Premium. */
@Stable
class AppActions(
    val nav: NavHostController,
    val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
    val openAdd: () -> Unit,
    val openEdit: (String) -> Unit,
    val openPremium: (PremiumReason) -> Unit,
) {
    fun snack(message: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = action,
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }

    fun goTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun back() {
        if (!nav.popBackStack()) goTab(Routes.HOME)
    }
}

@Composable
fun CraveWalletApp(vm: AppViewModel, openSubscriptionId: String? = null, onConsumedDeepLink: () -> Unit = {}) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var addRequest by remember { mutableStateOf<AddRequest?>(null) }
    var premiumReason by remember { mutableStateOf<PremiumReason?>(null) }

    val actions = remember(nav) {
        AppActions(
            nav = nav,
            snackbar = snackbar,
            scope = scope,
            openAdd = {
                if (vm.canAddMore) addRequest = AddRequest(editId = null) else premiumReason = PremiumReason.LIMIT
            },
            openEdit = { id -> addRequest = AddRequest(editId = id) },
            openPremium = { premiumReason = it },
        )
    }

    // Abrir el detalle desde una notificación
    LaunchedEffect(openSubscriptionId) {
        if (openSubscriptionId != null && vm.subscription(openSubscriptionId) != null) {
            nav.navigate(Routes.detail(openSubscriptionId))
            onConsumedDeepLink()
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentTab = Tab.of(route)
    val showFab = route == Routes.HOME || route == Routes.EXPENSES
    val hasSubs = state.subscriptions.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Background,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    containerColor = OnSurface,
                    contentColor = Color.White,
                    actionColor = PrimaryContainer,
                )
            }
        },
        floatingActionButton = {
            if (showFab && hasSubs) {
                ExtendedFloatingActionButton(
                    onClick = actions.openAdd,
                    containerColor = Accent,
                    contentColor = OnSurface,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    icon = { CwIcon(R.drawable.ic_add, tint = OnSurface) },
                    text = { Text("Agregar", style = CwType.Button) },
                )
            }
        },
        bottomBar = { BottomNav(currentTab, state) { tab -> actions.goTab(if (tab == Tab.GASTOS) Routes.expenses() else tab.route) } },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) { HomeScreen(state, actions) }
            composable(
                Routes.EXPENSES,
                arguments = listOf(navArgument("focus") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                ExpensesScreen(state, vm, actions, focusSearch = entry.arguments?.getBoolean("focus") ?: false)
            }
            composable(Routes.ANALYSIS) { AnalysisScreen(state, vm, actions) }
            composable(Routes.PROFILE) { ProfileScreen(state, vm, actions) }
            composable(Routes.DETAIL) { entry ->
                DetailScreen(entry.arguments?.getString("id").orEmpty(), state, vm, actions)
            }
            composable(Routes.CATEGORY) { entry ->
                val category = runCatching { Category.valueOf(entry.arguments?.getString("name").orEmpty()) }.getOrNull()
                if (category != null) CategoryDetailScreen(category, state, actions)
            }
            composable(Routes.REMINDERS) { RemindersScreen(state, vm, actions) }
        }
    }

    addRequest?.let { request ->
        AddSubscriptionSheet(
            request = request,
            state = state,
            vm = vm,
            actions = actions,
            onClose = { addRequest = null },
        )
    }
    premiumReason?.let { reason ->
        PremiumSheet(
            reason = reason,
            state = state,
            onActivate = {
                vm.setPremium(true)
                premiumReason = null
                actions.snack("¡Listo! Ya tienes CraveWallet Premium.")
            },
            onDismiss = { premiumReason = null },
        )
    }
    }
}

@Composable
private fun BottomNav(current: Tab, state: AppState, onSelect: (Tab) -> Unit) {
    val alerts = state.active.count { state.status(it) == SubStatus.COBRO_HOY }
    NavigationBar(containerColor = Surface, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                    BadgedBox(badge = {
                        when {
                            tab == Tab.INICIO && alerts > 0 -> Badge(containerColor = Accent, contentColor = OnSurface) {
                                Text("$alerts", fontWeight = FontWeight.Bold)
                            }
                            tab == Tab.ANALISIS && !state.profile.premium -> Badge(containerColor = OnSurface, contentColor = Color.White) {
                                CwIcon(R.drawable.ic_lock_fill, size = 10.dp, tint = Color.White)
                            }
                        }
                    }) {
                        CwIcon(if (selected) tab.selectedIcon else tab.icon, tint = if (selected) OnPrimaryContainer else OnSurfaceVariant)
                    }
                },
                label = {
                    Text(
                        tab.label,
                        style = CwType.Caption,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) OnSurface else OnSurfaceVariant,
                    )
                },
                colors = NavigationBarItemDefaults.colors(indicatorColor = PrimaryContainer),
            )
        }
    }
}

