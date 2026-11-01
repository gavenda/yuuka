<script setup lang="ts">
import PlanMeter from '@/components/PlanMeter.vue';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { STATUS } from '@/lib/palette';
import { displayMoney } from '@/lib/privacy';
import type { CategoryBreakdown } from '@/types';
import { t } from '@/i18n';

/**
 * The planned categories still within their plan but spending it faster than the month is passing
 * (`AheadOfPaceCard` in `DashboardCards.kt`): each one's share used, with the mark where the month has got to,
 * so the bar running past the mark is the warning.
 */
withDefaults(defineProps<{ entries: CategoryBreakdown[]; day: number; days: number; currency?: string }>(), { currency: DEFAULT_CURRENCY });
</script>

<template>
	<section class="card flex flex-col gap-4 p-5">
		<div>
			<h2 class="type-title-large">{{ t('dashboard.aheadOfPace') }}</h2>
			<p class="type-body-small">{{ t('dashboard.dayOf', { day, days }) }}</p>
		</div>

		<PlanMeter
			v-for="entry in entries"
			:key="entry.categoryId"
			:label="entry.name"
			:percent="Math.round((entry.actual / entry.planned) * 100)"
			:color="STATUS.warning"
			:marker="day / days"
			:lines="[
				t('dashboard.spentOfPlanned', { spent: displayMoney(entry.actual, currency), planned: displayMoney(entry.planned, currency) }),
			]"
		/>
	</section>
</template>
