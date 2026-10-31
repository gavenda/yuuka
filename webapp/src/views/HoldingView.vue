<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import MoneyText from '@/components/MoneyText.vue';
import TradeForm from '@/components/TradeForm.vue';
import TradeList from '@/components/TradeList.vue';
import { whenDiscardable } from '@/lib/formPane';
import { ADD } from '@/lib/icons';
import { formatPrice, holdingOf, percentChange } from '@/lib/investments';
import { MASK, useAmountVisibility } from '@/lib/privacy';
import { useInvestmentStore } from '@/stores/investments';
import { useLedgerStore } from '@/stores/ledger';
import type { PricePoint } from '@/types';
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { t } from '@/i18n';

/**
 * One stock across every broker account that holds it (`HoldingScreen` on Android): its last close and the day's
 * move, its closes over a month, a quarter or a year, the position in it and the trades that built it.
 */
const store = useInvestmentStore();
const ledger = useLedgerStore();
const route = useRoute();
const { hidden } = useAmountVisibility();

const symbol = computed(() => String(route.params.symbol ?? ''));
const trading = ref(false);

type Range = 1 | 3 | 12;
const range = ref<Range>(3);
const RANGES: { value: Range; label: string }[] = [
	{ value: 1, label: t('investments.range1m') },
	{ value: 3, label: t('investments.range3m') },
	{ value: 12, label: t('investments.range1y') },
];

watch(
	symbol,
	async (value) => {
		if (!value) return;
		await Promise.all([ledger.load(), store.load()]);
		await store.loadPrices(value);
	},
	{ immediate: true },
);

/** The stock's positions in every account, added together. */
const holding = computed(() => {
	const rows = store.holdings.filter((entry) => entry.symbol === symbol.value);
	const first = rows[0];
	if (!first) return null;
	const sum = (pick: (position: (typeof first)['position']) => number) => rows.reduce((total, row) => total + pick(row.position), 0);
	return holdingOf(
		{
			accountId: '',
			symbol: symbol.value,
			shares: sum((position) => position.shares),
			costBasis: sum((position) => position.costBasis),
			realised: sum((position) => position.realised),
			dividends: sum((position) => position.dividends),
		},
		{
			name: first.name,
			accountName: rows.map((row) => row.accountName).join(', '),
			bank: null,
			priceE4: first.priceE4,
			changeBp: first.changeBp,
			asOf: first.asOf,
		},
	);
});

const trades = computed(() => store.trades.filter((trade) => trade.symbol === symbol.value));
const dayMove = computed(() => (holding.value ? holding.value.priceE4 - holding.value.previousCloseE4 : 0));

/** The closes in range, the latest close joining them when the history has not caught up with it yet. */
const closes = computed<PricePoint[]>(() => {
	const known = store.prices[symbol.value] ?? [];
	const current = holding.value;
	const all =
		current?.asOf && !known.some((point) => point.day >= current.asOf!) ? [...known, { day: current.asOf, close: current.priceE4 }] : known;
	const from = new Date();
	from.setMonth(from.getMonth() - range.value);
	const cutoff = from.toISOString().slice(0, 10);
	return all.filter((point) => point.day >= cutoff);
});

const WIDTH = 600;
const HEIGHT = 160;
const chart = computed(() => {
	const values = closes.value.map((point) => point.close);
	if (values.length < 2) return null;
	const high = Math.max(...values);
	const low = Math.min(...values);
	const span = Math.max(1, high - low);
	const step = WIDTH / (values.length - 1);
	const points = values.map((value, index) => [index * step, HEIGHT * (1 - (value - low) / span)] as const);
	const line = points.map(([x, y], index) => `${index === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
	const first = values[0]!;
	const last = values[values.length - 1]!;
	return {
		line,
		area: `${line} L${WIDTH},${HEIGHT} L0,${HEIGHT} Z`,
		high,
		low,
		up: last >= first,
		change: percentChange(last - first, first),
		end: points[points.length - 1]!,
	};
});

const sharesShown = (count: number) => (hidden.value ? MASK : count.toLocaleString('en-US'));

function openTrade(): void {
	whenDiscardable(() => {
		trading.value = true;
	});
}

const accountFor = computed(() => store.held.find((entry) => entry.symbol === symbol.value)?.position.accountId ?? null);
</script>

<template>
	<div v-if="holding" class="flex flex-col gap-0.5 px-4 pt-3 pb-8">
		<div class="flex items-center gap-4 px-2 py-2">
			<span
				class="type-title-medium grid size-14 shrink-0 place-items-center rounded-full bg-secondary-container text-on-secondary-container"
				aria-hidden="true"
				>{{ holding.symbol.slice(0, 2) }}</span
			>
			<div class="min-w-0 flex-1">
				<p class="type-title-large">{{ holding.name }}</p>
				<p class="type-headline-medium">{{ formatPrice(holding.priceE4) }}</p>
				<p
					v-if="holding.changeBp !== null"
					class="type-body-medium"
					:class="dayMove > 0 ? 'text-primary' : dayMove < 0 ? 'text-error' : 'text-on-surface-variant'"
				>
					{{
						t('investments.todayChange', {
							change: `${dayMove < 0 ? '−' : '+'}${formatPrice(Math.abs(dayMove))}`,
							percent: `${holding.changeBp >= 0 ? '+' : ''}${(holding.changeBp / 100).toFixed(2)}%`,
						})
					}}
				</p>
			</div>
		</div>

		<div class="card flex flex-col gap-4 p-5">
			<div v-if="chart" class="flex flex-col gap-1">
				<div class="flex justify-between">
					<span class="type-title-medium" :class="chart.up ? 'text-primary' : 'text-error'">{{ chart.change }}</span>
					<span class="type-label-small text-on-surface-variant">{{ formatPrice(chart.high) }}</span>
				</div>
				<!-- Prices are public, so nothing here is masked. -->
				<svg
					:viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
					preserveAspectRatio="none"
					class="h-40 w-full overflow-visible"
					:class="chart.up ? 'text-primary' : 'text-error'"
					role="img"
					:aria-label="t('investments.priceChart', { symbol: holding.symbol })"
				>
					<defs>
						<linearGradient :id="`fill-${holding.symbol}`" x1="0" x2="0" y1="0" y2="1">
							<stop offset="0" stop-color="currentColor" stop-opacity="0.24" />
							<stop offset="1" stop-color="currentColor" stop-opacity="0" />
						</linearGradient>
					</defs>
					<path :d="chart.area" :fill="`url(#fill-${holding.symbol})`" />
					<path
						:d="chart.line"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
						vector-effect="non-scaling-stroke"
						stroke-linejoin="round"
					/>
				</svg>
				<span class="type-label-small self-end text-on-surface-variant">{{ formatPrice(chart.low) }}</span>
			</div>
			<p v-else class="type-body-medium py-6 text-on-surface-variant">{{ t('investments.pricesGathering') }}</p>
			<ConnectedButtonGroup v-model="range" :label="t('investments.priceRange')" :options="RANGES" />
		</div>

		<button type="button" class="btn-secondary my-2 w-full justify-center" @click="openTrade">
			<AppIcon :icon="ADD" :size="18" />
			{{ t('investments.newTrade') }}
		</button>

		<div class="card flex flex-col gap-2.5 p-5">
			<h2 class="type-title-large pb-1">{{ t('investments.position') }}</h2>
			<div class="flex justify-between">
				<span class="type-body-medium text-on-surface-variant">{{ t('investments.shares') }}</span>
				<span class="type-body-large">{{ sharesShown(holding.shares) }}</span>
			</div>
			<div v-if="holding.shares > 0" class="flex justify-between">
				<span class="type-body-medium text-on-surface-variant">{{ t('investments.averageCost') }}</span>
				<span class="type-body-large">{{ formatPrice(holding.avgCostE4) }}</span>
			</div>
			<div class="flex justify-between">
				<span class="type-body-medium text-on-surface-variant">{{ t('investments.costBasis') }}</span>
				<MoneyText :amount="holding.costBasis" class="type-body-large" />
			</div>
			<div class="flex justify-between">
				<span class="type-body-medium text-on-surface-variant">{{ t('investments.marketValue') }}</span>
				<MoneyText :amount="holding.marketValue" class="type-body-large" />
			</div>
			<hr class="border-outline-variant" />
			<div class="flex items-center justify-between">
				<span class="flex flex-col">
					<span class="type-body-medium text-on-surface-variant">{{ t('investments.unrealisedGain') }}</span>
					<span class="type-body-small text-on-surface-variant">{{ percentChange(holding.unrealised, holding.costBasis) }}</span>
				</span>
				<MoneyText :amount="holding.unrealised" tone="signed-alert" :explicit="holding.unrealised !== 0" class="type-body-large" />
			</div>
			<div class="flex justify-between">
				<span class="type-body-medium text-on-surface-variant">{{ t('investments.realisedGain') }}</span>
				<MoneyText
					:amount="holding.position.realised"
					tone="signed-alert"
					:explicit="holding.position.realised !== 0"
					class="type-body-large"
				/>
			</div>
			<div class="flex justify-between">
				<span class="type-body-medium text-on-surface-variant">{{ t('investments.dividendsReceived') }}</span>
				<MoneyText :amount="holding.position.dividends" tone="signed" class="type-body-large" />
			</div>
		</div>

		<div class="group-header">
			<h2>{{ t('investments.trades') }}</h2>
		</div>
		<TradeList :trades="trades" />

		<TradeForm :open="trading" :initial-symbol="holding.symbol" :initial-account="accountFor" @close="trading = false" />
	</div>
</template>
