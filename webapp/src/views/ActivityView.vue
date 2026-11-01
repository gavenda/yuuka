<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue';
import ListEnd from '@/components/ListEnd.vue';
import MoneyText from '@/components/MoneyText.vue';
import TradeForm from '@/components/TradeForm.vue';
import TradeList from '@/components/TradeList.vue';
import { formatLongDate } from '@/lib/dates';
import { whenDiscardable } from '@/lib/formPane';
import { useInView } from '@/lib/inView';
import { useInvestmentStore } from '@/stores/investments';
import { useLedgerStore } from '@/stores/ledger';
import type { Trade } from '@/types';
import { computed, onMounted, ref, watch } from 'vue';
import { t } from '@/i18n';

/** How many trades the activity shows at a time, as many as a page of Transactions. */
const TRADE_PAGE = 50;

/**
 * Every trade, newest first, under the day it was made (`ActivityScreen` on Android): opened from Investments'
 * "See all", beside it where the page has room. Every trade is already on the device — the holdings are worked
 * out from all of them — so the list is not asked of the server a page at a time; it shows `TRADE_PAGE` and the
 * next as the end comes within reach, as Transactions does, and ends with Yuuka once there are no more.
 */
const store = useInvestmentStore();
const ledger = useLedgerStore();
const shown = ref(TRADE_PAGE);
const trading = ref(false);
const editing = ref<Trade | null>(null);

onMounted(async () => {
	await Promise.all([ledger.load(), store.load()]);
});

const hasMore = computed(() => store.trades.length > shown.value);

/** A day cut off at the end of a page still says what the whole of it moved. */
const dayTotals = computed(() => {
	const totals = new Map<string, number>();
	for (const trade of store.trades) {
		const key = trade.occurredOn.slice(0, 10);
		totals.set(key, (totals.get(key) ?? 0) + trade.amount);
	}
	return totals;
});

/**
 * The trades on show under the day they were made, as Transactions lists its rows: the date as the day's
 * heading with the cash the day's trades moved at its end.
 */
const days = computed(() => {
	const byDay = new Map<string, Trade[]>();
	for (const trade of store.trades.slice(0, shown.value)) {
		const key = trade.occurredOn.slice(0, 10);
		const rows = byDay.get(key);
		if (rows) rows.push(trade);
		else byDay.set(key, [trade]);
	}
	return [...byDay.entries()].map(([date, rows]) => ({ date, total: dayTotals.value.get(date) ?? 0, rows }));
});

/** The foot of the list for as long as there is more of it to show. */
const listEnd = ref<HTMLElement | null>(null);
/** A screen ahead of the fold, so the next page is usually there before the end is. */
const endInReach = useInView(listEnd, '0px 0px 100% 0px');

/** Where the foot stands now: the observer only catches up after layout, so a page just drawn asks directly. */
function footInReach(): boolean {
	const foot = listEnd.value;
	return !!foot && foot.getBoundingClientRect().top <= window.innerHeight * 2;
}

// The next page is shown as the end comes within reach, and again when a page lands with the end still in reach.
watch(
	[endInReach, shown],
	() => {
		if (endInReach.value && hasMore.value && footInReach()) shown.value += TRADE_PAGE;
	},
	{ flush: 'post' },
);

function openEdit(trade: Trade): void {
	whenDiscardable(() => {
		editing.value = trade;
		trading.value = true;
	});
}

// A form standing beside the list closes with the trade it was editing, wherever that was deleted.
watch(
	() => store.trades,
	(trades) => {
		if (trading.value && editing.value && !trades.some((trade) => trade.id === editing.value?.id)) trading.value = false;
	},
);
</script>

<template>
	<div class="flex flex-col gap-0.5 px-4 pt-3 pb-8">
		<p v-if="store.error" class="type-body-medium py-2 text-error" role="alert">{{ store.error }}</p>

		<EmptyState
			v-if="store.loaded && store.trades.length === 0"
			fill
			mood="attentive"
			:title="t('investments.emptyTitle')"
			:description="t('investments.emptyDescription')"
		/>

		<template v-else-if="store.loaded">
			<section v-for="day in days" :key="day.date">
				<div class="group-header">
					<h2>{{ formatLongDate(day.date) }}</h2>
					<MoneyText :amount="day.total" tone="signed" />
				</div>
				<TradeList :trades="day.rows" show-symbol :selected-id="trading ? editing?.id : null" @edit="openEdit" />
			</section>

			<!-- Nothing can fail here, since nothing is fetched: the next page is only waiting to be drawn. -->
			<div v-if="hasMore" ref="listEnd" class="flex min-h-18 flex-col items-center justify-center p-4 text-center">
				<p class="type-body-medium text-on-surface-variant">{{ t('common.fetching') }}</p>
			</div>
			<ListEnd v-else />
		</template>

		<TradeForm :open="trading" :editing="editing" @close="trading = false" />
	</div>
</template>
