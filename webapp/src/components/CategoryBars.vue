<script setup lang="ts">
import { rankAndFold } from '@/lib/chart';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { forMode } from '@/lib/palette';
import { displayMoney } from '@/lib/privacy';
import { useTheme } from '@/lib/theme';
import type { CategoryBreakdown } from '@/types';
import { computed } from 'vue';

const props = withDefaults(defineProps<{ title: string; entries: CategoryBreakdown[]; currency?: string; limit?: number }>(), {
	currency: DEFAULT_CURRENCY,
	limit: 8,
});

const { theme } = useTheme();

const rows = computed(() => rankAndFold(props.entries, props.limit));

const total = computed(() => rows.value.reduce((sum, entry) => sum + entry.actual, 0));
const largest = computed(() => rows.value.reduce((max, entry) => Math.max(max, entry.actual), 0));

const share = (amount: number) => (total.value > 0 ? Math.floor((amount / total.value) * 100) : 0);
const fraction = (amount: number) => (largest.value > 0 ? Math.min(1, Math.max(0, amount / largest.value)) : 0);
</script>

<template>
	<section class="card p-5">
		<h2 class="type-title-small text-on-surface">{{ title }}</h2>

		<p v-if="!rows.length" class="mt-3 text-xs text-on-surface-variant">Nothing recorded this month.</p>

		<ul v-else class="mt-3 space-y-2.5">
			<li v-for="row in rows" :key="row.categoryId">
				<div class="flex justify-between gap-3 text-xs">
					<span class="min-w-0 truncate text-on-surface">{{ row.name }}</span>
					<span class="tabular shrink-0 text-on-surface-variant">{{ displayMoney(row.actual, currency) }} · {{ share(row.actual) }}%</span>
				</div>

				<div class="mt-1 h-2 overflow-hidden rounded-full bg-surface-variant">
					<div
						class="h-full rounded-full"
						:style="{ width: `${fraction(row.actual) * 100}%`, backgroundColor: forMode(row.color, theme === 'dark') }"
					/>
				</div>
			</li>
		</ul>
	</section>
</template>
