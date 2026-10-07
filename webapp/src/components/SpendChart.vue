<script setup lang="ts">
import { axisScale, compactAmount, dailyAverage, monthSeries, scallopPath } from '@/lib/chart';
import { formatLongDate } from '@/lib/dates';
import { currencySymbol, DEFAULT_CURRENCY } from '@/lib/money';
import { displayMoney, useAmountVisibility } from '@/lib/privacy';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { t } from '@/i18n';

const props = withDefaults(
	defineProps<{
		month: string;
		days: { date: string; amount: number }[];
		currency?: string;
	}>(),
	{ currency: DEFAULT_CURRENCY },
);

/*
 * The geometry, in SVG units (pixels). It is the Android chart's (`BarCharts.kt`): wide pill bars with the
 * day beneath each, a value axis down the right, and room above the plot so the top tick is not cut off.
 */
const BAR = 28;
const GAP = 6;
const PAD = 4;
const TOP = 8;
const PLOT = 168;
const LABEL = 24;
const AXIS = 44;
const BADGE_INSET = 3;
const BADGE = BAR - 2 * BADGE_INSET;
/** How close an axis figure may sit to the average's before the average's alone is drawn. */
const AXIS_CLEARANCE = 14;
/** Roughly half a tooltip's width, so its centre can be kept clear of either edge. */
const TOOLTIP_HALF = 64;
const DIMMED = 0.4;

const { hidden } = useAmountVisibility();
const scroller = ref<HTMLElement | null>(null);
const scrollLeft = ref(0);
const viewportWidth = ref(0);
/** A day being pointed at with a mouse, and one picked by tapping. Either names its day in the tooltip. */
const hovered = ref<number | null>(null);
const pinned = ref<number | null>(null);

const series = computed(() => monthSeries(props.month, props.days));

const total = computed(() => series.value.reduce((sum, entry) => sum + entry.amount, 0));
const spentDays = computed(() => series.value.filter((entry) => entry.amount > 0));
const hasData = computed(() => spentDays.value.length > 0);

const scale = computed(() => axisScale(Math.max(0, ...series.value.map((entry) => entry.amount))));
const average = computed(() => dailyAverage(series.value));
const glyph = computed(() => currencySymbol(props.currency));
/** A one-character symbol has room to be read; a three-letter code has to shrink to fit the badge. */
const glyphSize = computed(() => (glyph.value.length === 1 ? 13 : glyph.value.length === 2 ? 10 : 8));

const contentWidth = computed(() => PAD * 2 + series.value.length * BAR + (series.value.length - 1) * GAP);
const svgHeight = TOP + PLOT + LABEL;

/** The height up the plot of a value on the axis. */
function yOf(value: number): number {
	return TOP + PLOT * (1 - value / scale.value.top);
}

/** Every bar, placed. A bar is never shorter than it is wide (so it stays a pill), nor than the badge it holds. */
const bars = computed(() =>
	series.value.map((entry, index) => {
		const highlighted = average.value !== null && entry.amount > 0 && entry.amount >= average.value;
		const height = entry.amount <= 0 ? BAR : Math.max((PLOT * entry.amount) / scale.value.top, highlighted ? BADGE + 2 * BADGE_INSET : BAR);
		const x = PAD + index * (BAR + GAP);

		return {
			...entry,
			index,
			x,
			height,
			y: TOP + PLOT - height,
			highlighted,
			fill: entry.amount <= 0 ? 'fill-on-secondary-container/16' : highlighted ? 'fill-tertiary' : 'fill-primary',
			badge: highlighted ? scallopPath(x + BAR / 2, TOP + PLOT - height + BADGE_INSET + BADGE / 2, BADGE / 2) : '',
		};
	}),
);

const axisTicks = computed(() => {
	const clashing = average.value === null ? null : yOf(average.value);
	// The average's own label wins where the two would sit on top of each other.
	return scale.value.ticks.filter((tick) => clashing === null || Math.abs(yOf(tick) - clashing) >= AXIS_CLEARANCE);
});

function axisLabel(minor: number): string {
	return hidden.value ? '••' : compactAmount(minor);
}

const shown = computed(() => hovered.value ?? pinned.value);

/** With a day named the rest step back, so the one being read stands out. */
function opacityOf(index: number): number {
	return shown.value === null || shown.value === index ? 1 : DIMMED;
}

/** Floats above the day's bar, against the page rather than the plot, so it is not cut off by the plot's edge. */
const tooltip = computed(() => {
	const bar = shown.value === null ? undefined : bars.value[shown.value];
	if (!bar) return null;

	const centre = bar.x + BAR / 2 - scrollLeft.value;
	const width = viewportWidth.value;
	// Its bar has been scrolled out of view.
	if (width > 0 && (centre < 0 || centre > width)) return null;

	const left = width > 0 ? Math.min(Math.max(centre, TOOLTIP_HALF), Math.max(width - TOOLTIP_HALF, TOOLTIP_HALF)) : centre;
	return { bar, style: { left: `${left}px`, top: `${bar.y - 8}px` } };
});

function onTap(index: number): void {
	pinned.value = pinned.value === index ? null : index;
}

// A touch has no hover: it taps, and a mouse can do either.
function onEnter(event: PointerEvent, index: number): void {
	if (event.pointerType === 'mouse') hovered.value = index;
}

function onLeave(event: PointerEvent): void {
	if (event.pointerType === 'mouse') hovered.value = null;
}

// Something that scrolls under the pointer is not something to keep a tooltip pinned to.
function onScroll(): void {
	scrollLeft.value = scroller.value?.scrollLeft ?? 0;
	pinned.value = null;
	hovered.value = null;
}

/** Starts on the latest day with spending, its bar against the right edge. */
function scrollToLatest(): void {
	const element = scroller.value;
	if (!element) return;

	const last = series.value.reduce((found, entry, index) => (entry.amount > 0 ? index : found), -1);
	if (last < 0) return;

	element.scrollLeft = Math.max(0, PAD + last * (BAR + GAP) + BAR + PAD - element.clientWidth);
	scrollLeft.value = element.scrollLeft;
}

let observer: ResizeObserver | undefined;

watch(
	scroller,
	(element) => {
		observer?.disconnect();
		observer = undefined;
		if (!element) return;

		viewportWidth.value = element.clientWidth;
		if (typeof ResizeObserver !== 'undefined') {
			observer = new ResizeObserver(() => (viewportWidth.value = element.clientWidth));
			observer.observe(element);
		}
		scrollToLatest();
	},
	{ flush: 'post' },
);

// A different month is a different chart, so the selection does not carry over to it.
watch(
	() => props.month,
	() => {
		hovered.value = null;
		pinned.value = null;
		scrollToLatest();
	},
	{ flush: 'post' },
);

onBeforeUnmount(() => observer?.disconnect());
</script>

<template>
	<!-- On a secondary container, as on Android: the one card on the dashboard that is a picture rather than a figure. -->
	<section class="card-secondary p-5">
		<h2 class="type-title-large">{{ t('dashboard.chart.title') }}</h2>

		<p v-if="!hasData" class="type-body-small">{{ t('dashboard.chart.empty') }}</p>

		<template v-else>
			<p class="type-body-small">
				{{ t('dashboard.chart.summary', { amount: displayMoney(total, currency) }, spentDays.length) }}
			</p>

			<div class="relative mt-3 flex items-start">
				<!-- A month is too many wide bars for a phone, so the plot scrolls sideways while the axis stays put. -->
				<div
					ref="scroller"
					class="focus-ring min-w-0 flex-1 overflow-x-auto [scrollbar-width:none]"
					tabindex="0"
					:aria-label="t('dashboard.chart.scrolls')"
					@scroll.passive="onScroll"
				>
					<svg
						:width="contentWidth"
						:height="svgHeight"
						class="block max-w-none"
						role="img"
						:aria-label="t('dashboard.chart.forMonth', { month })"
					>
						<!-- Behind the bars, so a bar covers the line where it crosses. -->
						<line
							v-if="average !== null"
							data-average
							x1="0"
							:x2="Math.max(contentWidth, viewportWidth)"
							:y1="yOf(average)"
							:y2="yOf(average)"
							class="stroke-primary"
							stroke-width="1.5"
						/>

						<g v-for="bar in bars" :key="bar.date" :opacity="opacityOf(bar.index)">
							<rect data-bar :x="bar.x" :y="bar.y" :width="BAR" :height="bar.height" :rx="BAR / 2" :class="bar.fill" />

							<template v-if="bar.highlighted">
								<path data-badge :d="bar.badge" class="fill-tertiary-container" />
								<text
									:x="bar.x + BAR / 2"
									:y="bar.y + BADGE_INSET + BADGE / 2"
									text-anchor="middle"
									dominant-baseline="central"
									:font-size="glyphSize"
									class="fill-on-tertiary-container font-medium"
								>
									{{ glyph }}
								</text>
							</template>

							<text
								:x="bar.x + BAR / 2"
								:y="TOP + PLOT + 14"
								text-anchor="middle"
								dominant-baseline="central"
								class="type-label-small"
								:class="shown === bar.index ? 'fill-on-secondary-container' : 'fill-on-secondary-container/72'"
							>
								{{ bar.day }}
							</text>
						</g>

						<!-- One target per day, gaps included, so a finger never lands between two bars. -->
						<rect
							v-for="bar in bars"
							:key="`hit-${bar.date}`"
							data-hit
							:x="bar.x - GAP / 2"
							y="0"
							:width="BAR + GAP"
							:height="svgHeight"
							fill="transparent"
							class="cursor-pointer"
							@click="onTap(bar.index)"
							@pointerenter="onEnter($event, bar.index)"
							@pointerleave="onLeave($event)"
						/>
					</svg>
				</div>

				<svg :width="AXIS" :height="svgHeight" class="shrink-0" aria-hidden="true">
					<text
						v-for="tick in axisTicks"
						:key="tick"
						x="8"
						:y="yOf(tick)"
						dominant-baseline="central"
						class="type-label-small fill-on-secondary-container/72"
					>
						{{ axisLabel(tick) }}
					</text>
					<text
						v-if="average !== null"
						data-average-label
						x="8"
						:y="yOf(average)"
						dominant-baseline="central"
						class="type-label-small fill-primary"
					>
						{{ axisLabel(average) }}
					</text>
				</svg>

				<div
					v-if="tooltip"
					data-tooltip
					class="pointer-events-none absolute z-10 -translate-x-1/2 -translate-y-full rounded-sm bg-surface px-3 py-2 whitespace-nowrap text-on-surface shadow-elevation-2"
					:style="tooltip.style"
					aria-hidden="true"
				>
					<p class="type-label-large">{{ formatLongDate(tooltip.bar.date) }}</p>
					<p class="type-body-small">{{ displayMoney(tooltip.bar.amount, currency) }}</p>
				</div>
			</div>

			<ul class="type-label-small mt-3 flex flex-wrap items-center gap-x-5 gap-y-1">
				<li class="flex items-center gap-1.5">
					<span class="inline-block h-0.5 w-4 bg-primary" aria-hidden="true" /> {{ t('dashboard.chart.dailyAverage') }}
				</li>
				<li class="flex items-center gap-1.5">
					<span class="inline-block size-2.5 rounded-full bg-tertiary" aria-hidden="true" /> {{ t('dashboard.chart.atOrAbove') }}
				</li>
			</ul>

			<!-- The table is the non-visual route to the same numbers. -->
			<table class="sr-only">
				<caption>
					{{
						t('dashboard.chart.title')
					}}
				</caption>
				<thead>
					<tr>
						<th scope="col">{{ t('dashboard.chart.date') }}</th>
						<th scope="col">{{ t('common.spent') }}</th>
					</tr>
				</thead>
				<tbody>
					<tr v-for="entry in spentDays" :key="entry.date">
						<td>{{ formatLongDate(entry.date) }}</td>
						<td>{{ displayMoney(entry.amount, currency) }}</td>
					</tr>
				</tbody>
			</table>
		</template>
	</section>
</template>
