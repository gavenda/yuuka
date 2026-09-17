<script setup lang="ts">
import { percentOf, DEFAULT_CURRENCY } from '@/lib/money';
import { displayMoney } from '@/lib/privacy';
import { budgetStatus, STATUS } from '@/lib/palette';
import type { CategoryBreakdown } from '@/types';
import { computed } from 'vue';

const props = withDefaults(defineProps<{ entry: CategoryBreakdown; currency?: string }>(), { currency: DEFAULT_CURRENCY });

const status = computed(() => budgetStatus(props.entry.actual, props.entry.planned));
const fill = computed(() => STATUS[status.value]);
const percent = computed(() => percentOf(props.entry.actual, props.entry.planned));
const over = computed(() => props.entry.planned > 0 && props.entry.remaining < 0);

/** Status is never carried by colour alone — each state ships a word and a glyph. */
const statusLabel = computed(() => {
	if (props.entry.planned <= 0) return props.entry.actual > 0 ? 'Unbudgeted' : 'No budget set';
	if (over.value) return `${displayMoney(-props.entry.remaining, props.currency)} over`;
	return `${displayMoney(props.entry.remaining, props.currency)} left`;
});
</script>

<template>
	<div class="py-3">
		<div class="flex items-baseline justify-between gap-3">
			<div class="flex min-w-0 items-center gap-2">
				<span class="h-2.5 w-2.5 shrink-0 rounded-full" :style="{ backgroundColor: entry.color }" />
				<span class="truncate text-sm font-medium text-slate-900 dark:text-slate-100">{{ entry.name }}</span>
			</div>

			<span class="tabular shrink-0 text-sm text-slate-600 dark:text-slate-400">
				{{ displayMoney(entry.actual, currency) }}
				<span v-if="entry.planned > 0" class="text-slate-400 dark:text-slate-500">/ {{ displayMoney(entry.planned, currency) }}</span>
			</span>
		</div>

		<!-- Track is a light step of the same ramp so the state reads across the whole bar. -->
		<div class="mt-2 h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800">
			<div class="h-full rounded-full transition-[width]" :style="{ width: `${percent}%`, backgroundColor: fill }" />
		</div>

		<!-- Subcategories are already inside the parent's total; listing them says
		     what that total is made of. -->
		<ul v-if="entry.children.some((child) => child.actual > 0)" class="mt-1.5 space-y-0.5 pl-4">
			<li v-for="child in entry.children.filter((c) => c.actual > 0)" :key="child.categoryId" class="flex justify-between gap-2 text-xs">
				<span class="truncate text-slate-500 dark:text-slate-400">{{ child.name }}</span>
				<span class="tabular shrink-0 text-slate-500 dark:text-slate-400">{{ displayMoney(child.actual, currency) }}</span>
			</li>
		</ul>

		<p
			class="mt-1.5 flex items-center gap-1.5 text-xs"
			:class="over ? 'text-rose-700 dark:text-rose-400' : 'text-slate-500 dark:text-slate-400'"
		>
			<svg v-if="over" viewBox="0 0 16 16" class="h-3.5 w-3.5 shrink-0" fill="currentColor" aria-hidden="true">
				<path d="M8 1.5l6.5 12h-13L8 1.5zM8 6v4M8 11.5v1" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" />
			</svg>
			{{ statusLabel }}
		</p>
	</div>
</template>
