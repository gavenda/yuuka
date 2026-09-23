<script setup lang="ts">
import FilterSheet from '@/components/FilterSheet.vue';
import EmptyState from '@/components/EmptyState.vue';
import FabButton from '@/components/FabButton.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import MoneyText from '@/components/MoneyText.vue';
import MonthSwitcher from '@/components/MonthSwitcher.vue';
import TransactionForm from '@/components/TransactionForm.vue';
import { api, ApiError } from '@/lib/api';
import { formatLongDate, formatTime } from '@/lib/dates';
import { displayMoney } from '@/lib/privacy';
import { showSnackbar } from '@/lib/snackbar';
import { EDIT_NOTE } from '@/lib/icons';
import { dailyAccrued, describeRow, mergeTransferRows, type TransactionRow } from '@/lib/transactionRows';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { useTransactionStore } from '@/stores/transactions';
import type { Transaction } from '@/types';
import { computed, onMounted, ref, watch } from 'vue';

const store = useTransactionStore();
const ledger = useLedgerStore();
const budget = useBudgetStore();

const dialogOpen = ref(false);
const editing = ref<Transaction | null>(null);
const editingTransferToAccountId = ref<string | null>(null);
const formRef = ref<InstanceType<typeof TransactionForm> | null>(null);
const search = ref('');
const accountFilter = ref<string[]>([]);
const categoryFilter = ref<string[]>([]);
const tagFilter = ref<string[]>([]);
const openFilter = ref<'accounts' | 'categories' | 'tags' | null>(null);
const month = ref(budget.month);

const currency = computed(() => ledger.displayCurrency);

/** The running balance is the account's own money, shown in what it actually holds. */
function accountCurrency(accountId: string): string {
	return ledger.accountsById.get(accountId)?.currency ?? currency.value;
}

const groupedRows = computed<[string, TransactionRow[]][]>(() => store.byDate.map(([date, group]) => [date, mergeTransferRows(group)]));

/** Each day's cards, with the day's net change, worked out once rather than per binding in the template. */
const days = computed(() =>
	groupedRows.value.map(([date, rows]) => ({
		date,
		total: dailyAccrued(rows),
		items: rows.map((row) => ({ row, card: describeRow(row) })),
	})),
);

const accountOptions = computed(() => ledger.accounts.map((account) => ({ id: account.id, label: account.name })));
const categoryOptions = computed(() => [
	{ id: 'none', label: 'Uncategorized' },
	...ledger.categories.map((category) => ({ id: category.id, label: category.name })),
]);
const tagOptions = computed(() => ledger.tags.map((tag) => ({ id: tag.id, label: tag.name })));

/** In the order they were picked; anything no longer in the list is not counted. */
const accountLabel = computed(() => {
	const labels = accountFilter.value.flatMap((id) => accountOptions.value.find((option) => option.id === id)?.label ?? []);
	if (!labels.length) return 'All accounts';
	return labels.length === 1 ? labels[0] : `${labels[0]} & more`;
});

const filters = computed(() => ({
	month: month.value,
	accountId: accountFilter.value.join(',') || undefined,
	categoryId: categoryFilter.value.join(',') || undefined,
	tagId: tagFilter.value.join(',') || undefined,
	search: search.value.trim() || undefined,
}));

let searchTimer: ReturnType<typeof setTimeout> | undefined;

// Debounced so typing in the search box does not fire a request per keystroke.
watch(filters, (next) => {
	clearTimeout(searchTimer);
	searchTimer = setTimeout(() => void store.load(next), 250);
});

// Together, so the list's saved copy is not held back by the ledger's round trip.
onMounted(() => Promise.all([ledger.load(), store.load(filters.value)]));

function openCreate(): void {
	editing.value = null;
	editingTransferToAccountId.value = null;
	dialogOpen.value = true;
}

/** A transfer edit seeds the form with the outflow leg plus the inflow's account, so both sides stay linked. */
function openEdit(transaction: Transaction, transferToAccountId: string | null = null): void {
	editing.value = transaction;
	editingTransferToAccountId.value = transferToAccountId;
	dialogOpen.value = true;
}

/** Says "₱X saved to <account>" after a purchase triggers a Save the Change round-up. */
function announceRoundUp(roundUp: Transaction): void {
	const destinationCurrency = ledger.accountsById.get(roundUp.accountId)?.currency ?? currency.value;
	showSnackbar(`${displayMoney(roundUp.amount, destinationCurrency)} saved to ${roundUp.accountName ?? 'your account'}`);
}

async function save(payload: Record<string, unknown> & { mode: string }): Promise<void> {
	const { mode, ...body } = payload;

	try {
		const wasEditing = editing.value !== null;
		let roundUp: Transaction | null = null;

		if (mode === 'transfer' && editing.value?.transferId) {
			await api.updateTransfer(editing.value.transferId, body);
		} else if (mode === 'transfer') {
			await api.createTransfer(body);
		} else if (editing.value) {
			await api.updateTransaction(editing.value.id, body);
		} else {
			roundUp = (await api.createTransaction(body)).roundUp ?? null;
		}

		dialogOpen.value = false;
		showSnackbar(wasEditing ? 'Changes saved' : mode === 'transfer' ? 'Transfer added' : 'Transaction added');
		if (roundUp) announceRoundUp(roundUp);
		await Promise.all([store.refresh(), ledger.refreshAccounts(), budget.refresh()]);
	} catch (caught) {
		formRef.value?.fail(caught instanceof ApiError ? caught.message : 'Could not save the transaction.');
	}
}

async function remove(): Promise<void> {
	if (!editing.value) return;

	const label = editing.value.transferId ? 'Delete this transfer? Both sides will be removed.' : 'Delete this transaction?';
	if (!confirm(label)) return;

	await api.deleteTransaction(editing.value.id);
	dialogOpen.value = false;
	showSnackbar(editing.value.transferId ? 'Transfer deleted' : 'Transaction deleted');
	await Promise.all([store.refresh(), ledger.refreshAccounts(), budget.refresh()]);
}
</script>

<template>
	<div class="space-y-5">
		<header>
			<MonthSwitcher v-model="month" />
		</header>

		<!-- One row of filters, each opening a sheet of chips, then the search. The account button is only as wide as its label, a spacer pushes the tag and category icons (with a badge) to the end. -->
		<div class="flex items-center">
			<button
				type="button"
				class="state-layer focus-ring type-label-large flex min-h-10 min-w-0 cursor-pointer items-center gap-2 rounded-full px-3"
				:class="accountFilter.length ? 'text-primary' : 'text-on-surface'"
				aria-haspopup="dialog"
				@click="openFilter = 'accounts'"
			>
				<svg viewBox="0 0 24 24" class="size-5 shrink-0" fill="currentColor" aria-hidden="true">
					<path
						d="M21 7.28V5c0-1.1-.9-2-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14c1.1 0 2-.9 2-2v-2.28A2 2 0 0 0 22 15V9a2 2 0 0 0-1-1.72zM20 9v6h-7V9h7zM5 19V5h14v2h-6a2 2 0 0 0-2 2v6c0 1.1.9 2 2 2h6v2H5z"
					/>
					<circle cx="16" cy="12" r="1.5" />
				</svg>
				<span class="truncate">{{ accountLabel }}</span>
				<svg viewBox="0 0 24 24" class="size-5 shrink-0" fill="currentColor" aria-hidden="true"><path d="M7 10l5 5 5-5z" /></svg>
			</button>

			<div class="flex-1" aria-hidden="true" />

			<button
				type="button"
				class="btn-icon relative"
				:class="{ '!text-primary': tagFilter.length }"
				aria-label="Filter by tag"
				aria-haspopup="dialog"
				@click="openFilter = 'tags'"
			>
				<svg
					viewBox="0 0 24 24"
					class="size-6"
					fill="none"
					stroke="currentColor"
					stroke-width="2"
					stroke-linejoin="round"
					aria-hidden="true"
				>
					<path d="M20.6 13.4l-7.2 7.2a2 2 0 0 1-2.8 0L3 13V3h10l7.6 7.6a2 2 0 0 1 0 2.8z" />
					<circle cx="7.5" cy="7.5" r="1" fill="currentColor" />
				</svg>
				<span v-if="tagFilter.length" class="badge">{{ tagFilter.length }}</span>
			</button>

			<button
				type="button"
				class="btn-icon relative"
				:class="{ '!text-primary': categoryFilter.length }"
				aria-label="Filter by category"
				aria-haspopup="dialog"
				@click="openFilter = 'categories'"
			>
				<svg
					viewBox="0 0 24 24"
					class="size-6"
					fill="none"
					stroke="currentColor"
					stroke-width="2"
					stroke-linecap="round"
					aria-hidden="true"
				>
					<path d="M4 6h16M7 12h10M10 18h4" />
				</svg>
				<span v-if="categoryFilter.length" class="badge">{{ categoryFilter.length }}</span>
			</button>
		</div>

		<div class="relative">
			<svg
				viewBox="0 0 24 24"
				class="pointer-events-none absolute top-1/2 left-4 size-6 -translate-y-1/2 text-on-surface-variant"
				fill="none"
				stroke="currentColor"
				stroke-width="2"
				stroke-linecap="round"
				aria-hidden="true"
			>
				<circle cx="11" cy="11" r="7" />
				<path d="M20 20l-3.5-3.5" />
			</svg>
			<input
				v-model="search"
				class="input rounded-full border-transparent bg-surface-container-low pl-12"
				type="search"
				aria-label="Search"
				placeholder="Search payee, notes or tag"
			/>
		</div>

		<FilterSheet
			v-model="accountFilter"
			:open="openFilter === 'accounts'"
			title="Filter by account"
			:options="accountOptions"
			empty-text="No accounts yet"
			@close="openFilter = null"
		/>
		<FilterSheet
			v-model="categoryFilter"
			:open="openFilter === 'categories'"
			title="Filter by category"
			:options="categoryOptions"
			empty-text="No categories yet"
			@close="openFilter = null"
		/>
		<FilterSheet
			v-model="tagFilter"
			:open="openFilter === 'tags'"
			title="Filter by tag"
			:options="tagOptions"
			empty-text="No tags yet"
			@close="openFilter = null"
		/>

		<p v-if="store.error" class="banner-error" role="alert">
			{{ store.error }}
		</p>

		<div v-else-if="store.loading && !store.transactions.length" class="space-y-4" aria-hidden="true">
			<div v-for="group in 3" :key="group" class="animate-pulse space-y-2">
				<div class="h-3 w-28 rounded bg-surface-container-highest" />
				<div v-for="row in 3" :key="row" class="card flex items-start gap-3 p-3">
					<div class="min-w-0 flex-1 space-y-2">
						<div class="h-3.5 w-1/3 rounded bg-surface-container-highest" />
						<div class="h-3 w-1/4 rounded bg-surface-container-high" />
					</div>
					<div class="w-16 shrink-0 space-y-2">
						<div class="ml-auto h-3.5 w-14 rounded bg-surface-container-highest" />
						<div class="ml-auto h-3 w-10 rounded bg-surface-container-high" />
					</div>
				</div>
			</div>
		</div>

		<EmptyState
			v-else-if="!store.transactions.length"
			title="No transactions here"
			description="Nothing matches these filters yet. Add one, or widen the search."
		>
			<button type="button" class="btn-primary" @click="openCreate">Add a transaction</button>
		</EmptyState>

		<!-- One card per transaction under a header for its day, as on Android. -->
		<div v-else class="space-y-4">
			<section v-for="day in days" :key="day.date">
				<div class="flex items-center justify-between gap-3 px-1 pb-2">
					<h2 class="type-label-medium text-on-surface-variant">{{ formatLongDate(day.date) }}</h2>
					<MoneyText :amount="day.total" :currency="currency" signed explicit class="type-label-medium" />
				</div>

				<ul class="space-y-2">
					<li v-for="{ row, card } in day.items" :key="card.key" class="card overflow-hidden">
						<!-- The whole card opens the edit form. Spans, not blocks, because it is a button. -->
						<button
							type="button"
							class="state-layer focus-ring block w-full cursor-pointer text-left"
							@click="row.kind === 'transfer' ? openEdit(row.leg, row.toAccountId) : openEdit(row.transaction)"
						>
							<span class="flex items-start gap-3 p-3">
								<span class="block min-w-0 flex-1 space-y-1">
									<span class="block truncate text-sm text-on-surface">{{ card.title }}</span>
									<span class="block truncate text-xs text-on-surface-variant">{{ card.subtitle }}</span>
									<span
										v-if="card.automated"
										class="type-label-small block text-on-surface-variant"
										title="Posted automatically by a subscription"
										>Subscription</span
									>
									<span v-if="formatTime(card.occurredOn)" class="block text-xs text-on-surface-variant">
										{{ formatTime(card.occurredOn) }}
									</span>
								</span>

								<span class="flex shrink-0 flex-col items-end space-y-1">
									<MoneyText
										:amount="card.amount"
										:currency="currency"
										:signed="card.tone === 'signed'"
										:transfer="card.tone === 'transfer'"
										:explicit="card.tone === 'signed'"
										class="text-sm"
									/>
									<span class="tabular text-xs text-on-surface-variant">
										{{ displayMoney(card.balance, accountCurrency(card.balanceAccountId)) }}
									</span>
									<span v-if="card.category" class="flex items-center gap-1.5 text-xs text-on-surface-variant">
										{{ card.category?.name }}
										<span
											v-if="card.category?.color"
											class="size-2 rounded-full"
											:style="{ backgroundColor: card.category?.color ?? undefined }"
											aria-hidden="true"
										/>
									</span>
								</span>
							</span>

							<!-- Notes on the left, tags as chips at the right end of the same row, centred on each other. The icon stays with the note's first line. -->
							<span v-if="card.notes || card.tags.length" class="flex items-center gap-2 border-t border-outline-variant p-3">
								<span class="flex min-w-0 flex-1 items-start gap-2">
									<template v-if="card.notes">
										<svg viewBox="0 0 24 24" class="size-4 shrink-0 text-on-surface-variant" fill="currentColor" aria-hidden="true">
											<path :d="EDIT_NOTE" />
										</svg>
										<span class="min-w-0 flex-1 text-xs text-on-surface-variant">{{ card.notes }}</span>
									</template>
								</span>

								<span v-if="card.tags.length" class="flex max-w-[60%] shrink-0 flex-wrap justify-end gap-1.5">
									<span v-for="tag in card.tags" :key="tag.id" class="chip">
										<span class="size-2 shrink-0 rounded-full" :style="{ backgroundColor: tag.color }" aria-hidden="true" />
										<span class="truncate">{{ tag.name }}</span>
									</span>
								</span>
							</span>
						</button>
					</li>
				</ul>
			</section>

			<div v-if="store.hasMore" class="text-center">
				<button type="button" class="btn-outlined" :disabled="store.loading" @click="store.loadMore()">
					{{ store.loading ? 'Loading…' : `Load more (${store.transactions.length} of ${store.total})` }}
				</button>
			</div>
		</div>

		<ModalDialog
			:open="dialogOpen"
			:title="editing?.transferId ? 'Edit transfer' : editing ? 'Edit transaction' : 'New transaction'"
			@close="dialogOpen = false"
		>
			<TransactionForm
				ref="formRef"
				:transaction="editing"
				:transfer-to-account-id="editingTransferToAccountId"
				@submit="save"
				@cancel="dialogOpen = false"
				@delete="remove"
			/>
		</ModalDialog>

		<FabButton label="Add transaction" @click="openCreate" />
	</div>
</template>
