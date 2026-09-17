<script setup lang="ts">
import BudgetAmountEditor from '@/components/BudgetAmountEditor.vue';
import BudgetMeter from '@/components/BudgetMeter.vue';
import EmptyState from '@/components/EmptyState.vue';
import MonthSwitcher from '@/components/MonthSwitcher.vue';
import PhilippinesIncomeCalculator from '@/components/PhilippinesIncomeCalculator.vue';
import StatCard from '@/components/StatCard.vue';
import { parseMoney, percentOf, toDecimalString } from '@/lib/money';
import { computeNetPay } from '@/lib/philippinesTax';
import { displayMoney } from '@/lib/privacy';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { computed, onMounted, ref, watch } from 'vue';

const budget = useBudgetStore();
const ledger = useLedgerStore();

const currency = computed(() => ledger.displayCurrency);

/** PH-specific: what's typed is gross pay, and the take-home net is what actually gets budgeted from. */
const isPhp = computed(() => currency.value === 'PHP');

const month = computed({
	get: () => budget.month,
	set: (next: string) => void budget.setMonth(next),
});

const totalPlanned = computed(() => budget.expenseBreakdown.reduce((sum, entry) => sum + entry.planned, 0));
const totalActual = computed(() => budget.expenseBreakdown.reduce((sum, entry) => sum + entry.actual, 0));

/** Everything with a plan against it, expense or cashflow — what a percentage of income has been put towards. */
const totalAllocated = computed(() =>
	[...budget.expenseBreakdown, ...budget.cashflowBreakdown].reduce((sum, entry) => sum + entry.planned, 0),
);
const unallocatedIncome = computed(() => budget.plannedIncome - totalAllocated.value);

const editingIncome = ref(false);
const savingIncome = ref(false);
const incomeDraft = ref('');

/** PH-only: whether the figure being typed is gross pay to convert, or the budgeted amount itself. */
const incomeMode = ref<'gross' | 'fixed'>('gross');
const usingGross = computed(() => isPhp.value && incomeMode.value === 'gross');

/** The gross figure being typed, parsed live so the PH breakdown can update as they type. */
const grossDraft = computed(() => {
	if (incomeDraft.value.trim() === '') return 0;
	const amount = parseMoney(incomeDraft.value);
	return amount && amount > 0 ? amount : 0;
});

const netPayBreakdown = computed(() => (usingGross.value ? computeNetPay(grossDraft.value) : null));

/** What the closed field should read for the mode currently selected, from what's saved server-side. */
function syncIncomeDraft(): void {
	if (usingGross.value) {
		incomeDraft.value = budget.plannedIncomeGrossAmount ? toDecimalString(budget.plannedIncomeGrossAmount) : '';
	} else {
		incomeDraft.value = budget.plannedIncome > 0 ? toDecimalString(budget.plannedIncome) : '';
	}
}

// The server remembers both the mode and the figure it produced the saved amount from, so
// there's nothing to persist in the browser: every load or refresh — first mount, switching
// months, right after saving — picks it back up here.
watch(
	() => budget.summary,
	(summary) => {
		if (!summary) return;
		incomeMode.value = summary.plannedIncomeMode;
		syncIncomeDraft();
	},
	{ immediate: true },
);

// Switching modes locally, before saving, shows what was last saved for that mode.
watch(incomeMode, () => {
	if (!isPhp.value) return;
	editingIncome.value = false;
	syncIncomeDraft();
});

function startEditingIncome(): void {
	editingIncome.value = true;
}

async function commitIncome(): Promise<void> {
	const amount = incomeDraft.value.trim() === '' ? 0 : parseMoney(incomeDraft.value);
	if (amount === null || amount < 0) return;

	const toSave = usingGross.value && amount > 0 ? computeNetPay(amount).netPay : amount;

	savingIncome.value = true;
	try {
		await budget.setIncomePlan(toSave, usingGross.value ? 'gross' : 'fixed', usingGross.value ? amount : undefined);
		editingIncome.value = false;
	} finally {
		savingIncome.value = false;
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

		<section class="card p-5">
			<div class="flex flex-wrap items-center justify-between gap-3">
				<div>
					<p class="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">Planned income</p>
					<p class="mt-1 text-sm text-slate-500 dark:text-slate-400">
						<template v-if="usingGross"> Enter your gross monthly pay — the take-home net is what you budget from. </template>
						<template v-else>
							Set what you expect to bring in, then budget a category as a percentage of it instead of a fixed amount.
						</template>
					</p>
				</div>

				<div class="flex shrink-0 items-center gap-2">
					<!-- Switching modes changes what the same draft means, not what's shown while editing it. -->
					<div v-if="isPhp" class="flex overflow-hidden rounded-md border border-slate-300 dark:border-slate-700">
						<button
							type="button"
							class="px-1.5 py-1 text-xs font-medium"
							:class="incomeMode === 'gross' ? 'bg-blue-600 text-white' : 'text-slate-500 dark:text-slate-400'"
							@click="incomeMode = 'gross'"
						>
							Gross
						</button>
						<button
							type="button"
							class="px-1.5 py-1 text-xs font-medium"
							:class="incomeMode === 'fixed' ? 'bg-blue-600 text-white' : 'text-slate-500 dark:text-slate-400'"
							@click="incomeMode = 'fixed'"
						>
							Fixed
						</button>
					</div>

					<form v-if="editingIncome" class="flex items-center gap-1" @submit.prevent="commitIncome">
						<input
							v-model="incomeDraft"
							class="input tabular w-32 py-1 text-right"
							inputmode="decimal"
							:placeholder="usingGross ? 'Gross 0.00' : '0.00'"
							autofocus
							@keydown.esc="editingIncome = false"
						/>
						<button type="submit" class="btn-primary px-2 py-1 text-xs" :disabled="savingIncome">Save</button>
					</form>
					<button v-else type="button" class="btn-secondary tabular px-3 py-1 text-sm" @click="startEditingIncome">
						<template v-if="usingGross">{{ grossDraft > 0 ? displayMoney(grossDraft, 'PHP') : 'Set gross income' }}</template>
						<template v-else>{{ budget.plannedIncome > 0 ? displayMoney(budget.plannedIncome, currency) : 'Set income' }}</template>
					</button>
				</div>
			</div>

			<PhilippinesIncomeCalculator v-if="usingGross" :gross="grossDraft" />
		</section>

		<StatCard v-if="netPayBreakdown" label="Net pay" :amount="netPayBreakdown.netPay" currency="PHP" caption="Used as planned income" />

		<template v-if="budget.plannedIncome > 0">
			<StatCard label="Allocated" :amount="totalAllocated" :currency="currency" caption="Planned across expense and cashflow categories" />
			<StatCard
				label="Unallocated"
				:amount="unallocatedIncome"
				:currency="currency"
				signed
				caption="Income not yet put towards a category"
			/>
		</template>

		<StatCard label="Planned" :amount="totalPlanned" :currency="currency" caption="Across expense categories" />
		<StatCard
			label="Spent"
			:amount="totalActual"
			:currency="currency"
			:caption="totalPlanned > 0 ? `${percentOf(totalActual, totalPlanned)}% of plan` : 'No plan set'"
		/>
		<StatCard label="Remaining" :amount="totalPlanned - totalActual" :currency="currency" signed caption="Planned minus spent" />

		<StatCard
			v-if="budget.cashflowBreakdown.length"
			label="Moved to cashflow"
			:amount="budget.summary?.cashflow ?? 0"
			:currency="currency"
			caption="Investments, savings and the like"
		/>

		<EmptyState
			v-if="!budget.loading && !budget.expenseBreakdown.length"
			title="No categories yet"
			description="Budgets are set per category, so create a few first."
		>
			<RouterLink to="/categories" class="btn-primary">Add categories</RouterLink>
		</EmptyState>

		<section v-else class="card p-5">
			<h2 class="mb-1 text-sm font-semibold text-slate-900 dark:text-white">Expense</h2>
			<p class="mb-3 text-sm text-slate-500 dark:text-slate-400">
				<template v-if="ledger.budgetMode === 'fixed'">Click a planned amount to change it — it applies to every month.</template>
				<template v-else>Click a planned amount to change it for {{ month }}.</template>
			</p>

			<ul class="divide-y divide-slate-100 dark:divide-slate-800/60">
				<li v-for="entry in budget.expenseBreakdown" :key="entry.categoryId">
					<div class="flex items-center gap-3">
						<div class="min-w-0 flex-1">
							<BudgetMeter :entry="entry" :currency="currency" />
						</div>

						<div class="shrink-0">
							<BudgetAmountEditor :entry="entry" :currency="currency" />
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
							<BudgetAmountEditor :entry="entry" :currency="currency" />
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
