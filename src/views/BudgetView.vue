<script setup lang="ts">
import BudgetMeter from '@/components/BudgetMeter.vue';
import EmptyState from '@/components/EmptyState.vue';
import MonthSwitcher from '@/components/MonthSwitcher.vue';
import StatCard from '@/components/StatCard.vue';
import { parseMoney, toDecimalString } from '@/lib/money';
import { displayMoney } from '@/lib/privacy';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { computed, onMounted, ref } from 'vue';

const budget = useBudgetStore();
const ledger = useLedgerStore();

const editingId = ref<string | null>(null);
const draft = ref('');
const saving = ref(false);

const currency = computed(() => ledger.displayCurrency);

const month = computed({
	get: () => budget.month,
	set: (next: string) => void budget.setMonth(next),
});

const totalPlanned = computed(() => budget.expenseBreakdown.reduce((sum, entry) => sum + entry.planned, 0));
const totalActual = computed(() => budget.expenseBreakdown.reduce((sum, entry) => sum + entry.actual, 0));

function startEditing(categoryId: string, planned: number): void {
	editingId.value = categoryId;
	draft.value = planned > 0 ? toDecimalString(planned) : '';
}

async function commit(categoryId: string): Promise<void> {
	const amount = draft.value.trim() === '' ? 0 : parseMoney(draft.value);
	if (amount === null || amount < 0) return;

	saving.value = true;
	try {
		await budget.setBudget(categoryId, amount);
		editingId.value = null;
	} finally {
		saving.value = false;
	}
}

onMounted(async () => {
	await ledger.load();
	await budget.load();
});
</script>

<template>
	<div class="space-y-6">
		<header class="flex flex-wrap items-center justify-between gap-3">
			<h1 class="text-xl font-semibold tracking-tight text-slate-900 dark:text-white">Budget</h1>
			<MonthSwitcher v-model="month" />
		</header>

		<div class="grid gap-4 sm:grid-cols-3">
			<StatCard label="Planned" :amount="totalPlanned" :currency="currency" caption="Across expense categories" />
			<StatCard
				label="Spent"
				:amount="totalActual"
				:currency="currency"
				:caption="`${Math.round((totalActual / (totalPlanned || 1)) * 100)}% of plan`"
			/>
			<StatCard label="Remaining" :amount="totalPlanned - totalActual" :currency="currency" signed caption="Planned minus spent" />
		</div>

		<div v-if="budget.cashflowBreakdown.length" class="grid gap-4 sm:grid-cols-3">
			<StatCard
				label="Moved to cashflow"
				:amount="budget.summary?.cashflow ?? 0"
				:currency="currency"
				caption="Investments, savings and the like"
			/>
		</div>

		<EmptyState
			v-if="!budget.loading && !budget.expenseBreakdown.length"
			title="No categories yet"
			description="Budgets are set per category, so create a few first."
		>
			<RouterLink to="/categories" class="btn-primary">Add categories</RouterLink>
		</EmptyState>

		<section v-else class="card p-5">
			<h2 class="mb-1 text-sm font-semibold text-slate-900 dark:text-white">Expense</h2>
			<p class="mb-3 text-sm text-slate-500 dark:text-slate-400">Click a planned amount to change it for {{ month }}.</p>

			<ul class="divide-y divide-slate-100 dark:divide-slate-800/60">
				<li v-for="entry in budget.expenseBreakdown" :key="entry.categoryId">
					<div class="flex items-center gap-3">
						<div class="min-w-0 flex-1">
							<BudgetMeter :entry="entry" :currency="currency" />
						</div>

						<div class="shrink-0">
							<form v-if="editingId === entry.categoryId" class="flex items-center gap-1" @submit.prevent="commit(entry.categoryId)">
								<input
									v-model="draft"
									class="input tabular w-28 py-1 text-right"
									inputmode="decimal"
									placeholder="0.00"
									autofocus
									@keydown.esc="editingId = null"
								/>
								<button type="submit" class="btn-primary px-2 py-1 text-xs" :disabled="saving">Save</button>
							</form>

							<button
								v-else
								type="button"
								class="btn-secondary tabular px-3 py-1 text-xs"
								@click="startEditing(entry.categoryId, entry.planned)"
							>
								{{ entry.planned > 0 ? displayMoney(entry.planned, currency) : 'Set budget' }}
							</button>
						</div>
					</div>
				</li>
			</ul>
		</section>

		<section v-if="budget.cashflowBreakdown.length" class="card p-5">
			<h2 class="mb-1 text-sm font-semibold text-slate-900 dark:text-white">Cashflow</h2>
			<p class="mb-3 text-sm text-slate-500 dark:text-slate-400">
				Money moved between your own accounts. Investments live here — a contribution is a movement, not spending, so it is budgeted apart
				from the figures above.
			</p>

			<ul class="divide-y divide-slate-100 dark:divide-slate-800/60">
				<li v-for="entry in budget.cashflowBreakdown" :key="entry.categoryId">
					<div class="flex items-center gap-3">
						<div class="min-w-0 flex-1">
							<BudgetMeter :entry="entry" :currency="currency" />
						</div>

						<div class="shrink-0">
							<form v-if="editingId === entry.categoryId" class="flex items-center gap-1" @submit.prevent="commit(entry.categoryId)">
								<input
									v-model="draft"
									class="input tabular w-28 py-1 text-right"
									inputmode="decimal"
									placeholder="0.00"
									autofocus
									@keydown.esc="editingId = null"
								/>
								<button type="submit" class="btn-primary px-2 py-1 text-xs" :disabled="saving">Save</button>
							</form>

							<button
								v-else
								type="button"
								class="btn-secondary tabular px-3 py-1 text-xs"
								@click="startEditing(entry.categoryId, entry.planned)"
							>
								{{ entry.planned > 0 ? displayMoney(entry.planned, currency) : 'Set budget' }}
							</button>
						</div>
					</div>
				</li>
			</ul>
		</section>

		<section v-if="budget.incomeBreakdown.length" class="card p-5">
			<h2 class="mb-3 text-sm font-semibold text-slate-900 dark:text-white">Income</h2>
			<ul class="divide-y divide-slate-100 dark:divide-slate-800/60">
				<li v-for="entry in budget.incomeBreakdown" :key="entry.categoryId" class="flex items-center justify-between gap-3 py-3">
					<div class="flex min-w-0 items-center gap-2">
						<span class="h-2.5 w-2.5 shrink-0 rounded-full" :style="{ backgroundColor: entry.color }" />
						<span class="truncate text-sm font-medium text-slate-900 dark:text-slate-100">{{ entry.name }}</span>
					</div>
					<span class="tabular text-sm text-emerald-700 dark:text-emerald-400">{{ displayMoney(entry.actual, currency) }}</span>
				</li>
			</ul>
		</section>
	</div>
</template>
