<script setup lang="ts">
import { currencySymbol, DEFAULT_CURRENCY, parseMoney, parsePercent, percentOf, toDecimalString } from '@/lib/money';
import { budgetStatus, STATUS } from '@/lib/palette';
import { displayMoney } from '@/lib/privacy';
import { useBudgetStore } from '@/stores/budget';
import type { CategoryBreakdown } from '@/types';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import { computed, ref } from 'vue';

const props = withDefaults(defineProps<{ entry: CategoryBreakdown; currency?: string }>(), { currency: DEFAULT_CURRENCY });

const budget = useBudgetStore();

const percent = computed(() => percentOf(props.entry.actual, props.entry.planned));
const over = computed(() => props.entry.planned > 0 && props.entry.remaining < 0);
const fill = computed(() => STATUS[budgetStatus(props.entry.actual, props.entry.planned)]);

/** Status is never carried by colour alone — each state ships a word. */
const statusLabel = computed(() => {
	if (props.entry.planned <= 0) return props.entry.actual > 0 ? 'Unbudgeted' : 'No budget set';
	if (over.value) return `${displayMoney(-props.entry.remaining, props.currency)} over`;
	return `${displayMoney(props.entry.remaining, props.currency)} left`;
});

const editing = ref(false);
const mode = ref<'amount' | 'percent'>('amount');
const draft = ref('');
const saving = ref(false);

const percentInvalid = computed(() => mode.value === 'percent' && draft.value.trim() !== '' && parsePercent(draft.value) === null);

function start(): void {
	if (props.entry.plannedPercent !== null) {
		mode.value = 'percent';
		draft.value = String(props.entry.plannedPercent);
	} else {
		mode.value = 'amount';
		draft.value = props.entry.planned > 0 ? toDecimalString(props.entry.planned) : '';
	}
	editing.value = true;
}

function selectMode(next: 'amount' | 'percent'): void {
	if (mode.value === next) return;
	mode.value = next;
	draft.value = '';
}

async function commit(): Promise<void> {
	let change: { amount: number } | { percent: number };

	if (draft.value.trim() === '') {
		change = { amount: 0 };
	} else if (mode.value === 'percent') {
		const value = parsePercent(draft.value);
		if (value === null) return;
		change = { percent: value };
	} else {
		const value = parseMoney(draft.value);
		if (value === null || value < 0) return;
		change = { amount: value };
	}

	saving.value = true;
	try {
		await budget.setBudget(props.entry.categoryId, change);
		editing.value = false;
	} finally {
		saving.value = false;
	}
}

const RING = { size: 40, radius: 16, stroke: 4, amplitude: 1.5, waves: 7 };

/** The share drawn as a wave running clockwise from the top, going flat once the plan is used up, like Material's wavy indicator. */
const arc = computed(() => {
	const progress = percent.value / 100;
	if (progress <= 0) return '';

	const centre = RING.size / 2;
	const amplitude = progress >= 1 ? 0 : RING.amplitude;
	const steps = Math.max(2, Math.ceil(progress * 180));
	const points: string[] = [];

	for (let step = 0; step <= steps; step++) {
		const angle = (step / steps) * progress * Math.PI * 2;
		const radius = RING.radius + amplitude * Math.sin(angle * RING.waves);
		const x = centre + radius * Math.sin(angle);
		const y = centre - radius * Math.cos(angle);
		points.push(`${step === 0 ? 'M' : 'L'}${x.toFixed(2)} ${y.toFixed(2)}`);
	}

	return points.join(' ') + (progress >= 1 ? ' Z' : '');
});
</script>

<template>
	<div class="card p-4">
		<div class="flex items-baseline justify-between gap-3">
			<span class="min-w-0 flex-1 truncate text-sm text-on-surface">{{ entry.name }}</span>

			<span class="tabular shrink-0 text-xs text-on-surface-variant">
				{{ displayMoney(entry.actual, currency)
				}}<template v-if="entry.planned > 0"> / {{ displayMoney(entry.planned, currency) }}</template>
			</span>
		</div>

		<form v-if="editing" class="mt-2 space-y-2" @submit.prevent="commit" @keydown.esc="editing = false">
			<ConnectedButtonGroup
				:model-value="mode"
				label="Enter as"
				:options="[
					{ value: 'amount', label: currency },
					{ value: 'percent', label: '%' },
				]"
				@update:model-value="selectMode"
			/>

			<div>
				<div class="relative">
					<span
						v-if="mode === 'amount'"
						class="pointer-events-none absolute inset-y-0 left-4 grid place-items-center text-base text-on-surface-variant"
						aria-hidden="true"
					>
						{{ currencySymbol(currency) }}
					</span>
					<input
						v-model="draft"
						class="input tabular"
						:class="[mode === 'amount' ? 'pl-10' : '', percentInvalid ? 'border-error' : '']"
						inputmode="decimal"
						:aria-label="mode === 'percent' ? 'Planned percent of income' : 'Planned amount'"
						:aria-invalid="percentInvalid"
						:placeholder="mode === 'percent' ? '0' : '0.00'"
						:disabled="saving"
						autofocus
					/>
				</div>
				<p v-if="percentInvalid" class="mt-1 px-4 text-xs text-error" role="alert">Enter a percentage between 0 and 100</p>
			</div>

			<div class="flex gap-2">
				<button type="button" class="btn-outlined flex-1" :disabled="saving" @click="editing = false">Cancel</button>
				<button type="submit" class="btn-primary flex-1" :disabled="saving || percentInvalid">Save</button>
			</div>
		</form>

		<button v-else type="button" class="btn-text mt-1 -ml-3 text-3xl tabular" @click="start">
			<template v-if="entry.plannedPercent !== null">{{ entry.plannedPercent }}%</template>
			<template v-else-if="entry.planned > 0">{{ displayMoney(entry.planned, currency) }}</template>
			<template v-else>Set a budget</template>
		</button>

		<div class="mt-2.5 flex items-center gap-3">
			<p class="flex-1 text-xs" :class="over ? 'text-error' : 'text-on-surface-variant'">{{ statusLabel }}</p>

			<div class="relative grid shrink-0 place-items-center" :style="{ width: `${RING.size}px`, height: `${RING.size}px` }">
				<svg :viewBox="`0 0 ${RING.size} ${RING.size}`" class="absolute inset-0" aria-hidden="true">
					<circle
						:cx="RING.size / 2"
						:cy="RING.size / 2"
						:r="RING.radius"
						fill="none"
						class="stroke-surface-variant"
						:stroke-width="RING.stroke"
					/>
					<path
						v-if="arc"
						:d="arc"
						fill="none"
						:stroke-width="RING.stroke"
						stroke-linecap="round"
						stroke-linejoin="round"
						:style="{ stroke: fill }"
					/>
				</svg>
				<span class="type-label-small relative text-on-surface">{{ percent }}%</span>
			</div>
		</div>
	</div>
</template>
