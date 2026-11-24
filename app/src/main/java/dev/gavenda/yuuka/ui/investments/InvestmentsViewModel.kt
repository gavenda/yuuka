package dev.gavenda.yuuka.ui.investments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.EtfMatch
import dev.gavenda.yuuka.data.model.PricePoint
import dev.gavenda.yuuka.data.model.Security
import dev.gavenda.yuuka.data.model.Trade
import dev.gavenda.yuuka.domain.CurrencyTotal
import dev.gavenda.yuuka.domain.Holding
import dev.gavenda.yuuka.domain.TradeKinds
import dev.gavenda.yuuka.domain.holdings
import dev.gavenda.yuuka.domain.positions
import dev.gavenda.yuuka.domain.totalsByCurrency
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
    /** Every position, sold-out ones included; [held] is the ones still holding shares or units. */
    val holdings: List<Holding> = emptyList(),
    /** The accounts a trade can be made in: the active ones. A trade's must hold its security's currency, which the form asks. */
    val accounts: List<Account> = emptyList(),
    /** The broker accounts in use, with the cash they hold. */
    val brokerCash: Map<String, Long> = emptyMap(),
    /** The currency every aggregate figure leads with. */
    val displayCurrency: String = "PHP",
    val loaded: Boolean = false,
) {
    val held: List<Holding> get() = holdings.filter { it.units > 0 }
    val securitiesBySymbol: Map<String, Security> get() = securities.associateBy { it.symbol }

    /** What a security is priced in: pesos for a stock or a fund, an ETF's own currency. */
    fun currencyOf(symbol: String): String = securitiesBySymbol[symbol]?.currency ?: "PHP"

    /** What the holdings come to, a currency at a time, the display currency first: there is no rate to add them up by. */
    val totals: List<CurrencyTotal>
        get() = totalsByCurrency(held, displayCurrency).ifEmpty { listOf(CurrencyTotal(displayCurrency, 0, 0, 0, 0)) }

    /** The latest trading day any stock was priced at. */
    val pricesAsOf: String? get() = held.mapNotNull { it.asOf }.maxOrNull()

    /** Amounts a currency at a time, the display currency first; a currency with nothing is left out, but the display currency always stands. */
    private fun byCurrency(entries: List<Pair<String, Long>>): List<Pair<String, Long>> {
        val sums = entries.groupBy({ it.first }, { it.second }).mapValues { (_, amounts) -> amounts.sum() }.toMutableMap()
        sums.putIfAbsent(displayCurrency, 0)
        return sums.entries
            .filter { (currency, amount) -> amount != 0L || currency == displayCurrency }
            .map { it.key to it.value }
            .sortedWith(compareByDescending<Pair<String, Long>> { it.first == displayCurrency }.thenBy { it.first })
    }

    /** What selling has realised since 1 January, average cost, a currency at a time. */
    val realisedThisYear: List<Pair<String, Long>>
        get() {
            val yearStart = "${LocalDate.now().year}-01-01"
            val all = positions(trades).orEmpty().map { currencyOf(it.symbol) to it.realised }
            val before = positions(trades.filter { it.occurredOn < yearStart }).orEmpty().map { currencyOf(it.symbol) to -it.realised }
            return byCurrency(all + before)
        }

    /** Dividends received since 1 January, after the tax withheld, a currency at a time. */
    val dividendsThisYear: List<Pair<String, Long>>
        get() {
            val yearStart = "${LocalDate.now().year}-01-01"
            return byCurrency(
                trades.filter { it.kind == TradeKinds.DIVIDEND && it.occurredOn >= yearStart }.map { currencyOf(it.symbol) to it.amount },
            )
        }
}

/** What the trade form hands back. */
data class TradeSubmission(
    val accountId: String,
    val symbol: String,
    val kind: String,
    val occurredOn: String,
    /** Ten-thousandths of a share or a unit. */
    val units: Long,
    val price: Long,
    /** A fund's cash before fees, as the trust bank said it; null for a stock or an ETF. */
    val gross: Long?,
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
        ledgerRepository.settings,
    ) { trades, securities, accounts, settings ->
        val bySymbol = securities.associateBy { it.symbol }
        val byId = accounts.associateBy { it.id }
        val brokers = trades.map { it.accountId }.distinct()
        InvestmentsUiState(
            trades = trades,
            securities = securities,
            holdings = holdings(trades, bySymbol, { byId[it]?.name }, { byId[it]?.currency }),
            accounts = accounts.filter { !it.archived },
            brokerCash = brokers.associateWith { byId[it]?.balance ?: 0L },
            displayCurrency = settings?.displayCurrency ?: "PHP",
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

    /** The US-listed and LSE ETFs Yahoo Finance finds for [query]; empty when it cannot be asked (offline). */
    suspend fun searchEtfs(query: String): List<EtfMatch> = runCatching { repository.searchEtfs(query) }.getOrDefault(emptyList())

    /** Adds an ETF a search found, so a trade can be made of it; null when it cannot be had. */
    suspend fun addSecurity(symbol: String): Security? = runCatching { repository.addSecurity(symbol) }.getOrNull()

    suspend fun createTrade(submission: TradeSubmission) = with(submission) {
        repository.createTrade(accountId, symbol, kind, occurredOn, units, price, gross, fees)
    }

    suspend fun updateTrade(id: String, submission: TradeSubmission) = with(submission) {
        repository.updateTrade(id, accountId, symbol, kind, occurredOn, units, price, gross, fees)
    }

    suspend fun deleteTrade(id: String) = repository.deleteTrade(id)

    /** Whether the history still stands without this trade: a later sell may need its shares or units. */
    fun canDelete(id: String): Boolean = positions(uiState.value.trades.filter { it.id != id }) != null
}
