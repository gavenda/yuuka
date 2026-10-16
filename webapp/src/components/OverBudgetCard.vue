<script setup lang="ts">
import { useHarmonised } from '@/lib/harmonise';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { displayMoney } from '@/lib/privacy';
import type { CategoryBreakdown } from '@/types';
import { t } from '@/i18n';

/**
 * The categories behind the "Over budget" figure, the furthest over first (`OverBudgetCard` in
 * `DashboardCards.kt`). Each bar is what was spent: the category's colour as far as the plan reached, then
 * the overrun in the error colour, the words beside it saying the same so the colour is never alone.
 */
withDefaults(defineProps<{ entries: CategoryBreakdown[]; currency?: string }>(), { currency: DEFAULT_CURRENCY });

const harmonised = useHarmonised();
</script>

<template>
	<section class="card p-5">
		<h2 class="type-title-large">{{ t('dashboard.overBudget') }}</h2>

		<ul class="mt-3 flex flex-col gap-3">
			<li v-for="entry in entries" :key="entry.categoryId">
				<div class="type-body-small flex justify-between gap-3">
					<span class="min-w-0 truncate">{{ entry.name }}</span>
					<span class="tabular shrink-0 text-error">{{ t('budget.over', { amount: displayMoney(-entry.remaining, currency) }) }}</span>
				</div>

				<div class="mt-1 flex h-2 gap-1" aria-hidden="true">
					<div
						class="h-full rounded-full"
						:style="{ flexGrow: entry.planned, flexBasis: 0, backgroundColor: harmonised(entry.color) ?? 'var(--color-primary)' }"
					/>
					<div class="h-full min-w-2 rounded-full bg-error" :style="{ flexGrow: entry.actual - entry.planned, flexBasis: 0 }" />
				</div>

				<p class="type-body-small tabular mt-1 text-on-surface-variant">
					{{
						t('dashboard.spentOfPlanned', { spent: displayMoney(entry.actual, currency), planned: displayMoney(entry.planned, currency) })
					}}
				</p>
			</li>
		</ul>
	</section>
</template>
