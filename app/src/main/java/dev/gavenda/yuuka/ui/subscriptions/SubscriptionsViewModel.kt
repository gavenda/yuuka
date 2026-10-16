package dev.gavenda.yuuka.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.Payee
import dev.gavenda.yuuka.data.model.Subscription
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY
import dev.gavenda.yuuka.repository.LedgerRepository
import dev.gavenda.yuuka.repository.PayeeRepository
import dev.gavenda.yuuka.repository.SubscriptionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SubscriptionsUiState(
    val subscriptions: List<Subscription> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val payees: List<Payee> = emptyList(),
    val defaultAccountId: String? = null,
    /** The currency the total is shown in, like every other aggregate figure. */
    val displayCurrency: String = DEFAULT_CURRENCY,
)

/** What the form hands back. [startOn] is null when an edit left the date alone, so the schedule is not restarted. */
data class SubscriptionSubmission(
    val accountId: String,
    val categoryId: String?,
    val amount: Long,
    val payee: String,
    val notes: String,
    val startOn: String?,
)

/** Mirrors the web app's `subscriptions` store plus `SubscriptionsView.vue`. */
class SubscriptionsViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    ledgerRepository: LedgerRepository,
    payeeRepository: PayeeRepository,
) : ViewModel() {
    val uiState: StateFlow<SubscriptionsUiState> = combine(
        subscriptionRepository.subscriptions,
        ledgerRepository.accounts,
        ledgerRepository.categories,
        payeeRepository.payees,
        ledgerRepository.settings,
    ) { subscriptions, accounts, categories, payees, settings ->
        SubscriptionsUiState(subscriptions, accounts, categories, payees, settings?.defaultAccountId, settings?.displayCurrency ?: DEFAULT_CURRENCY)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsUiState())

    init {
        // Whatever the last sync cached is already on screen; this just brings it up to date.
        viewModelScope.launch { runCatching { subscriptionRepository.refresh() } }
    }

    /** A new subscription when [id] is null, which always has a start date. */
    suspend fun save(id: String?, submission: SubscriptionSubmission) {
        with(submission) {
            if (id == null) {
                subscriptionRepository.create(accountId, categoryId, amount, payee, notes, requireNotNull(startOn))
            } else {
                subscriptionRepository.update(id, accountId, categoryId, amount, payee, notes, startOn)
            }
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = subscriptionRepository.setEnabled(id, enabled)

    suspend fun delete(id: String) = subscriptionRepository.delete(id)
}
