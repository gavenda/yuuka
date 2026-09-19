<script setup lang="ts">
import { BAR_SPEC, Chart, chartInk, monthSeries, SERIES_ONE } from '@/lib/chart';
import { formatLongDate } from '@/lib/dates';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { displayMoney, useAmountVisibility } from '@/lib/privacy';
import { useTheme } from '@/lib/theme';
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue';

const props = withDefaults(
	defineProps<{
		month: string;
		days: { date: string; amount: number }[];
		currency?: string;
	}>(),
	{ currency: DEFAULT_CURRENCY },
);

const { theme } = useTheme();
const { hidden } = useAmountVisibility();
const showTable = ref(false);
const canvas = ref<HTMLCanvasElement | null>(null);
// shallowRef: Chart instances are large and must not be made reactive.
const chart = shallowRef<Chart | null>(null);

const series = computed(() => monthSeries(props.month, props.days));

const total = computed(() => series.value.reduce((sum, entry) => sum + entry.amount, 0));
const spentDays = computed(() => series.value.filter((entry) => entry.amount > 0));
const hasData = computed(() => spentDays.value.length > 0);

function render(): void {
	chart.value?.destroy();
	chart.value = null;

	if (!canvas.value || !hasData.value || showTable.value) return;
	// Some environments (tests, print preview) hand back no 2D context.
	if (!canvas.value.getContext('2d')) return;

	const dark = theme.value === 'dark';
	const ink = chartInk(dark);
	const entries = series.value;

	chart.value = new Chart(canvas.value, {
		type: 'bar',
		data: {
			labels: entries.map((entry) => entry.day),
			datasets: [
				{
					label: 'Spent',
					data: entries.map((entry) => entry.amount / 100),
					backgroundColor: dark ? SERIES_ONE.dark : SERIES_ONE.light,
					...BAR_SPEC,
				},
			],
		},
		options: {
			responsive: true,
			maintainAspectRatio: false,
			// A single series: the heading already says what is plotted, so a
			// one-swatch legend would only restate it.
			plugins: {
				legend: { display: false },
				tooltip: {
					backgroundColor: ink.tooltipBackground,
					titleColor: ink.tooltipText,
					bodyColor: ink.tooltipMuted,
					padding: 10,
					displayColors: false,
					callbacks: {
						title: (items) => formatLongDate(entries[items[0].dataIndex].date),
						label: (item) => displayMoney(entries[item.dataIndex].amount, props.currency),
					},
				},
			},
			scales: {
				x: {
					grid: { display: false },
					border: { display: false },
					ticks: { color: ink.tick, font: { size: 10 }, maxRotation: 0, autoSkipPadding: 16 },
				},
				y: {
					beginAtZero: true,
					border: { display: false },
					grid: { color: ink.grid, lineWidth: 1 },
					ticks: {
						color: ink.tick,
						font: { size: 10 },
						maxTicksLimit: 4,
						callback: (value) => (hidden.value ? '' : Number(value).toLocaleString()),
					},
				},
			},
		},
	});
}

watch([series, () => theme.value, () => hidden.value, showTable, () => props.currency], render, { flush: 'post' });
watch(canvas, render, { flush: 'post' });

onBeforeUnmount(() => chart.value?.destroy());
</script>

<template>
	<section class="card p-5">
		<header class="mb-4 flex flex-wrap items-start justify-between gap-3">
			<div>
				<h2 class="text-sm font-medium text-on-surface">Spending by day</h2>
				<p class="mt-0.5 text-sm text-on-surface-variant">
					{{ displayMoney(total, currency) }} across {{ spentDays.length }}
					{{ spentDays.length === 1 ? 'day' : 'days' }}
				</p>
			</div>

			<button type="button" class="btn-text btn-sm" @click="showTable = !showTable">
				{{ showTable ? 'Show chart' : 'Show data' }}
			</button>
		</header>

		<p v-if="!hasData" class="py-10 text-center text-sm text-on-surface-variant">No spending recorded this month.</p>

		<div v-else-if="!showTable" class="relative h-48">
			<canvas ref="canvas" role="img" :aria-label="`Spending by day for ${month}`" />
		</div>

		<!-- The table is the non-visual route to the same numbers. -->
		<table v-else class="w-full text-sm">
			<thead>
				<tr class="border-b border-outline-variant text-left text-xs text-on-surface-variant">
					<th class="py-2 font-medium">Date</th>
					<th class="py-2 text-right font-medium">Spent</th>
				</tr>
			</thead>
			<tbody>
				<tr v-for="entry in spentDays" :key="entry.date" class="border-b border-outline-variant last:border-0">
					<td class="py-2 text-on-surface">{{ formatLongDate(entry.date) }}</td>
					<td class="tabular py-2 text-right text-on-surface">{{ displayMoney(entry.amount, currency) }}</td>
				</tr>
			</tbody>
		</table>
	</section>
</template>
