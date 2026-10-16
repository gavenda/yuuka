<script setup lang="ts">
import { axisScale, compactAmount, shortMonth } from '@/lib/chart';
import { useElementWidth } from '@/lib/elementWidth';
import { formatMonth } from '@/lib/dates';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { displayMoney, useAmountVisibility } from '@/lib/privacy';
import type { TrendMonth } from '@/types';
import { computed, ref, watch } from 'vue';
import { t } from '@/i18n';

/**
 * Income and spending month by month (`IncomeSpendChart` in `TrendCharts.kt`): a pair of bars per month on one
 * axis, income in the inflow colour and spending in the neutral the figures use for a routine outflow, 2px apart.
 * Two series, so a legend names both; pointing at or tapping a month names its figures in a tooltip. The same
 * figures sit beside it as a visually hidden table.
 */
const props = withDefaults(defineProps<{ months: TrendMonth[]; currency?: string }>(), { currency: DEFAULT_CURRENCY });

/* The geometry, in pixels, as on Android. */
const TOP = 8;
const PLOT = 160;
const LABEL = 24;
const AXIS = 44;
const PAIR_GAP = 2;
const MAX_BAR = 20;
const DIMMED = 0.4;
const TOOLTIP_HALF = 72;

const { hidden } = useAmountVisibility();
const plot = ref<HTMLElement | null>(null);
const width = useElementWidth(plot);
const hovered = ref<number | null>(null);
const pinned = ref<number | null>(null);
const shown = computed(() => hovered.value ?? pinned.value);

const scale = computed(() => axisScale(Math.max(0, ...props.months.flatMap((entry) => [entry.income, entry.expenses]))));
const slot = computed(() => (props.months.length > 0 ? width.value / props.months.length : 0));
const barWidth = computed(() => Math.max(4, Math.min(MAX_BAR, (slot.value - 12 - PAIR_GAP) / 2)));
const svgHeight = TOP + PLOT + LABEL;

function yOf(value: number): number {
	return TOP + PLOT * (1 - value / scale.value.top);
}

function heightOf(value: number): number {
	return value > 0 ? Math.max(2, (PLOT * value) / scale.value.top) : 0;
}

const groups = computed(() =>
	props.months.map((entry, index) => {
		const centre = slot.value * index + slot.value / 2;
		return { ...entry, index, centre, incomeX: centre - PAIR_GAP / 2 - barWidth.value, spentX: centre + PAIR_GAP / 2 };
	}),
);

const kept = computed(() => props.months.reduce((sum, entry) => sum + entry.net, 0));

function axisLabel(minor: number): string {
	return hidden.value ? '••' : compactAmount(minor);
}

const tooltip = computed(() => {
	const group = shown.value === null ? undefined : groups.value[shown.value];
	if (!group) return null;
	const left = Math.min(Math.max(group.centre, TOOLTIP_HALF), Math.max(width.value - TOOLTIP_HALF, TOOLTIP_HALF));
	const top = yOf(Math.max(group.income, group.expenses)) - 8;
	return { group, style: { left: `${left}px`, top: `${top}px` } };
});

function onTap(index: number): void {
	pinned.value = pinned.value === index ? null : index;
}

function onEnter(event: PointerEvent, index: number): void {
	if (event.pointerType === 'mouse') hovered.value = index;
}

function onLeave(event: PointerEvent): void {
	if (event.pointerType === 'mouse') hovered.value = null;
}

// Other months are a different chart, so a selection does not carry over to them.
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
		<h2 class="type-title-large">{{ t('dashboard.trend.incomeSpend') }}</h2>
		<p class="type-body-small">
			{{
				t(
					kept >= 0 ? 'dashboard.trend.kept' : 'dashboard.trend.overspent',
					{ amount: displayMoney(Math.abs(kept), currency) },
					months.length,
				)
			}}
		</p>

		<div class="relative mt-3 flex items-start">
			<div ref="plot" class="min-w-0 flex-1">
				<svg :width="width" :height="svgHeight" class="block" role="img" :aria-label="t('dashboard.trend.incomeSpend')">
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

					<g v-for="group in groups" :key="group.month" :opacity="shown === null || shown === group.index ? 1 : DIMMED">
						<rect
							v-if="group.income > 0"
							:x="group.incomeX"
							:y="TOP + PLOT - heightOf(group.income)"
							:width="barWidth"
							:height="heightOf(group.income)"
							:rx="Math.min(4, barWidth / 2)"
							class="fill-primary"
						/>
						<rect
							v-if="group.expenses > 0"
							:x="group.spentX"
							:y="TOP + PLOT - heightOf(group.expenses)"
							:width="barWidth"
							:height="heightOf(group.expenses)"
							:rx="Math.min(4, barWidth / 2)"
							class="fill-on-surface-variant"
						/>
						<text
							:x="group.centre"
							:y="TOP + PLOT + 14"
							text-anchor="middle"
							dominant-baseline="central"
							class="type-label-small"
							:class="shown === group.index ? 'fill-on-surface' : 'fill-on-surface-variant'"
						>
							{{ shortMonth(group.month) }}
						</text>
					</g>

					<!-- One target per month, its whole slot, so a finger never lands between two. -->
					<rect
						v-for="group in groups"
						:key="`hit-${group.month}`"
						:x="slot * group.index"
						y="0"
						:width="slot"
						:height="svgHeight"
						fill="transparent"
						class="cursor-pointer"
						@click="onTap(group.index)"
						@pointerenter="onEnter($event, group.index)"
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
				<p class="type-label-large">{{ formatMonth(tooltip.group.month) }}</p>
				<p class="type-body-small">{{ t('common.income') }}: {{ displayMoney(tooltip.group.income, currency) }}</p>
				<p class="type-body-small">{{ t('common.spent') }}: {{ displayMoney(tooltip.group.expenses, currency) }}</p>
				<p class="type-body-small">{{ t('dashboard.trend.net') }}: {{ displayMoney(tooltip.group.net, currency) }}</p>
			</div>
		</div>

		<ul class="type-label-small mt-3 flex flex-wrap items-center gap-x-5 gap-y-1">
			<li class="flex items-center gap-1.5">
				<span class="inline-block size-2.5 rounded-xs bg-primary" aria-hidden="true" /> {{ t('common.income') }}
			</li>
			<li class="flex items-center gap-1.5">
				<span class="inline-block size-2.5 rounded-xs bg-on-surface-variant" aria-hidden="true" /> {{ t('common.spent') }}
			</li>
		</ul>

		<!-- The table is the non-visual route to the same numbers. -->
		<table class="sr-only">
			<caption>
				{{
					t('dashboard.trend.incomeSpend')
				}}
			</caption>
			<thead>
				<tr>
					<th scope="col">{{ t('dashboard.trend.month') }}</th>
					<th scope="col">{{ t('common.income') }}</th>
					<th scope="col">{{ t('common.spent') }}</th>
					<th scope="col">{{ t('dashboard.trend.net') }}</th>
				</tr>
			</thead>
			<tbody>
				<tr v-for="entry in months" :key="entry.month">
					<td>{{ formatMonth(entry.month) }}</td>
					<td>{{ displayMoney(entry.income, currency) }}</td>
					<td>{{ displayMoney(entry.expenses, currency) }}</td>
					<td>{{ displayMoney(entry.net, currency) }}</td>
				</tr>
			</tbody>
		</table>
	</section>
</template>
