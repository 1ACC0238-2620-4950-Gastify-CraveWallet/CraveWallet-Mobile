package com.cravewallet.app.ui.expenses

import androidx.compose.runtime.*
import com.cravewallet.app.AppViewModel
import com.cravewallet.app.data.ExchangeRateHistory
import kotlinx.coroutines.CancellationException

@Composable
internal fun ConnectedExchangeRateHistory(id: String, vm: AppViewModel) {
    var history by remember(id) { mutableStateOf<ExchangeRateHistory?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var retry by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(id, retry) {
        loading = true
        error = null
        try { history = vm.exchangeRateHistory(id) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message.orEmpty() }
        finally { loading = false }
    }
    ExchangeRateHistoryCard(history, loading, error, onRetry = { retry++ })
}
