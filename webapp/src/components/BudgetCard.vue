<script setup lang="ts">
import { currencySymbol, DEFAULT_CURRENCY, parseMoney, parsePercent, percentOf, toDecimalString } from '@/lib/money';
import { budgetStatus, STATUS } from '@/lib/palette';
import { displayMoney } from '@/lib/privacy';
import { useBudgetStore } from '@/stores/budget';
import type { CategoryBreakdown } from '@/types';
import AlertDialog from '@/components/AlertDialog.vue';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import TextField from '@/components/TextField.vue';
import { reducedMotion, useFrameClock } from '@/lib/frameClock';
import { useFormValidation } from '@/lib/validation';
import { computed, ref, shallowRef, watch } from 'vue';
import { t } from '@/i18n';

const props = withDefaults(defineProps<{ entry: CategoryBreakdown; currency?: string }>(), { currency: DEFAULT_CURRENCY });

const budget = useBudgetStore();

const percent = computed(() => percentOf(props.entry.actual, props.entry.planned));
const over = computed(() => props.entry.planned > 0 && props.entry.remaining < 0);
const fill = computed(() => STATUS[budgetStatus(props.entry.actual, props.entry.planned)]);

/** Status is never carried by colour alone — each state ships a word. */
const statusLabel = computed(() => {
	if (props.entry.planned <= 0) return props.entry.actual > 0 ? t('budget.unbudgeted') : t('budget.noBudget');
	if (over.value) return t('budget.over', { amount: displayMoney(-props.entry.remaining, props.currency) });
	return t('budget.left', { amount: displayMoney(props.entry.remaining, props.currency) });
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
		if (mode.value === 'percent') return parsePercent(draft.value) === null ? t('budget.percentRange') : null;

		const value = parseMoney(draft.value);
		if (value === null) return t('budget.amountNumber');
		return value < 0 ? t('budget.negative') : null;
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

/** A share always with a decimal, as Android writes one: 15 reads "15.0%". */
function shareLabel(share: number): string {
	return `${Number.isInteger(share) ? share.toFixed(1) : share}%`;
}

/** Spent against the plan (and the share it is), what has been spent without one, or an invitation to set one. */
const plannedLine = computed(() => {
	const { actual, planned, plannedPercent } = props.entry;
	if (planned > 0) {
		const line = `${displayMoney(actual, props.currency)} / ${displayMoney(planned, props.currency)}`;
		return plannedPercent !== null ? `${line} · ${shareLabel(plannedPercent)}` : line;
	}
	return actual > 0 ? displayMoney(actual, props.currency) : t('budget.setABudget');
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
	<!-- One row of a segmented list, as Android's ListItem draws it: the name, what has been
	     spent against the plan and what is left, then the share used as a ring. Pressing it opens the plan's dialog. -->
	<div>
		<button
			type="button"
			class="group-row state-layer focus-ring flex min-h-[88px] cursor-pointer items-center gap-4 py-3 pr-4 pl-4"
			@click="start"
		>
			<span class="min-w-0 flex-1">
				<span class="type-body-large block truncate">{{ entry.name }}</span>
				<span class="type-body-medium block text-on-surface-variant tabular">{{ plannedLine }}</span>
				<!-- The state in words beside the ring: status never rests on colour alone. -->
				<span class="type-body-medium block" :class="over ? 'text-error' : 'text-on-surface-variant'">{{ statusLabel }}</span>
			</span>

			<span class="relative grid size-12 shrink-0 place-items-center">
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
			</span>
		</button>

		<AlertDialog :open="editing" :title="entry.name" role="dialog" @close="editing = false">
			<form :id="`${fieldId}-form`" class="flex flex-col gap-3 sm:w-96" novalidate @submit.prevent="commit" @input="validation.onInput">
				<ConnectedButtonGroup
					:model-value="mode"
					:label="t('budget.budgetAs')"
					:options="[
						{ value: 'amount', label: currency },
						{ value: 'percent', label: '%' },
					]"
					:disabled="saving"
					@update:model-value="selectMode"
				/>

				<!-- Empty clears the plan, which is a change like any other; anything else has to be a share, or an amount that is not negative. -->
				<TextField
					:id="fieldId"
					v-model="draft"
					:label="t('common.budget')"
					inputmode="decimal"
					:placeholder="mode === 'percent' ? '0' : '0.00'"
					:prefix="mode === 'amount' ? currencySymbol(currency) : undefined"
					:disabled="saving"
					:error="fieldError(fieldId)"
					@blur="touch(fieldId)"
				/>
			</form>

			<template #actions>
				<button type="button" class="btn-text" :disabled="saving" @click="editing = false">{{ t('common.cancel') }}</button>
				<button type="submit" :form="`${fieldId}-form`" class="btn-primary" :disabled="saving || !validation.isValid.value">
					{{ t('common.save') }}
				</button>
			</template>
		</AlertDialog>
	</div>
</template>
