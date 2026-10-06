<script setup lang="ts">
import { currencySymbol, DEFAULT_CURRENCY, parseMoney, parsePercent, percentOf, toDecimalString } from '@/lib/money';
import { budgetStatus, STATUS } from '@/lib/palette';
import { displayMoney } from '@/lib/privacy';
import { useBudgetStore } from '@/stores/budget';
import type { CategoryBreakdown } from '@/types';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import DenseField from '@/components/DenseField.vue';
import { reducedMotion, useFrameClock } from '@/lib/frameClock';
import { useFormValidation } from '@/lib/validation';
import { computed, ref, shallowRef, watch } from 'vue';

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

/** Every card has its own field, so the id carries the category; a card is never reused for another one. */
const fieldId = `budget-${props.entry.categoryId}`;

const validation = useFormValidation({
	[fieldId]: () => {
		// Empty clears the plan, which is a change like any other.
		if (draft.value.trim() === '') return null;
		if (mode.value === 'percent') return parsePercent(draft.value) === null ? 'Enter a percentage between 0 and 100.' : null;

		const value = parseMoney(draft.value);
		if (value === null) return 'Enter an amount as a number, such as 250.00.';
		return value < 0 ? 'A budget cannot be negative.' : null;
	},
});

const { error: fieldError, touch } = validation;

function start(): void {
	validation.reset();
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
	validation.reset();
}

async function commit(): Promise<void> {
	if (!validation.isValid.value) return;

	let change: { amount: number } | { percent: number };

	if (draft.value.trim() === '') {
		change = { amount: 0 };
	} else if (mode.value === 'percent') {
		change = { percent: parsePercent(draft.value) as number };
	} else {
		change = { amount: parseMoney(draft.value) as number };
	}

	saving.value = true;
	try {
		await budget.setBudget(props.entry.categoryId, change);
		editing.value = false;
	} finally {
		saving.value = false;
	}
}

/** What the plan reads as when it is not being edited: the share, the amount, or an invitation to set one. */
const plannedLabel = computed(() => {
	// Always with a decimal, as Android writes a share: 15 reads "15.0%".
	if (props.entry.plannedPercent !== null) {
		return `${Number.isInteger(props.entry.plannedPercent) ? props.entry.plannedPercent.toFixed(1) : props.entry.plannedPercent}%`;
	}
	return props.entry.planned > 0 ? displayMoney(props.entry.planned, props.currency) : 'Set a budget';
});

/** Material's circular wavy progress indicator: a 48dp box, a 4dp stroke. */
const RING = { size: 48, radius: 19, stroke: 4, amplitude: 1.6, waves: 9, gap: 0.035 };

const centre = RING.size / 2;

function point(turn: number, radius: number): string {
	const angle = turn * Math.PI * 2;
	return `${(centre + radius * Math.sin(angle)).toFixed(2)} ${(centre - radius * Math.cos(angle)).toFixed(2)}`;
}

/** How long the ring takes to reach a new share, and how long the wave takes to travel one of its own lengths. */
const PROGRESS_MS = 500;
const WAVE_MS = 1000;

const clock = useFrameClock();
const target = computed(() => percent.value / 100);
/** Where the ring last set off from, and when; it starts out already arrived. */
const tween = shallowRef({ from: target.value, at: -Infinity });

function shownTowards(to: number): number {
	const elapsed = reducedMotion() ? 1 : Math.min(1, (clock.value - tween.value.at) / PROGRESS_MS);
	return tween.value.from + (to - tween.value.from) * (1 - (1 - elapsed) ** 3);
}

// A share that changes is travelled to from wherever the ring had got to, as Material's indicator does.
watch(target, (_, previous) => (tween.value = { from: shownTowards(previous), at: clock.value }));

/** The share the ring is drawing this frame, on its way to the real one. */
const shown = computed(() => shownTowards(target.value));

/** The share drawn as a wave running clockwise from the top, going flat once the plan is used up. */
const arc = computed(() => {
	const progress = shown.value;
	if (progress <= 0) return '';

	const amplitude = progress >= 1 ? 0 : RING.amplitude;
	// The wave travels round the ring; a flat one has nothing to move, so it does not redraw every frame.
	const phase = amplitude ? ((clock.value % WAVE_MS) / WAVE_MS) * Math.PI * 2 : 0;
	const steps = Math.max(2, Math.ceil(progress * 160));
	const points: string[] = [];

	for (let step = 0; step <= steps; step++) {
		const turn = (step / steps) * progress;
		points.push(`${step === 0 ? 'M' : 'L'}${point(turn, RING.radius + amplitude * Math.sin(turn * Math.PI * 2 * RING.waves - phase))}`);
	}

	return points.join(' ') + (progress >= 1 ? ' Z' : '');
});

/** What is left of the ring, as a plain track, standing a little clear of the wave at both ends. */
const track = computed(() => {
	const progress = shown.value;
	if (progress >= 1) return '';
	if (progress <= 0)
		return `M${point(0, RING.radius)} A${RING.radius} ${RING.radius} 0 1 1 ${point(0.5, RING.radius)} A${RING.radius} ${RING.radius} 0 1 1 ${point(0, RING.radius)}`;

	const from = progress + RING.gap;
	const to = 1 - RING.gap;
	if (to <= from) return '';
	return `M${point(from, RING.radius)} A${RING.radius} ${RING.radius} 0 ${to - from > 0.5 ? 1 : 0} 1 ${point(to, RING.radius)}`;
});
</script>

<template>
	<article class="card p-4">
		<header class="flex items-center justify-between gap-3">
			<h3 class="type-body-medium min-w-0 flex-1 truncate">{{ entry.name }}</h3>
			<span class="type-body-small tabular">
				{{ displayMoney(entry.actual, currency)
				}}<template v-if="entry.planned > 0"> / {{ displayMoney(entry.planned, currency) }}</template>
			</span>
		</header>

		<form v-if="editing" class="flex flex-col gap-2 pt-2" novalidate @submit.prevent="commit" @input="validation.onInput">
			<ConnectedButtonGroup
				:model-value="mode"
				label="Budget as"
				:options="[
					{ value: 'amount', label: currency },
					{ value: 'percent', label: '%' },
				]"
				:disabled="saving"
				@update:model-value="selectMode"
			/>

			<!-- Empty clears the plan, which is a change like any other; anything else has to be a share, or an amount that is not negative. -->
			<DenseField
				:id="fieldId"
				v-model="draft"
				class="type-body-large"
				label="Planned"
				inputmode="decimal"
				:placeholder="mode === 'percent' ? '0' : '0.00'"
				:prefix="mode === 'amount' ? currencySymbol(currency) : undefined"
				:disabled="saving"
				:error="fieldError(fieldId)"
				@blur="touch(fieldId)"
				@keydown.esc="editing = false"
			/>

			<div class="flex gap-2">
				<button type="button" class="btn-outlined flex-1" :disabled="saving" @click="editing = false">Cancel</button>
				<button type="submit" class="btn-primary flex-1" :disabled="saving || !validation.isValid.value">Save</button>
			</div>
		</form>

		<button v-else type="button" class="btn-text type-headline-medium mt-1 min-h-11 px-0 py-1" @click="start">{{ plannedLabel }}</button>

		<!-- The state in words, then the share spent as a ring: status never rests on colour alone. -->
		<div class="flex items-center gap-3 pt-2.5">
			<span class="type-body-small min-w-0 flex-1" :class="over ? 'text-error' : ''">{{ statusLabel }}</span>

			<div class="relative grid size-12 shrink-0 place-items-center">
				<svg :viewBox="`0 0 ${RING.size} ${RING.size}`" class="absolute inset-0 size-full" aria-hidden="true">
					<path
						v-if="track"
						:d="track"
						fill="none"
						stroke="var(--color-secondary-container)"
						:stroke-width="RING.stroke"
						stroke-linecap="round"
					/>
					<path v-if="arc" :d="arc" fill="none" :stroke="fill" :stroke-width="RING.stroke" stroke-linecap="round" stroke-linejoin="round" />
				</svg>
				<span class="type-label-small relative">{{ percent }}%</span>
			</div>
		</div>
	</article>
</template>
