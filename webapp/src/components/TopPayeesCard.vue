<script setup lang="ts">
import { DEFAULT_CURRENCY } from '@/lib/money';
import { displayMoney } from '@/lib/privacy';
import type { PayeeSpend } from '@/types';
import { computed } from 'vue';
import { t } from '@/i18n';

/**
 * Who the month's spending went to, the most first (`TopPayeesCard` in `DashboardCards.kt`): one series, so
 * every bar is the primary colour and the title names it. Each says its amount and how many times in words.
 */
const props = withDefaults(defineProps<{ payees: PayeeSpend[]; currency?: string }>(), { currency: DEFAULT_CURRENCY });

const largest = computed(() => props.payees.reduce((max, row) => Math.max(max, row.amount), 0));
const fraction = (amount: number) => (largest.value > 0 ? Math.min(1, Math.max(0, amount / largest.value)) : 0);
</script>

<template>
	<section class="card p-5">
		<h2 class="type-title-large">{{ t('dashboard.topPayees') }}</h2>

		<ul class="mt-3 flex flex-col gap-2.5">
			<li v-for="row in payees" :key="row.payee">
				<div class="type-body-small flex justify-between gap-3">
					<span class="min-w-0 truncate">{{ row.payee }}</span>
					<span class="tabular shrink-0">{{
						t('dashboard.payeeTimes', { amount: displayMoney(row.amount, currency), count: row.count }, row.count)
					}}</span>
				</div>

				<div class="mt-1 flex h-2 gap-1" aria-hidden="true">
					<div
						v-if="fraction(row.amount) > 0"
						class="h-full min-w-2 rounded-full bg-primary"
						:style="{ width: `${fraction(row.amount) * 100}%` }"
					/>
					<div v-if="fraction(row.amount) < 1" class="h-full min-w-0 flex-1 rounded-full bg-secondary-container" />
				</div>
			</li>
		</ul>
	</section>
</template>
