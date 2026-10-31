package dev.gavenda.yuuka.ui.investments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.PricePoint
import dev.gavenda.yuuka.data.model.Security
import dev.gavenda.yuuka.data.model.Trade
import dev.gavenda.yuuka.domain.Holding
import dev.gavenda.yuuka.domain.TradeKinds
import dev.gavenda.yuuka.domain.holdings
import dev.gavenda.yuuka.domain.positions
import dev.gavenda.yuuka.repository.InvestmentRepository
import dev.gavenda.yuuka.repository.LedgerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class InvestmentsUiState(
    val trades: List<Trade> = emptyList(),
    val securities: List<Security> = emptyList(),
    /** Every position, sold-out ones included; [held] is the ones still holding shares. */
    val holdings: List<Holding> = emptyList(),
    /** The accounts a trade can be made in: active ones holding pesos. */
    val accounts: List<Account> = emptyList(),
    /** The broker accounts in use, with the cash they hold. */
    val brokerCash: Map<String, Long> = emptyMap(),
    val loaded: Boolean = false,
) {
    val held: List<Holding> get() = holdings.filter { it.shares > 0 }
    val securitiesBySymbol: Map<String, Security> get() = securities.associateBy { it.symbol }

    /** The latest trading day any stock was priced at. */
    val pricesAsOf: String? get() = held.mapNotNull { it.asOf }.maxOrNull()

    /** What selling has realised since 1 January, average cost. */
    val realisedThisYear: Long
        get() {
            val yearStart = "${LocalDate.now().year}-01-01"
            val all = positions(trades)?.sumOf { it.realised } ?: 0
            val before = positions(trades.filter { it.occurredOn < yearStart })?.sumOf { it.realised } ?: 0
            return all - before
        }

    /** Dividends received since 1 January, after the final tax. */
    val dividendsThisYear: Long
        get() {
            val yearStart = "${LocalDate.now().year}-01-01"
            return trades.filter { it.kind == TradeKinds.DIVIDEND && it.occurredOn >= yearStart }.sumOf { it.amount }
        }
}

/** What the trade form hands back. */
data class TradeSubmission(
    val accountId: String,
    val symbol: String,
    val kind: String,
    val occurredOn: String,
    val shares: Long,
    val price: Long,
    val fees: Long,
)

/** Backs Investments and a holding's screen alike: one ledger of trades, read two ways. */
class InvestmentsViewModel(
    private val repository: InvestmentRepository,
    ledgerRepository: LedgerRepository,
) : ViewModel() {
    val uiState: StateFlow<InvestmentsUiState> = combine(
        repository.trades,
        repository.securities,
        ledgerRepository.accounts,
    ) { trades, securities, accounts ->
        val bySymbol = securities.associateBy { it.symbol }
        val byId = accounts.associateBy { it.id }
        val brokers = trades.map { it.accountId }.distinct()
        InvestmentsUiState(
            trades = trades,
            securities = securities,
            holdings = holdings(trades, bySymbol, { byId[it]?.name }, { byId[it]?.bank }),
            accounts = accounts.filter { !it.archived && it.currency == "PHP" },
            brokerCash = brokers.associateWith { byId[it]?.balance ?: 0L },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InvestmentsUiState())

    init {
        // Whatever is cached is already on screen; this brings prices and trades up to date.
        viewModelScope.launch { runCatching { repository.refresh() } }
    }

    fun prices(symbol: String): Flow<List<PricePoint>> = repository.prices(symbol)

    fun refreshPrices(symbol: String) {
        viewModelScope.launch { runCatching { repository.refreshPrices(symbol) } }
    }

    suspend fun createTrade(submission: TradeSubmission) = with(submission) {
        repository.createTrade(accountId, symbol, kind, occurredOn, shares, price, fees)
    }

    suspend fun deleteTrade(id: String) = repository.deleteTrade(id)

    /** Whether the history still stands without this trade: a later sell may need its shares. */
    fun canDelete(id: String): Boolean = positions(uiState.value.trades.filter { it.id != id }) != null
}
