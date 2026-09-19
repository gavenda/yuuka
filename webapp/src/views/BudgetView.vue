<script setup lang="ts">
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
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
/** Unclamped, unlike `percentOf`: over-allocating reads as a negative share. */
const unallocatedPercent = computed(() =>
	budget.plannedIncome > 0 ? Math.round((unallocatedIncome.value / budget.plannedIncome) * 100) : 0,
);

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

// Together, so the summary's saved copy is not held back by the ledger's round trip.
onMounted(() => Promise.all([ledger.load(), budget.load()]));
</script>

<template>
	<div class="space-y-6">
		<header>
			<MonthSwitcher v-model="month" />
		</header>

		<section class="card p-5">
			<div class="flex flex-wrap items-center justify-between gap-3">
				<div>
					<p class="type-title-small text-on-surface-variant">Planned income</p>
					<p class="mt-1 text-sm text-on-surface-variant">
						<template v-if="usingGross"> Enter your gross monthly pay — the take-home net is what you budget from. </template>
						<template v-else>
							Set what you expect to bring in, then budget a category as a percentage of it instead of a fixed amount.
						</template>
					</p>
				</div>

				<div class="flex shrink-0 items-center gap-2">
					<!-- Switching modes changes what the same draft means, not what's shown while editing it. -->
					<ConnectedButtonGroup
						v-if="isPhp"
						v-model="incomeMode"
						class="shrink-0"
						label="Income entered as"
						dense
						:options="[
							{ value: 'gross', label: 'Gross' },
							{ value: 'fixed', label: 'Fixed' },
						]"
					/>

					<form v-if="editingIncome" class="flex items-center gap-1" @submit.prevent="commitIncome">
						<input
							v-model="incomeDraft"
							class="input input-sm tabular w-32 text-right"
							inputmode="decimal"
							:placeholder="usingGross ? 'Gross 0.00' : '0.00'"
							autofocus
							@keydown.esc="editingIncome = false"
						/>
						<button type="submit" class="btn-primary btn-sm" :disabled="savingIncome">Save</button>
					</form>
					<button v-else type="button" class="btn-secondary btn-sm tabular" @click="startEditingIncome">
						<template v-if="usingGross">{{ grossDraft > 0 ? displayMoney(grossDraft, 'PHP') : 'Set gross income' }}</template>
						<template v-else>{{ budget.plannedIncome > 0 ? displayMoney(budget.plannedIncome, currency) : 'Set income' }}</template>
					</button>
				</div>
			</div>

			<PhilippinesIncomeCalculator v-if="usingGross" :gross="grossDraft" />

			<div v-if="budget.incomeBreakdown.length" class="mt-4">
				<p class="type-title-small text-on-surface-variant">Income</p>
				<ul class="mt-2 divide-y divide-outline-variant">
					<li v-for="entry in budget.incomeBreakdown" :key="entry.categoryId" class="flex items-center justify-between gap-3 py-2 text-sm">
						<span class="text-on-surface-variant">{{ entry.name }}</span>
						<span class="tabular text-on-surface">{{ displayMoney(entry.actual, currency) }}</span>
					</li>
				</ul>
			</div>
		</section>

		<!-- One row each from md up; stacked on mobile. Columns follow however many cards are showing. -->
		<div v-if="netPayBreakdown || budget.plannedIncome > 0" class="grid gap-6 md:auto-cols-fr md:grid-flow-col">
			<StatCard v-if="netPayBreakdown" label="Net pay" :amount="netPayBreakdown.netPay" currency="PHP" caption="Used as planned income" />

			<template v-if="budget.plannedIncome > 0">
				<StatCard
					label="Allocated"
					:amount="totalAllocated"
					:currency="currency"
					caption="Planned across expense and cashflow categories"
				/>
				<StatCard
					label="Unallocated"
					:amount="unallocatedIncome"
					:currency="currency"
					signed
					:caption="`${unallocatedPercent}% of planned income`"
				/>
			</template>
		</div>

		<div class="grid gap-6 md:auto-cols-fr md:grid-flow-col">
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
		</div>

		<EmptyState
			v-if="!budget.loading && !budget.expenseBreakdown.length"
			title="No categories yet"
			description="Budgets are set per category, so create a few first."
		>
			<RouterLink to="/categories" class="btn-primary">Add categories</RouterLink>
		</EmptyState>

		<section v-else class="card p-5">
			<h2 class="mb-1 text-sm font-medium text-on-surface">Expense</h2>
			<p class="mb-3 text-sm text-on-surface-variant">
				<template v-if="ledger.budgetMode === 'fixed'">Click a planned amount to change it — it applies to every month.</template>
				<template v-else>Click a planned amount to change it for {{ month }}.</template>
			</p>

			<ul class="divide-y divide-outline-variant">
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
			<h2 class="mb-1 text-sm font-medium text-on-surface">Cashflow</h2>
			<p class="mb-3 text-sm text-on-surface-variant">
				Money moved between your own accounts. Investments live here — a contribution is a movement, not spending, so it is budgeted apart
				from the figures above.
			</p>

			<ul class="divide-y divide-outline-variant">
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
	</div>
</template>
