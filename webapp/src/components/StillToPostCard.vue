<script setup lang="ts">
import MoneyText from '@/components/MoneyText.vue';
import { formatLongDate } from '@/lib/dates';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { displayMoney } from '@/lib/privacy';
import type { Subscription } from '@/types';
import { computed } from 'vue';
import { t } from '@/i18n';

/**
 * The subscriptions still to post this month, the soonest first, and what they come to (`StillToPostCard` in
 * `DashboardCards.kt`) — signed like the amounts, in the display currency, added up as they stand like the
 * Subscriptions screen's monthly total.
 */
const props = withDefaults(defineProps<{ subscriptions: Subscription[]; currency?: string }>(), { currency: DEFAULT_CURRENCY });

const total = computed(() => props.subscriptions.reduce((sum, subscription) => sum + subscription.amount, 0));
</script>

<template>
	<section class="card p-5">
		<h2 class="type-title-large">{{ t('dashboard.stillToPost') }}</h2>
		<p class="type-body-small">
			{{ t('dashboard.stillToPostSummary', { amount: displayMoney(total, currency), count: subscriptions.length }, subscriptions.length) }}
		</p>

		<ul class="mt-2">
			<li v-for="subscription in subscriptions" :key="subscription.id" class="flex items-center gap-3 py-1.5">
				<div class="min-w-0 flex-1">
					<p class="type-body-medium truncate">{{ subscription.payee }}</p>
					<p class="type-body-small text-on-surface-variant">{{ formatLongDate(subscription.nextRunOn) }}</p>
				</div>
				<MoneyText :amount="subscription.amount" :currency="currency" tone="signed-alert" class="type-body-medium" />
			</li>
		</ul>
	</section>
</template>
