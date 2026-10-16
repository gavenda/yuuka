<script setup lang="ts">
import { axisRange, compactAmount, shortMonth } from '@/lib/chart';
import { useElementWidth } from '@/lib/elementWidth';
import { formatMonth } from '@/lib/dates';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { displayMoney, useAmountVisibility } from '@/lib/privacy';
import type { TrendMonth } from '@/types';
import { computed, ref, watch } from 'vue';
import { t } from '@/i18n';

/**
 * Net worth at each month's end (`NetWorthChart` in `TrendCharts.kt`): one 2px line in the primary colour with
 * an 8px marker per month ringed in the card's colour, on an axis that need not start at zero (`axisRange`).
 * One series, so the title names it and there is no legend. Pointing at or tapping a month stands a crosshair
 * on it and names it in a tooltip; the same figures sit beside it as a visually hidden table.
 */
const props = withDefaults(defineProps<{ months: TrendMonth[]; currency?: string }>(), { currency: DEFAULT_CURRENCY });

/* The geometry, in pixels, as on Android. */
const TOP = 8;
const PLOT = 160;
const LABEL = 24;
const AXIS = 44;
const PAD = 8;
const MARKER = 4;
/** The least room a month's label needs, so a narrow plot labels every other one. */
const LABEL_ROOM = 36;
const TOOLTIP_HALF = 72;

const { hidden } = useAmountVisibility();
const plot = ref<HTMLElement | null>(null);
const width = useElementWidth(plot);
const hovered = ref<number | null>(null);
const pinned = ref<number | null>(null);
const shown = computed(() => hovered.value ?? pinned.value);

const scale = computed(() => {
	const values = props.months.map((entry) => entry.netWorth);
	return axisRange(Math.min(...values), Math.max(...values));
});
const svgHeight = TOP + PLOT + LABEL;
const spacing = computed(() => (props.months.length > 1 ? (width.value - 2 * PAD) / (props.months.length - 1) : 0));
const labelEvery = computed(() => Math.max(1, Math.ceil(LABEL_ROOM / Math.max(1, spacing.value))));

function yOf(value: number): number {
	return TOP + PLOT * (1 - (value - scale.value.bottom) / (scale.value.top - scale.value.bottom));
}

const points = computed(() =>
	props.months.map((entry, index) => ({ ...entry, index, x: PAD + index * spacing.value, y: yOf(entry.netWorth) })),
);
const path = computed(() =>
	points.value.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(' '),
);

/** How far net worth moved over the months shown, in words. */
const caption = computed(() => {
	const first = props.months[0];
	const last = props.months.at(-1);
	if (!first || !last) return '';
	const change = last.netWorth - first.netWorth;
	const since = formatMonth(first.month);
	if (change === 0) return t('dashboard.trend.level', { month: since });
	return t(change > 0 ? 'dashboard.trend.up' : 'dashboard.trend.down', {
		amount: displayMoney(Math.abs(change), props.currency),
		month: since,
	});
});

function axisLabel(minor: number): string {
	return hidden.value ? '••' : compactAmount(minor);
}

const tooltip = computed(() => {
	const point = shown.value === null ? undefined : points.value[shown.value];
	if (!point) return null;
	const left = Math.min(Math.max(point.x, TOOLTIP_HALF), Math.max(width.value - TOOLTIP_HALF, TOOLTIP_HALF));
	return { point, style: { left: `${left}px`, top: `${point.y - 12}px` } };
});

/** Each month's target runs halfway to its neighbours, so a finger always lands on the nearest. */
function hitOf(index: number): { x: number; width: number } {
	const half = spacing.value / 2;
	const x = index === 0 ? 0 : PAD + index * spacing.value - half;
	const end = index === props.months.length - 1 ? width.value : PAD + index * spacing.value + half;
	return { x, width: Math.max(0, end - x) };
}

function onTap(index: number): void {
	pinned.value = pinned.value === index ? null : index;
}

function onEnter(event: PointerEvent, index: number): void {
	if (event.pointerType === 'mouse') hovered.value = index;
}

function onLeave(event: PointerEvent): void {
	if (event.pointerType === 'mouse') hovered.value = null;
}

watch(
	() => props.months.at(-1)?.month,
	() => {
		hovered.value = null;
		pinned.value = null;
	},
);
</script>

<template>
	<section class="card p-5">
		<h2 class="type-title-large">{{ t('dashboard.trend.netWorth') }}</h2>
		<p class="type-body-small">{{ caption }}</p>

		<div class="relative mt-3 flex items-start">
			<div ref="plot" class="min-w-0 flex-1">
				<svg :width="width" :height="svgHeight" class="block" role="img" :aria-label="t('dashboard.trend.netWorth')">
					<line
						v-for="tick in scale.ticks"
						:key="`grid-${tick}`"
						x1="0"
						:x2="width"
						:y1="yOf(tick)"
						:y2="yOf(tick)"
						class="stroke-outline-variant"
						stroke-width="1"
					/>

					<line
						v-if="tooltip"
						:x1="tooltip.point.x"
						:x2="tooltip.point.x"
						:y1="TOP"
						:y2="TOP + PLOT"
						class="stroke-on-surface-variant"
						stroke-width="1"
					/>

					<path :d="path" fill="none" class="stroke-primary" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />

					<circle
						v-for="point in points"
						:key="point.month"
						:cx="point.x"
						:cy="point.y"
						:r="shown === point.index ? MARKER + 2 : MARKER"
						class="fill-primary stroke-surface-container-highest"
						stroke-width="2"
					/>

					<template v-for="point in points" :key="`label-${point.month}`">
						<text
							v-if="(months.length - 1 - point.index) % labelEvery === 0"
							:x="point.x"
							:y="TOP + PLOT + 14"
							text-anchor="middle"
							dominant-baseline="central"
							class="type-label-small"
							:class="shown === point.index ? 'fill-on-surface' : 'fill-on-surface-variant'"
						>
							{{ shortMonth(point.month) }}
						</text>
					</template>

					<rect
						v-for="point in points"
						:key="`hit-${point.month}`"
						:x="hitOf(point.index).x"
						y="0"
						:width="hitOf(point.index).width"
						:height="svgHeight"
						fill="transparent"
						class="cursor-pointer"
						@click="onTap(point.index)"
						@pointerenter="onEnter($event, point.index)"
						@pointerleave="onLeave($event)"
					/>
				</svg>
			</div>

			<svg :width="AXIS" :height="svgHeight" class="shrink-0" aria-hidden="true">
				<text
					v-for="tick in scale.ticks"
					:key="tick"
					x="8"
					:y="yOf(tick)"
					dominant-baseline="central"
					class="type-label-small fill-on-surface-variant"
				>
					{{ axisLabel(tick) }}
				</text>
			</svg>

			<div
				v-if="tooltip"
				class="pointer-events-none absolute z-10 -translate-x-1/2 -translate-y-full rounded-sm bg-surface px-3 py-2 whitespace-nowrap text-on-surface shadow-elevation-2"
				:style="tooltip.style"
				aria-hidden="true"
			>
				<p class="type-label-large">{{ formatMonth(tooltip.point.month) }}</p>
				<p class="type-body-small">{{ displayMoney(tooltip.point.netWorth, currency) }}</p>
			</div>
		</div>

		<!-- The table is the non-visual route to the same numbers. -->
		<table class="sr-only">
			<caption>
				{{
					t('dashboard.trend.netWorth')
				}}
			</caption>
			<thead>
				<tr>
					<th scope="col">{{ t('dashboard.trend.month') }}</th>
					<th scope="col">{{ t('common.netWorth') }}</th>
				</tr>
			</thead>
			<tbody>
				<tr v-for="entry in months" :key="entry.month">
					<td>{{ formatMonth(entry.month) }}</td>
					<td>{{ displayMoney(entry.netWorth, currency) }}</td>
				</tr>
			</tbody>
		</table>
	</section>
</template>
