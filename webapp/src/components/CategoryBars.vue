<script setup lang="ts">
import { BAR_SPEC, Chart, chartInk, rankAndFold } from '@/lib/chart';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { displayMoney, useAmountVisibility } from '@/lib/privacy';
import { forMode } from '@/lib/palette';
import { useTheme } from '@/lib/theme';
import type { CategoryBreakdown } from '@/types';
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue';

const props = withDefaults(defineProps<{ title: string; entries: CategoryBreakdown[]; currency?: string; limit?: number }>(), {
	currency: DEFAULT_CURRENCY,
	limit: 8,
});

const { theme } = useTheme();
const { hidden } = useAmountVisibility();
const showTable = ref(false);
const canvas = ref<HTMLCanvasElement | null>(null);
const chart = shallowRef<Chart | null>(null);

const spent = computed(() => props.entries.filter((entry) => entry.actual > 0));

const rows = computed(() => rankAndFold(spent.value, props.limit));

const total = computed(() => spent.value.reduce((sum, entry) => sum + entry.actual, 0));
const share = (amount: number) => (total.value > 0 ? Math.round((amount / total.value) * 100) : 0);

/** Height follows the row count so bars keep a constant thickness. */
const chartHeight = computed(() => Math.max(96, rows.value.length * 34));

/**
 * Draws each value at the bar's tip. Three of the palette's light-mode hues sit
 * below 3:1 against a white card, so the figures stay readable as text rather
 * than relying on the fill.
 */
const valueLabels = {
	id: 'valueLabels',
	afterDatasetsDraw(instance: Chart) {
		const { ctx } = instance;
		const meta = instance.getDatasetMeta(0);
		const ink = chartInk(theme.value === 'dark');

		ctx.save();
		ctx.font = '11px system-ui, sans-serif';
		ctx.fillStyle = ink.tick;
		ctx.textBaseline = 'middle';

		meta.data.forEach((bar, index) => {
			const row = rows.value[index];
			if (!row) return;

			const text = `${displayMoney(row.actual, props.currency)} · ${share(row.actual)}%`;
			const x = bar.x + 8;
			// Drop the label rather than let it overflow the plot area.
			if (x + ctx.measureText(text).width > instance.chartArea.right) return;
			ctx.fillText(text, x, bar.y);
		});

		ctx.restore();
	},
};

function render(): void {
	chart.value?.destroy();
	chart.value = null;

	if (!canvas.value || !rows.value.length || showTable.value) return;
	if (!canvas.value.getContext('2d')) return;

	const dark = theme.value === 'dark';
	const ink = chartInk(dark);
	const current = rows.value;

	chart.value = new Chart(canvas.value, {
		type: 'bar',
		data: {
			labels: current.map((row) => row.name),
			datasets: [
				{
					label: 'Spent',
					data: current.map((row) => row.actual / 100),
					// Colour follows the category, never its rank, so filtering the
					// list never repaints the survivors.
					backgroundColor: current.map((row) => forMode(row.color, dark)),
					...BAR_SPEC,
				},
			],
		},
		options: {
			indexAxis: 'y',
			responsive: true,
			maintainAspectRatio: false,
			// Room on the right for the value labels drawn above.
			layout: { padding: { right: 96 } },
			plugins: {
				legend: { display: false },
				tooltip: {
					backgroundColor: ink.tooltipBackground,
					titleColor: ink.tooltipText,
					bodyColor: ink.tooltipMuted,
					padding: 10,
					displayColors: false,
					callbacks: {
						label: (item) => `${displayMoney(current[item.dataIndex].actual, props.currency)} · ${share(current[item.dataIndex].actual)}%`,
					},
				},
			},
			scales: {
				x: { display: false, beginAtZero: true },
				y: {
					grid: { display: false },
					border: { display: false },
					ticks: { color: ink.tick, font: { size: 12 }, crossAlign: 'far' },
				},
			},
		},
		plugins: [valueLabels],
	});
}

watch([rows, () => theme.value, () => hidden.value, showTable, () => props.currency], render, { flush: 'post' });
watch(canvas, render, { flush: 'post' });

onBeforeUnmount(() => chart.value?.destroy());
</script>

<template>
	<section class="card p-5">
		<header class="mb-4 flex flex-wrap items-start justify-between gap-3">
			<div>
				<h2 class="text-sm font-medium text-on-surface">{{ title }}</h2>
				<p class="mt-0.5 text-sm text-on-surface-variant">{{ displayMoney(total, currency) }} total</p>
			</div>

			<button v-if="rows.length" type="button" class="btn-text btn-sm" @click="showTable = !showTable">
				{{ showTable ? 'Show chart' : 'Show data' }}
			</button>
		</header>

		<p v-if="!rows.length" class="py-6 text-center text-sm text-on-surface-variant">Nothing recorded this month.</p>

		<div v-else-if="!showTable" class="relative" :style="{ height: `${chartHeight}px` }">
			<canvas ref="canvas" role="img" :aria-label="title" />
		</div>

		<table v-else class="w-full text-sm">
			<thead>
				<tr class="border-b border-outline-variant text-left text-xs text-on-surface-variant">
					<th class="py-2 font-medium">Category</th>
					<th class="py-2 text-right font-medium">Spent</th>
					<th class="py-2 text-right font-medium">Share</th>
				</tr>
			</thead>
			<tbody>
				<tr v-for="row in rows" :key="row.categoryId" class="border-b border-outline-variant last:border-0">
					<td class="py-2 text-on-surface">{{ row.name }}</td>
					<td class="tabular py-2 text-right text-on-surface">{{ displayMoney(row.actual, currency) }}</td>
					<td class="tabular py-2 text-right text-on-surface-variant">{{ share(row.actual) }}%</td>
				</tr>
			</tbody>
		</table>
	</section>
</template>
