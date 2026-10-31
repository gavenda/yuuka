<script setup lang="ts">
import AccountLogo from '@/components/AccountLogo.vue';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import EmptyState from '@/components/EmptyState.vue';
import FabButton from '@/components/FabButton.vue';
import MoneyText from '@/components/MoneyText.vue';
import StatCard from '@/components/StatCard.vue';
import TradeForm from '@/components/TradeForm.vue';
import TradeList from '@/components/TradeList.vue';
import { formatLongDate } from '@/lib/dates';
import { whenDiscardable } from '@/lib/formPane';
import { formatPrice, percentChange, type Holding } from '@/lib/investments';
import { displayMoney, MASK, useAmountVisibility } from '@/lib/privacy';
import { useInvestmentStore } from '@/stores/investments';
import { useLedgerStore } from '@/stores/ledger';
import type { Trade } from '@/types';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { t } from '@/i18n';

/**
 * Every PSE holding across the broker accounts (`InvestmentsScreen` on Android): what it is worth at the last
 * close, how far that is from what it cost, what it is made of, and the trades behind it.
 */
const store = useInvestmentStore();
const ledger = useLedgerStore();
const route = useRoute();
const router = useRouter();
const { hidden } = useAmountVisibility();

const tab = ref<'holdings' | 'activity'>('holdings');
const TABS = [
	{ value: 'holdings' as const, label: t('investments.tabHoldings') },
	{ value: 'activity' as const, label: t('investments.tabActivity') },
];
const trading = ref(false);

onMounted(async () => {
	await Promise.all([ledger.load(), store.load()]);
});

const marketValue = computed(() => store.held.reduce((sum, holding) => sum + holding.marketValue, 0));
const cost = computed(() => store.held.reduce((sum, holding) => sum + holding.costBasis, 0));
const unrealised = computed(() => marketValue.value - cost.value);
const dayChange = computed(() => store.held.reduce((sum, holding) => sum + holding.dayChange, 0));

const caption = computed(() =>
	store.pricesAsOf ? t('investments.asOf', { cost: displayMoney(cost.value), date: formatLongDate(store.pricesAsOf) }) : undefined,
);

/** The holdings under the broker account that holds them. */
const groups = computed(() => {
	const byAccount = new Map<string, Holding[]>();
	for (const holding of store.held) {
		const rows = byAccount.get(holding.position.accountId);
		if (rows) rows.push(holding);
		else byAccount.set(holding.position.accountId, [holding]);
	}
	return [...byAccount.entries()].map(([accountId, rows]) => ({
		accountId,
		name: rows[0]!.accountName,
		bank: rows[0]!.bank,
		total: rows.reduce((sum, holding) => sum + holding.marketValue, 0),
		cash: store.brokerCash.get(accountId) ?? 0,
		rows,
	}));
});

/** The trades under the month they were made in, newest first. */
const months = computed(() => {
	const byMonth = new Map<string, Trade[]>();
	for (const trade of store.trades) {
		const key = trade.occurredOn.slice(0, 7);
		const rows = byMonth.get(key);
		if (rows) rows.push(trade);
		else byMonth.set(key, [trade]);
	}
	return [...byMonth.entries()].map(([month, rows]) => ({
		label: new Date(`${month}-01T00:00:00`).toLocaleDateString(undefined, { month: 'long', year: 'numeric' }),
		rows,
	}));
});

/** One bar split by what each stock is worth, largest first. */
const SLICE_CLASSES = [
	'bg-primary',
	'bg-secondary',
	'bg-tertiary',
	'bg-primary-container',
	'bg-secondary-container',
	'bg-tertiary-container',
];
const allocation = computed(() => {
	const bySymbol = new Map<string, number>();
	for (const holding of store.held) bySymbol.set(holding.symbol, (bySymbol.get(holding.symbol) ?? 0) + holding.marketValue);
	const total = Math.max(
		1,
		[...bySymbol.values()].reduce((sum, value) => sum + value, 0),
	);
	return [...bySymbol.entries()]
		.sort((a, b) => b[1] - a[1])
		.map(([symbol, value], index) => ({ symbol, share: (value * 100) / total, colour: SLICE_CLASSES[index % SLICE_CLASSES.length]! }));
});

const sharesShown = (count: number) => (hidden.value ? MASK : count.toLocaleString('en-US'));

function openTrade(): void {
	whenDiscardable(() => {
		trading.value = true;
	});
}
</script>

<template>
	<div class="flex flex-col gap-0.5 px-4 pt-3 pb-24">
		<p v-if="store.error" class="type-body-medium py-2 text-error" role="alert">{{ store.error }}</p>

		<EmptyState
			v-if="store.loaded && store.trades.length === 0"
			fill
			mood="attentive"
			:title="t('investments.emptyTitle')"
			:description="t('investments.emptyDescription')"
		/>

		<template v-else-if="store.loaded">
			<StatCard :label="t('investments.marketValue')" :amount="marketValue" hero watermark :caption="caption" />

			<div class="mt-2 grid grid-cols-2 gap-2">
				<div class="card p-4">
					<p class="type-label-small uppercase">{{ t('investments.unrealised') }}</p>
					<MoneyText :amount="unrealised" tone="signed-alert" :explicit="unrealised !== 0" class="type-title-large mt-1.5 block" />
					<p class="type-body-small">{{ percentChange(unrealised, cost) }}</p>
				</div>
				<div class="card p-4">
					<p class="type-label-small uppercase">{{ t('investments.today') }}</p>
					<MoneyText :amount="dayChange" tone="signed-alert" :explicit="dayChange !== 0" class="type-title-large mt-1.5 block" />
					<p class="type-body-small">{{ percentChange(dayChange, marketValue - dayChange) }}</p>
				</div>
				<div class="card p-4">
					<p class="type-label-small uppercase">{{ t('investments.realised') }}</p>
					<MoneyText
						:amount="store.realisedThisYear"
						tone="signed-alert"
						:explicit="store.realisedThisYear !== 0"
						class="type-title-large mt-1.5 block"
					/>
				</div>
				<div class="card p-4">
					<p class="type-label-small uppercase">{{ t('investments.dividends') }}</p>
					<MoneyText
						:amount="store.dividendsThisYear"
						tone="signed-alert"
						:explicit="store.dividendsThisYear !== 0"
						class="type-title-large mt-1.5 block"
					/>
				</div>
			</div>

			<div v-if="allocation.length" class="card mt-2 flex flex-col gap-3 p-5">
				<h2 class="type-title-large">{{ t('investments.allocation') }}</h2>
				<div class="flex h-4 gap-0.5 overflow-hidden rounded-full">
					<span v-for="slice in allocation" :key="slice.symbol" :class="slice.colour" :style="{ flexGrow: slice.share }" />
				</div>
				<div class="grid grid-cols-2 gap-x-4 gap-y-3">
					<div v-for="slice in allocation" :key="slice.symbol" class="flex items-center gap-2">
						<span class="size-2.5 rounded-full" :class="slice.colour" aria-hidden="true" />
						<span class="type-body-medium flex-1">{{ slice.symbol }}</span>
						<span class="type-body-medium text-on-surface-variant">{{ slice.share.toFixed(1) }}%</span>
					</div>
				</div>
			</div>

			<ConnectedButtonGroup v-model="tab" :label="t('investments.tabs')" :options="TABS" class="mt-4 mb-1" />

			<template v-if="tab === 'holdings'">
				<section v-for="group in groups" :key="group.accountId">
					<div class="group-header">
						<span class="flex items-center gap-3">
							<AccountLogo :name="group.name" :bank="group.bank" :size="24" />
							<h2>{{ group.name }}</h2>
						</span>
						<MoneyText :amount="group.total" />
					</div>
					<ul class="group-rows">
						<li v-for="holding in group.rows" :key="holding.symbol">
							<button
								type="button"
								class="group-row state-layer focus-ring cursor-pointer"
								:aria-current="route.params.symbol === holding.symbol ? 'true' : undefined"
								@click="router.push(`/investments/${holding.symbol}`)"
							>
								<span class="flex items-center gap-4 px-4 py-3">
									<span
										class="type-label-large grid size-10 shrink-0 place-items-center rounded-full bg-secondary-container text-on-secondary-container"
										aria-hidden="true"
										>{{ holding.symbol.slice(0, 2) }}</span
									>
									<span class="flex min-w-0 flex-1 flex-col gap-0.5">
										<span class="type-title-medium">{{ holding.symbol }}</span>
										<span class="type-body-small truncate">{{ holding.name }}</span>
										<span class="type-body-small text-on-surface-variant">{{
											t('investments.sharesAt', { shares: sharesShown(holding.shares), price: formatPrice(holding.priceE4) })
										}}</span>
									</span>
									<span class="flex shrink-0 flex-col items-end gap-0.5">
										<MoneyText :amount="holding.marketValue" class="type-title-medium" />
										<MoneyText :amount="holding.unrealised" tone="signed-alert" explicit class="type-body-small" />
										<span class="type-body-small text-on-surface-variant">{{ percentChange(holding.unrealised, holding.costBasis) }}</span>
									</span>
								</span>
							</button>
						</li>
						<li>
							<div class="group-row">
								<span class="flex items-center px-4 py-4">
									<span class="type-title-medium flex-1">{{ t('investments.cash') }}</span>
									<MoneyText :amount="group.cash" tone="signed-alert" class="type-title-medium" />
								</span>
							</div>
						</li>
					</ul>
				</section>
			</template>

			<template v-else>
				<section v-for="month in months" :key="month.label">
					<div class="group-header">
						<h2>{{ month.label }}</h2>
					</div>
					<TradeList :trades="month.rows" show-symbol />
				</section>
			</template>
		</template>

		<TradeForm :open="trading" @close="trading = false" />
		<FabButton :label="t('investments.newTrade')" @click="openTrade" />
	</div>
</template>
