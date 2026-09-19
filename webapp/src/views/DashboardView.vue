<script setup lang="ts">
import CategoryBars from '@/components/CategoryBars.vue';
import MoneyText from '@/components/MoneyText.vue';
import MonthSwitcher from '@/components/MonthSwitcher.vue';
import SpendChart from '@/components/SpendChart.vue';
import StatCard from '@/components/StatCard.vue';
import { formatDate, formatTime } from '@/lib/dates';
import { mergeTransferRows } from '@/lib/transactionRows';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { useTransactionStore } from '@/stores/transactions';
import { computed, onMounted, watch } from 'vue';

const budget = useBudgetStore();
const ledger = useLedgerStore();
const transactions = useTransactionStore();

/** Fetched deeper than the 8 shown so a transfer pair split across the page boundary still merges into one row. */
const FETCH_LIMIT = 16;
const SHOWN = 8;

const currency = computed(() => ledger.displayCurrency);
const recent = computed(() => mergeTransferRows(transactions.transactions).slice(0, SHOWN));

const month = computed({
	get: () => budget.month,
	set: (next: string) => void budget.setMonth(next),
});

async function loadAll(): Promise<void> {
	await Promise.all([ledger.load(), budget.load(true), transactions.load({ month: budget.month, limit: FETCH_LIMIT })]);
}

onMounted(loadAll);
watch(
	() => budget.month,
	(next) => void transactions.load({ month: next, limit: FETCH_LIMIT }),
);
</script>

<template>
	<div class="space-y-6">
		<header>
			<MonthSwitcher v-model="month" />
		</header>

		<p v-if="budget.error" class="banner-error" role="alert">
			{{ budget.error }}
		</p>

		<div class="grid gap-4 sm:grid-cols-3">
			<StatCard label="Net worth" :amount="budget.summary?.netWorth ?? 0" :currency="currency" />
			<StatCard label="Income this month" :amount="budget.summary?.income ?? 0" :currency="currency" />
			<StatCard label="Spent this month" :amount="budget.summary?.expenses ?? 0" :currency="currency" />
		</div>

		<div class="grid gap-4 sm:grid-cols-3">
			<StatCard
				label="Net this month"
				:amount="budget.summary?.net ?? 0"
				:currency="currency"
				signed
				:caption="(budget.summary?.net ?? 0) >= 0 ? 'Saved' : 'Overspent'"
			/>
			<StatCard label="Budget remaining" :amount="budget.unspent" :currency="currency" caption="Across budgeted categories" />
			<StatCard
				label="Over budget"
				:amount="budget.overspent"
				:currency="currency"
				signed
				:caption="budget.overspent < 0 ? 'Needs attention' : 'Nothing overspent'"
			/>
		</div>

		<SpendChart :month="budget.month" :days="budget.summary?.dailySpend ?? []" :currency="currency" />

		<div class="grid gap-4 lg:grid-cols-2">
			<CategoryBars title="Where the money went" :entries="budget.expenseBreakdown" :currency="currency" />

			<section class="card p-5">
				<header class="mb-4 flex items-center justify-between">
					<h2 class="text-sm font-medium text-on-surface">Recent activity</h2>
					<RouterLink to="/transactions" class="text-sm font-medium text-primary hover:underline"> View all </RouterLink>
				</header>

				<p v-if="!recent.length" class="py-6 text-center text-sm text-on-surface-variant">Nothing recorded this month yet.</p>

				<ul v-else class="divide-y divide-outline-variant">
					<li
						v-for="row in recent"
						:key="row.kind === 'transfer' ? row.id : row.transaction.id"
						class="flex items-center justify-between gap-3 py-2.5"
					>
						<template v-if="row.kind === 'transfer'">
							<div class="min-w-0">
								<p class="truncate text-sm font-medium text-on-surface">
									{{ row.payee || `${row.fromAccountName} → ${row.toAccountName}` }}
								</p>
								<p class="truncate text-xs text-on-surface-variant">
									{{ [formatDate(row.leg.occurredOn), formatTime(row.leg.occurredOn)].filter(Boolean).join(' · ') }} ·
									{{ row.fromAccountName }} → {{ row.toAccountName }}
								</p>
							</div>
							<MoneyText :amount="row.amount" :currency="currency" transfer />
						</template>
						<template v-else>
							<div class="min-w-0">
								<p class="truncate text-sm font-medium text-on-surface">
									{{ row.transaction.payee || row.transaction.categoryName || 'Uncategorized' }}
								</p>
								<p class="truncate text-xs text-on-surface-variant">
									{{ [formatDate(row.transaction.occurredOn), formatTime(row.transaction.occurredOn)].filter(Boolean).join(' · ') }} ·
									{{ row.transaction.accountName }}
								</p>
							</div>
							<MoneyText :amount="row.transaction.amount" :currency="currency" signed />
						</template>
					</li>
				</ul>
			</section>
		</div>
	</div>
</template>
