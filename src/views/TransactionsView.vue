<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import EmptyState from '@/components/EmptyState.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import MoneyText from '@/components/MoneyText.vue';
import MonthSwitcher from '@/components/MonthSwitcher.vue';
import TransactionForm from '@/components/TransactionForm.vue';
import { api, ApiError } from '@/lib/api';
import { formatLongDate, formatTime } from '@/lib/dates';
import { displayMoney } from '@/lib/privacy';
import { mergeTransferRows, type TransactionRow } from '@/lib/transactionRows';
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
const accountFilter = ref('');
const categoryFilter = ref('');
const month = ref(budget.month);

const currency = computed(() => ledger.displayCurrency);

/** The running balance is the account's own money, shown in what it actually holds. */
function accountCurrency(accountId: string): string {
	return ledger.accountsById.get(accountId)?.currency ?? currency.value;
}

const groupedRows = computed<[string, TransactionRow[]][]>(() => store.byDate.map(([date, group]) => [date, mergeTransferRows(group)]));

const filters = computed(() => ({
	month: month.value,
	accountId: accountFilter.value || undefined,
	categoryId: categoryFilter.value || undefined,
	search: search.value.trim() || undefined,
}));

let searchTimer: ReturnType<typeof setTimeout> | undefined;

// Debounced so typing in the search box does not fire a request per keystroke.
watch(filters, (next) => {
	clearTimeout(searchTimer);
	searchTimer = setTimeout(() => void store.load(next), 250);
});

onMounted(async () => {
	await ledger.load();
	await store.load(filters.value);
});

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

async function save(payload: Record<string, unknown> & { mode: string }): Promise<void> {
	const { mode, ...body } = payload;

	try {
		if (mode === 'transfer' && editing.value?.transferId) await api.updateTransfer(editing.value.transferId, body);
		else if (mode === 'transfer') await api.createTransfer(body);
		else if (editing.value) await api.updateTransaction(editing.value.id, body);
		else await api.createTransaction(body);

		dialogOpen.value = false;
		await Promise.all([store.refresh(), ledger.refreshAccounts(), budget.refresh()]);
	} catch (caught) {
		formRef.value?.fail(caught instanceof ApiError ? caught.message : 'Could not save the transaction.');
	}
}

async function remove(transaction: Transaction): Promise<void> {
	const label = transaction.transferId ? 'Delete this transfer? Both sides will be removed.' : 'Delete this transaction?';
	if (!confirm(label)) return;

	await api.deleteTransaction(transaction.id);
	await Promise.all([store.refresh(), ledger.refreshAccounts(), budget.refresh()]);
}
</script>

<template>
	<div class="space-y-5">
		<header class="flex flex-wrap items-center justify-between gap-3">
			<h1 class="text-xl font-semibold tracking-tight text-slate-900 dark:text-white">Transactions</h1>
			<div class="flex items-center gap-2">
				<MonthSwitcher v-model="month" />
				<button type="button" class="btn-primary" @click="openCreate">Add</button>
			</div>
		</header>

		<!-- Filters sit in one row above the list. -->
		<div class="grid gap-3 sm:grid-cols-3">
			<input v-model="search" class="input" type="search" placeholder="Search payee or notes" aria-label="Search transactions" />

			<select v-model="accountFilter" class="input" aria-label="Filter by account">
				<option value="">All accounts</option>
				<option v-for="account in ledger.accounts" :key="account.id" :value="account.id">{{ account.name }}</option>
			</select>

			<select v-model="categoryFilter" class="input" aria-label="Filter by category">
				<option value="">All categories</option>
				<option value="none">Uncategorised</option>
				<option v-for="category in ledger.categories" :key="category.id" :value="category.id">{{ category.name }}</option>
			</select>
		</div>

		<p v-if="store.error" class="rounded-lg bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:bg-rose-500/10 dark:text-rose-400" role="alert">
			{{ store.error }}
		</p>

		<div
			v-else-if="store.loading && !store.transactions.length"
			class="card divide-y divide-slate-100 dark:divide-slate-800/60"
			aria-hidden="true"
		>
			<div v-for="group in 3" :key="group" class="animate-pulse">
				<div class="h-8 bg-slate-50 px-4 py-2 dark:bg-slate-950/40">
					<div class="h-3 w-24 rounded bg-slate-200 dark:bg-slate-800" />
				</div>
				<div v-for="row in 3" :key="row" class="flex items-center gap-3 px-4 py-3">
					<span class="h-2.5 w-2.5 shrink-0 rounded-full bg-slate-200 dark:bg-slate-800" />
					<div class="min-w-0 flex-1 space-y-2">
						<div class="h-3.5 w-1/3 rounded bg-slate-200 dark:bg-slate-800" />
						<div class="h-3 w-1/2 rounded bg-slate-100 dark:bg-slate-800/60" />
					</div>
					<div class="h-3.5 w-14 shrink-0 rounded bg-slate-200 dark:bg-slate-800" />
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

		<div v-else class="card divide-y divide-slate-100 dark:divide-slate-800/60">
			<section v-for="[date, group] in groupedRows" :key="date">
				<h2
					class="bg-slate-50 px-4 py-2 text-xs font-medium tracking-wide text-slate-500 uppercase dark:bg-slate-950/40 dark:text-slate-400"
				>
					{{ formatLongDate(date) }}
				</h2>

				<ul class="divide-y divide-slate-100 dark:divide-slate-800/60">
					<li
						v-for="row in group"
						:key="row.kind === 'transfer' ? row.id : row.transaction.id"
						class="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-slate-50 dark:hover:bg-slate-800/40"
					>
						<template v-if="row.kind === 'transfer'">
							<span
								class="h-2.5 w-2.5 shrink-0 rounded-full"
								:style="{ backgroundColor: row.categoryColor ?? '#898781' }"
								aria-hidden="true"
							/>

							<button type="button" class="min-w-0 flex-1 cursor-pointer text-left" @click="openEdit(row.leg, row.toAccountId)">
								<p class="flex min-w-0 items-center gap-1.5 text-sm font-medium text-slate-900 dark:text-slate-100">
									<span class="min-w-0 truncate">{{ row.payee || row.categoryName || 'Transfer' }}</span>
									<span
										v-if="row.payee && row.categoryName"
										class="shrink-0 rounded-full px-1.5 py-0.5 text-[10px] leading-none font-medium"
										:style="{
											backgroundColor: `color-mix(in srgb, ${row.categoryColor ?? '#898781'} 18%, transparent)`,
											color: row.categoryColor ?? '#898781',
										}"
										>{{ row.categoryName }}</span
									>
								</p>
								<p class="truncate text-xs text-slate-500 dark:text-slate-400">
									{{ row.fromAccountName }} → {{ row.toAccountName
									}}{{ formatTime(row.leg.occurredOn) ? ` · ${formatTime(row.leg.occurredOn)}` : '' }}
								</p>
							</button>

							<span v-if="row.notes" class="hidden max-w-40 shrink-0 items-center gap-1 text-xs text-slate-500 sm:flex dark:text-slate-400">
								<svg
									viewBox="0 0 20 20"
									class="h-3.5 w-3.5 shrink-0"
									fill="none"
									stroke="currentColor"
									stroke-width="1.5"
									aria-hidden="true"
								>
									<rect x="4" y="3.5" width="12" height="13" rx="1.5" />
									<path d="M6.75 8h6.5M6.75 11h6.5M6.75 14h3.5" stroke-linecap="round" />
								</svg>
								<span class="truncate">{{ row.notes }}</span>
							</span>

							<div class="flex shrink-0 flex-col items-end gap-0.5">
								<MoneyText :amount="row.amount" :currency="currency" transfer class="text-sm font-medium" />
								<span class="tabular text-xs text-slate-400 dark:text-slate-500">
									{{ displayMoney(row.leg.runningBalance, accountCurrency(row.leg.accountId)) }}
								</span>
							</div>

							<ActionIcon icon="delete" :label="`Delete ${row.payee || 'transfer'}`" danger class="row-actions" @click="remove(row.leg)" />
						</template>

						<template v-else>
							<span
								class="h-2.5 w-2.5 shrink-0 rounded-full"
								:style="{ backgroundColor: row.transaction.categoryColor ?? '#898781' }"
								aria-hidden="true"
							/>

							<button type="button" class="min-w-0 flex-1 cursor-pointer text-left" @click="openEdit(row.transaction)">
								<p class="flex min-w-0 items-center gap-1.5 text-sm font-medium text-slate-900 dark:text-slate-100">
									<span class="min-w-0 truncate">{{ row.transaction.payee || row.transaction.categoryName || 'Uncategorised' }}</span>
									<span
										v-if="row.transaction.payee && row.transaction.categoryName"
										class="shrink-0 rounded-full px-1.5 py-0.5 text-[10px] leading-none font-medium"
										:style="{
											backgroundColor: `color-mix(in srgb, ${row.transaction.categoryColor ?? '#898781'} 18%, transparent)`,
											color: row.transaction.categoryColor ?? '#898781',
										}"
										>{{ row.transaction.categoryName }}</span
									>
								</p>
								<p class="truncate text-xs text-slate-500 dark:text-slate-400">
									{{ row.transaction.accountName
									}}{{ formatTime(row.transaction.occurredOn) ? ` · ${formatTime(row.transaction.occurredOn)}` : '' }}
								</p>
							</button>

							<span
								v-if="row.transaction.notes"
								class="hidden max-w-40 shrink-0 items-center gap-1 text-xs text-slate-500 sm:flex dark:text-slate-400"
							>
								<svg
									viewBox="0 0 20 20"
									class="h-3.5 w-3.5 shrink-0"
									fill="none"
									stroke="currentColor"
									stroke-width="1.5"
									aria-hidden="true"
								>
									<rect x="4" y="3.5" width="12" height="13" rx="1.5" />
									<path d="M6.75 8h6.5M6.75 11h6.5M6.75 14h3.5" stroke-linecap="round" />
								</svg>
								<span class="truncate">{{ row.transaction.notes }}</span>
							</span>

							<div class="flex shrink-0 flex-col items-end gap-0.5">
								<MoneyText :amount="row.transaction.amount" :currency="currency" signed explicit class="text-sm font-medium" />
								<span class="tabular text-xs text-slate-400 dark:text-slate-500">
									{{ displayMoney(row.transaction.runningBalance, accountCurrency(row.transaction.accountId)) }}
								</span>
							</div>

							<ActionIcon
								icon="delete"
								:label="`Delete ${row.transaction.payee || 'transaction'}`"
								danger
								class="row-actions"
								@click="remove(row.transaction)"
							/>
						</template>
					</li>
				</ul>
			</section>

			<div v-if="store.hasMore" class="p-4 text-center">
				<button type="button" class="btn-secondary" :disabled="store.loading" @click="store.loadMore()">
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
			/>
		</ModalDialog>
	</div>
</template>
