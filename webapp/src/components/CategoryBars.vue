<script setup lang="ts">
import { rankAndFold } from '@/lib/chart';
import { useHarmonised } from '@/lib/harmonise';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { displayMoney } from '@/lib/privacy';
import type { CategoryBreakdown } from '@/types';
import { computed } from 'vue';
import { t } from '@/i18n';

/**
 * A horizontal ranked bar per category (`CategoryBarList` in `BarCharts.kt`), each carrying its own value
 * label — colour never carries the figure alone. A bar is Material's linear progress indicator: the
 * category's colour as far as its share of the largest, then a gap, then the rest of the track.
 */
const props = withDefaults(defineProps<{ title: string; entries: CategoryBreakdown[]; currency?: string; limit?: number }>(), {
	currency: DEFAULT_CURRENCY,
	limit: 8,
});

const harmonised = useHarmonised();

const rows = computed(() => rankAndFold(props.entries, props.limit));

const total = computed(() => rows.value.reduce((sum, entry) => sum + entry.actual, 0));
const largest = computed(() => rows.value.reduce((max, entry) => Math.max(max, entry.actual), 0));

const share = (amount: number) => (total.value > 0 ? Math.floor((amount / total.value) * 100) : 0);
const fraction = (amount: number) => (largest.value > 0 ? Math.min(1, Math.max(0, amount / largest.value)) : 0);
</script>

<template>
	<section class="card p-5">
		<h2 class="type-title-large">{{ title }}</h2>

		<p v-if="!rows.length" class="type-body-small mt-3">{{ t('dashboard.nothingRecorded') }}</p>

		<ul v-else class="mt-3 flex flex-col gap-2.5">
			<li v-for="row in rows" :key="row.categoryId">
				<div class="type-body-small flex justify-between gap-3">
					<span class="min-w-0 truncate">{{ row.name }}</span>
					<span class="tabular shrink-0">{{ displayMoney(row.actual, currency) }} · {{ share(row.actual) }}%</span>
				</div>

				<div class="mt-1 flex h-2 gap-1">
					<div
						v-if="fraction(row.actual) > 0"
						class="h-full min-w-2 rounded-full"
						:style="{ width: `${fraction(row.actual) * 100}%`, backgroundColor: harmonised(row.color) ?? 'var(--color-primary)' }"
					/>
					<div v-if="fraction(row.actual) < 1" class="h-full min-w-0 flex-1 rounded-full bg-secondary-container" />
				</div>
			</li>
		</ul>
	</section>
</template>
