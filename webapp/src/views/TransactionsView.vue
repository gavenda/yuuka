<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import AlertDialog from '@/components/AlertDialog.vue';
import AppIcon from '@/components/AppIcon.vue';
import CatMark from '@/components/CatMark.vue';
import EmptyState from '@/components/EmptyState.vue';
import FabButton from '@/components/FabButton.vue';
import FilterChipRow from '@/components/FilterChipRow.vue';
import MoneyText from '@/components/MoneyText.vue';
import MonthSwitcher from '@/components/MonthSwitcher.vue';
import SwipeReveal from '@/components/SwipeReveal.vue';
import TopBar from '@/components/TopBar.vue';
import TransactionForm from '@/components/TransactionForm.vue';
import { api, ApiError } from '@/lib/api';
import { formatLongDate, formatTime } from '@/lib/dates';
import { displayMoney } from '@/lib/privacy';
import { showSnackbar } from '@/lib/snackbar';
import { useHarmonised } from '@/lib/harmonise';
import {
	ACCOUNT_BALANCE_WALLET,
	ARROW_DROP_DOWN,
	ARROW_RIGHT_ALT,
	CLOSE,
	DELETE,
	EDIT_NOTE,
	FILTER_LIST_OUTLINED,
	SEARCH,
	SELL_OUTLINED,
} from '@/lib/icons';
import { dailyAccrued, describeRow, mergeTransferRows, type TransactionRow } from '@/lib/transactionRows';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { useTransactionStore } from '@/stores/transactions';
import type { Transaction } from '@/types';
import { computed, onMounted, ref, watch } from 'vue';
import { t } from '@/i18n';

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
type FilterKind = 'accounts' | 'categories' | 'tags';
/** Each filter button opens its options as a row of chips under the bar; pressing it again puts the row away. */
const openFilter = ref<FilterKind | null>(null);
/** What was last open, which stays drawn while the row slides shut. */
const shownFilter = ref<FilterKind>('accounts');
const month = ref(budget.month);
/** The row a delete has been asked for, while the question is still open. */
const pendingDelete = ref<Transaction | null>(null);
const harmonised = useHarmonised();

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
// Top-level categories only: a parent's filter already takes in what is filed under its children.
const categoryOptions = computed(() => [
	{ id: 'none', label: t('common.uncategorized') },
	...ledger.categories.filter((category) => category.parentId === null).map((category) => ({ id: category.id, label: category.name })),
]);
const tagOptions = computed(() => ledger.tags.map((tag) => ({ id: tag.id, label: tag.name })));

/** In the order they were picked; anything no longer in the list is not counted. */
const accountLabel = computed(() => {
	const labels = accountFilter.value.flatMap((id) => accountOptions.value.find((option) => option.id === id)?.label ?? []);
	if (!labels.length) return t('transactions.allAccounts');
	return labels.length === 1 ? labels[0] : t('transactions.andMore', { name: labels[0] });
});

const filterRows = {
	accounts: {
		label: t('transactions.filterByAccount'),
		emptyText: t('accounts.emptyShort'),
		options: accountOptions,
		selected: accountFilter,
	},
	categories: {
		label: t('transactions.filterByCategory'),
		emptyText: t('categories.emptyShort'),
		options: categoryOptions,
		selected: categoryFilter,
	},
	tags: { label: t('transactions.filterByTag'), emptyText: t('tags.emptyTitle'), options: tagOptions, selected: tagFilter },
};
const shownRow = computed(() => filterRows[shownFilter.value]);
const shownSelection = computed({
	get: () => shownRow.value.selected.value,
	set: (next) => (shownRow.value.selected.value = next),
});

function toggleFilter(kind: FilterKind): void {
	openFilter.value = openFilter.value === kind ? null : kind;
	if (openFilter.value) shownFilter.value = openFilter.value;
}

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
	showSnackbar(
		t('transactions.roundUpSaved', {
			amount: displayMoney(roundUp.amount, destinationCurrency),
			account: roundUp.accountName ?? t('transactions.roundUpFallback'),
		}),
	);
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
		showSnackbar(
			wasEditing
				? t('transactions.changesSaved')
				: mode === 'transfer'
					? t('transactions.transferAdded')
					: t('transactions.transactionAdded'),
		);
		if (roundUp) announceRoundUp(roundUp);
		await Promise.all([store.refresh(), ledger.refreshAccounts(), budget.refresh()]);
	} catch (caught) {
		formRef.value?.fail(caught instanceof ApiError ? caught.message : t('common.couldNotSave'));
	}
}

async function remove(): Promise<void> {
	const target = pendingDelete.value;
	if (!target) return;

	pendingDelete.value = null;
	await api.deleteTransaction(target.id);
	showSnackbar(target.transferId ? t('transactions.transferDeleted') : t('transactions.transactionDeleted'));
	await Promise.all([store.refresh(), ledger.refreshAccounts(), budget.refresh()]);
}
</script>

<template>
	<div class="pb-24">
		<!-- What stays put while the list scrolls: the month, the search, and one row of filters, each opening
		     a row of chips under it. The account button is only as wide as its label; the tag and category icons
		     (each with a count of what is on) sit at the end. -->
		<TopBar>
			<MonthSwitcher v-model="month" />

			<label class="search-field mt-8">
				<AppIcon :icon="SEARCH" />
				<input v-model="search" type="search" :aria-label="t('transactions.search')" :placeholder="t('transactions.searchPlaceholder')" />
				<button v-if="search" type="button" class="btn-icon -mr-2" :aria-label="t('common.clear')" @click="search = ''">
					<AppIcon :icon="CLOSE" />
				</button>
			</label>

			<div class="mt-4 -mb-2 flex h-12 items-center">
				<div class="flex min-w-0 flex-1">
					<button
						type="button"
						class="btn-text min-w-0 gap-0"
						:aria-expanded="openFilter === 'accounts'"
						aria-controls="filter-chips"
						@click="toggleFilter('accounts')"
					>
						<AppIcon :icon="ACCOUNT_BALANCE_WALLET" :size="18" class="mr-2" />
						<span class="truncate">{{ accountLabel }}</span>
						<AppIcon :icon="ARROW_DROP_DOWN" :size="18" />
					</button>
				</div>

				<button
					type="button"
					class="btn-icon relative m-1"
					:aria-label="t('transactions.filterByTag')"
					:aria-expanded="openFilter === 'tags'"
					aria-controls="filter-chips"
					@click="toggleFilter('tags')"
				>
					<AppIcon :icon="SELL_OUTLINED" />
					<span v-if="tagFilter.length" class="badge">{{ tagFilter.length }}</span>
				</button>

				<button
					type="button"
					class="btn-icon relative m-1"
					:aria-label="t('transactions.filterByCategory')"
					:aria-expanded="openFilter === 'categories'"
					aria-controls="filter-chips"
					@click="toggleFilter('categories')"
				>
					<AppIcon :icon="FILTER_LIST_OUTLINED" />
					<span v-if="categoryFilter.length" class="badge">{{ categoryFilter.length }}</span>
				</button>
			</div>

			<FilterChipRow
				id="filter-chips"
				v-model="shownSelection"
				:open="openFilter !== null"
				:label="shownRow.label"
				:options="shownRow.options.value"
				:empty-text="shownRow.emptyText"
			/>
		</TopBar>

		<p v-if="store.error" class="type-body-large p-4 text-error" role="alert">{{ store.error }}</p>

		<div v-else-if="store.loading && !store.transactions.length" class="px-4" aria-hidden="true">
			<div v-for="group in 3" :key="group" class="animate-pulse">
				<div class="px-2 pt-5 pb-3"><div class="h-4 w-28 rounded-xs bg-surface-container-highest" /></div>
				<div class="group-rows">
					<div v-for="row in 3" :key="row" class="group-row h-[88px]" />
				</div>
			</div>
		</div>

		<EmptyState
			v-else-if="!store.transactions.length"
			fill
			class="m-4"
			:title="t('transactions.emptyTitle')"
			:description="t('transactions.emptyDescription')"
		/>

		<!-- A day's rows under a heading for the day, drawn like the account groups: one block of separate rows. -->
		<template v-else>
			<section v-for="day in days" :key="day.date" class="px-4">
				<div class="group-header">
					<h2>{{ formatLongDate(day.date) }}</h2>
					<MoneyText :amount="day.total" :currency="currency" tone="signed" />
				</div>

				<ul class="group-rows">
					<li v-for="{ row, card } in day.items" :key="card.key">
						<SwipeReveal>
							<template #actions>
								<ActionIcon
									icon="delete"
									:label="t('common.deleteShort')"
									danger
									@click="pendingDelete = row.kind === 'transfer' ? row.leg : row.transaction"
								/>
							</template>

							<!-- The whole row opens the edit form. Spans, not blocks, because it is a button. -->
							<button
								type="button"
								class="group-row state-layer focus-ring cursor-pointer"
								@click="row.kind === 'transfer' ? openEdit(row.leg, row.toAccountId) : openEdit(row.transaction)"
							>
								<!-- The headline and what is under it at the start, its figures at the end, top-aligned with each other. -->
								<span class="flex gap-4 px-4 py-3">
									<span class="flex min-w-0 flex-1 flex-col gap-1">
										<span class="type-title-medium truncate">{{ card.title }}</span>

										<!-- A transfer's "From → To", the arrow an icon rather than a character. -->
										<span v-if="row.kind === 'transfer'" class="type-body-small flex items-center gap-1">
											<span class="truncate">{{ row.fromAccountName }}</span>
											<AppIcon :icon="ARROW_RIGHT_ALT" :size="16" class="text-secondary" />
											<span class="truncate">{{ row.toAccountName }}</span>
										</span>
										<span v-else class="type-body-small truncate">{{ card.subtitle }}</span>

										<span v-if="card.automated" class="type-label-small text-tertiary" :title="t('transactions.automatedHint')">{{
											t('transactions.automatedBadge')
										}}</span>
										<span v-if="formatTime(card.occurredOn)" class="type-body-small">{{ formatTime(card.occurredOn) }}</span>
									</span>

									<span class="flex shrink-0 flex-col items-end gap-1">
										<MoneyText
											:amount="card.amount"
											:currency="currency"
											:tone="card.tone === 'transfer' ? 'transfer' : 'signed-alert'"
											class="type-title-medium"
										/>
										<!-- An overdrawn account reads in the error colour, as its balance does on the Accounts screen. -->
										<span class="type-body-small tabular" :class="card.balance < 0 ? 'text-error' : ''">
											{{ displayMoney(card.balance, accountCurrency(card.balanceAccountId)) }}
										</span>
										<span v-if="card.category" class="type-body-small flex items-center gap-1.5">
											{{ card.category.name }}
											<span
												v-if="card.category.color"
												class="size-2 rounded-full"
												:style="{ backgroundColor: harmonised(card.category.color) }"
												aria-hidden="true"
											/>
										</span>
									</span>
								</span>

								<!-- Notes on the left, tags as chips at the right end of the same row, centred on each other. The icon stays with the note's first line. -->
								<span v-if="card.notes || card.tags.length" class="flex items-center gap-2 px-4 pb-3">
									<span class="flex min-w-0 flex-1 items-start gap-2">
										<template v-if="card.notes">
											<AppIcon :icon="EDIT_NOTE" :size="16" class="text-on-surface-variant" />
											<span class="type-body-small min-w-0 flex-1 text-on-surface-variant">{{ card.notes }}</span>
										</template>
									</span>

									<span v-if="card.tags.length" class="flex max-w-[60%] shrink-0 flex-wrap justify-end gap-1.5">
										<span v-for="tag in card.tags" :key="tag.id" class="chip">
											<span class="size-2 shrink-0 rounded-full" :style="{ backgroundColor: harmonised(tag.color) }" aria-hidden="true" />
											<span class="truncate">{{ tag.name }}</span>
										</span>
									</span>
								</span>
							</button>
						</SwipeReveal>
					</li>
				</ul>
			</section>

			<div v-if="store.hasMore" class="p-4">
				<button type="button" class="btn-text w-full" :disabled="store.loading" @click="store.loadMore()">
					{{ store.loading ? t('common.fetching') : t('transactions.loadMore', { shown: store.transactions.length, total: store.total }) }}
				</button>
			</div>
			<!-- The end of the list, and only once there is no more of it to load: the cat, small and quiet, so
			     the last row is not mistaken for a page that stopped short. -->
			<div v-else class="flex justify-center pt-6 pb-2">
				<CatMark class="w-10! opacity-50" />
			</div>
		</template>

		<TransactionForm
			ref="formRef"
			:open="dialogOpen"
			:transaction="editing"
			:transfer-to-account-id="editingTransferToAccountId"
			@submit="save"
			@close="dialogOpen = false"
		/>

		<AlertDialog
			:open="pendingDelete !== null"
			:title="pendingDelete?.transferId ? t('transactions.deleteTransferTitle') : t('transactions.deleteTransactionTitle')"
			:icon="DELETE"
			@close="pendingDelete = null"
		>
			<template v-if="pendingDelete?.transferId">
				{{ t('transactions.deleteTransferBody') }}
			</template>
			<template v-else>{{ t('transactions.deleteTransactionBody') }}</template>
			<template #actions>
				<button type="button" class="btn-text" @click="pendingDelete = null">{{ t('common.noGoBack') }}</button>
				<button type="button" class="btn-text text-error" @click="remove">{{ t('common.yesDelete') }}</button>
			</template>
		</AlertDialog>

		<FabButton :label="t('transactions.newTransaction')" @click="openCreate" />
	</div>
</template>
