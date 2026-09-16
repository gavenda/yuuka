<script setup lang="ts">
import CategoryBars from '@/components/CategoryBars.vue';
import MoneyText from '@/components/MoneyText.vue';
import MonthSwitcher from '@/components/MonthSwitcher.vue';
import SpendChart from '@/components/SpendChart.vue';
import StatCard from '@/components/StatCard.vue';
import { formatDate } from '@/lib/dates';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { useTransactionStore } from '@/stores/transactions';
import { computed, onMounted, watch } from 'vue';

const budget = useBudgetStore();
const ledger = useLedgerStore();
const transactions = useTransactionStore();

const currency = computed(() => ledger.displayCurrency);
const recent = computed(() => transactions.transactions.slice(0, 8));

const month = computed({
	get: () => budget.month,
	set: (next: string) => void budget.setMonth(next),
});

async function loadAll(): Promise<void> {
	await Promise.all([ledger.load(), budget.load(true), transactions.load({ month: budget.month, limit: 8 })]);
}

onMounted(loadAll);
watch(
	() => budget.month,
	(next) => void transactions.load({ month: next, limit: 8 }),
);
</script>

<template>
	<div class="space-y-6">
		<header class="flex flex-wrap items-center justify-between gap-3">
			<h1 class="text-xl font-semibold tracking-tight text-slate-900 dark:text-white">Dashboard</h1>
			<MonthSwitcher v-model="month" />
		</header>

		<p
			v-if="budget.error"
			class="rounded-lg bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:bg-rose-500/10 dark:text-rose-400"
			role="alert"
		>
			{{ budget.error }}
		</p>

		<!-- One hero figure per view: everything else on the page explains it. -->
		<div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
			<StatCard label="Net worth" :amount="budget.summary?.netWorth ?? 0" :currency="currency" hero class="sm:col-span-2" />
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
					<h2 class="text-sm font-semibold text-slate-900 dark:text-white">Recent activity</h2>
					<RouterLink to="/transactions" class="text-sm font-medium text-blue-700 hover:underline dark:text-blue-400">
						View all
					</RouterLink>
				</header>

				<p v-if="!recent.length" class="py-6 text-center text-sm text-slate-500 dark:text-slate-400">Nothing recorded this month yet.</p>

				<ul v-else class="divide-y divide-slate-100 dark:divide-slate-800/60">
					<li v-for="transaction in recent" :key="transaction.id" class="flex items-center justify-between gap-3 py-2.5">
						<div class="min-w-0">
							<p class="truncate text-sm font-medium text-slate-900 dark:text-slate-100">
								{{ transaction.payee || transaction.categoryName || 'Uncategorised' }}
							</p>
							<p class="truncate text-xs text-slate-500 dark:text-slate-400">
								{{ formatDate(transaction.occurredOn) }} · {{ transaction.accountName }}
							</p>
						</div>
						<MoneyText :amount="transaction.amount" :currency="currency" signed />
					</li>
				</ul>
			</section>
		</div>
	</div>
</template>
