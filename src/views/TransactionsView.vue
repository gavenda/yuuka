<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import EmptyState from '@/components/EmptyState.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import MoneyText from '@/components/MoneyText.vue';
import MonthSwitcher from '@/components/MonthSwitcher.vue';
import TransactionForm from '@/components/TransactionForm.vue';
import { api, ApiError } from '@/lib/api';
import { formatLongDate } from '@/lib/dates';
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
const formRef = ref<InstanceType<typeof TransactionForm> | null>(null);
const search = ref('');
const accountFilter = ref('');
const categoryFilter = ref('');
const month = ref(budget.month);

const currency = computed(() => ledger.displayCurrency);

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
	dialogOpen.value = true;
}

function openEdit(transaction: Transaction): void {
	// A transfer is two linked rows; editing one leg would silently desync the
	// other, so transfers are delete-and-recreate rather than editable.
	if (transaction.transferId) return;
	editing.value = transaction;
	dialogOpen.value = true;
}

async function save(payload: Record<string, unknown> & { mode: string }): Promise<void> {
	const { mode, ...body } = payload;

	try {
		if (mode === 'transfer') await api.createTransfer(body);
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

		<EmptyState
			v-else-if="!store.loading && !store.transactions.length"
			title="No transactions here"
			description="Nothing matches these filters yet. Add one, or widen the search."
		>
			<button type="button" class="btn-primary" @click="openCreate">Add a transaction</button>
		</EmptyState>

		<div v-else class="card divide-y divide-slate-100 dark:divide-slate-800/60">
			<section v-for="[date, group] in store.byDate" :key="date">
				<h2
					class="bg-slate-50 px-4 py-2 text-xs font-medium tracking-wide text-slate-500 uppercase dark:bg-slate-950/40 dark:text-slate-400"
				>
					{{ formatLongDate(date) }}
				</h2>

				<ul class="divide-y divide-slate-100 dark:divide-slate-800/60">
					<li
						v-for="transaction in group"
						:key="transaction.id"
						class="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-slate-50 dark:hover:bg-slate-800/40"
					>
						<span
							class="h-2.5 w-2.5 shrink-0 rounded-full"
							:style="{ backgroundColor: transaction.categoryColor ?? '#898781' }"
							aria-hidden="true"
						/>

						<button type="button" class="min-w-0 flex-1 text-left" @click="openEdit(transaction)">
							<p class="truncate text-sm font-medium text-slate-900 dark:text-slate-100">
								{{ transaction.payee || transaction.categoryName || 'Uncategorised' }}
							</p>
							<p class="truncate text-xs text-slate-500 dark:text-slate-400">
								{{ transaction.accountName }}
								<template v-if="transaction.categoryName"> · {{ transaction.categoryName }}</template>
								<template v-if="transaction.transferId"> · Transfer</template>
								<template v-if="transaction.notes"> · {{ transaction.notes }}</template>
							</p>
						</button>

						<MoneyText :amount="transaction.amount" :currency="currency" signed explicit class="shrink-0 text-sm font-medium" />

						<ActionIcon
							icon="delete"
							:label="`Delete ${transaction.payee || 'transaction'}`"
							danger
							class="row-actions"
							@click="remove(transaction)"
						/>
					</li>
				</ul>
			</section>

			<div v-if="store.hasMore" class="p-4 text-center">
				<button type="button" class="btn-secondary" :disabled="store.loading" @click="store.loadMore()">
					{{ store.loading ? 'Loading…' : `Load more (${store.transactions.length} of ${store.total})` }}
				</button>
			</div>
		</div>

		<ModalDialog :open="dialogOpen" :title="editing ? 'Edit transaction' : 'New transaction'" @close="dialogOpen = false">
			<TransactionForm ref="formRef" :transaction="editing" @submit="save" @cancel="dialogOpen = false" />
		</ModalDialog>
	</div>
</template>
