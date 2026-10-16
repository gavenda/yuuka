<script setup lang="ts">
import MoneyText from '@/components/MoneyText.vue';
import type { Account } from '@/types';
import { t } from '@/i18n';

/**
 * Every account in use with its balance, under its type, each in the currency it holds (`AccountsCard` in
 * `DashboardCards.kt`). Net worth adds them as they stand, so when they hold more than one currency the card
 * says so rather than let the total pass for one currency's figure.
 */
defineProps<{ groups: { type: string; accounts: Account[] }[]; mixed: boolean }>();
</script>

<template>
	<section class="card p-5">
		<h2 class="type-title-large">{{ t('dashboard.accounts') }}</h2>

		<template v-for="group in groups" :key="group.type">
			<h3 v-if="group.type" class="type-label-large mt-4 mb-1 text-on-surface-variant">{{ group.type }}</h3>
			<ul>
				<li v-for="account in group.accounts" :key="account.id" class="flex items-center gap-3 py-1.5">
					<span class="type-body-medium min-w-0 flex-1 truncate">{{ account.name }}</span>
					<MoneyText :amount="account.balance" :currency="account.currency" tone="signed-alert" class="type-body-medium" />
				</li>
			</ul>
		</template>

		<p v-if="mixed" class="type-body-small mt-3 text-on-surface-variant">{{ t('dashboard.mixedCurrencies') }}</p>
	</section>
</template>
