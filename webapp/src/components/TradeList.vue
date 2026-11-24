<script setup lang="ts">
import { Button } from 'tailwind-material3';
import ActionIcon from '@/components/ActionIcon.vue';
import AlertDialog from '@/components/AlertDialog.vue';
import MoneyText from '@/components/MoneyText.vue';
import SwipeReveal from '@/components/SwipeReveal.vue';
import { ApiError } from '@/lib/api';
import { formatDate, formatTime } from '@/lib/dates';
import { DELETE } from '@/lib/icons';
import { formatPrice, isFund, securityLabel, tradeWord } from '@/lib/investments';
import { displayUnits } from '@/lib/privacy';
import { showSnackbar } from '@/lib/snackbar';
import { useInvestmentStore } from '@/stores/investments';
import type { Trade } from '@/types';
import { computed, ref } from 'vue';
import { t } from '@/i18n';

/**
 * Trades as a segmented list (`TradeRow` and `DeleteTradeDialog` on Android): pressing one edits it, and its
 * delete is behind its swipe. Deleting takes the trade's cash with it, so it asks first; a buy a later sell
 * depends on cannot go, and the dialog says so instead.
 */
/** `showDate` for rows that stand under no day's heading (a stock's own screen), where the day leads the time. */
const props = defineProps<{ trades: Trade[]; showSymbol?: boolean; showDate?: boolean; selectedId?: string | null }>();
const emit = defineEmits<{ edit: [trade: Trade] }>();

const store = useInvestmentStore();
const pending = ref<Trade | null>(null);
const blocked = computed(() => (pending.value ? !store.canDelete(pending.value.id) : false));
const error = ref<string | null>(null);

const kindLabel = (trade: Trade) => t(`investments.${tradeWord(trade.kind, trade.symbol)}`);
const nameOf = (trade: Trade) => securityLabel(trade.symbol, trade.name ?? store.securitiesBySymbol.get(trade.symbol)?.name);
/** How many at what: `100 sh · ₱118.00`, a fund's `5.1483 units · ₱1,942.3725`, a dividend's or a distribution's a share or a unit. */
function lineOf(trade: Trade): string {
	const fund = isFund(trade.symbol);
	const key = trade.kind === 'dividend' ? (fund ? 'distributionLine' : 'dividendLine') : fund ? 'unitsAt' : 'sharesAt';
	return t(`investments.${key}`, { shares: displayUnits(trade.units), price: formatPrice(trade.price) });
}
/** The time a trade was made, led by its day (`15 Oct, 9:45 AM`) where no day's heading says it; nothing for an untimed trade under one. */
const whenOf = (occurredOn: string) =>
	[props.showDate ? formatDate(occurredOn) : null, formatTime(occurredOn)].filter(Boolean).join(', ') || null;

async function remove(): Promise<void> {
	const trade = pending.value;
	if (!trade) return;
	pending.value = null;
	try {
		await store.deleteTrade(trade.id);
		showSnackbar(t('investments.tradeDeleted'));
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : t('common.couldNotSave');
	}
}
</script>

<template>
	<p v-if="error" class="type-body-medium px-4 py-2 text-error" role="alert">{{ error }}</p>
	<ul class="group-rows">
		<li v-for="trade in props.trades" :key="trade.id">
			<SwipeReveal>
				<template #actions>
					<ActionIcon
						icon="delete"
						:label="t('investments.deleteTrade', { name: `${nameOf(trade)} ${kindLabel(trade)}` })"
						danger
						@click="pending = trade"
					/>
				</template>
				<!-- The whole row opens the edit form. Spans, not blocks, because it is a button. -->
				<button
					type="button"
					class="group-row state-layer focus-ring w-full cursor-pointer text-start"
					:aria-current="props.selectedId === trade.id ? 'true' : undefined"
					@click="emit('edit', trade)"
				>
					<!-- Laid out as a transaction's row: the headline, the account and the time under it, the cash and the shares at the end. -->
					<span class="flex gap-4 px-4 py-3">
						<span class="flex min-w-0 flex-1 flex-col gap-1">
							<span class="type-title-medium truncate">{{ showSymbol ? `${kindLabel(trade)} · ${nameOf(trade)}` : kindLabel(trade) }}</span>
							<span class="type-body-small truncate">{{ trade.accountName ?? '' }}</span>
							<span v-if="whenOf(trade.occurredOn)" class="type-body-small">{{ whenOf(trade.occurredOn) }}</span>
						</span>
						<!-- The cash at the end with how many at what under it, where a transaction keeps its running balance. -->
						<span class="flex shrink-0 flex-col items-end gap-1">
							<!-- A buy or sell only turns cash into shares or units and back; a dividend is income. -->
							<MoneyText
								:amount="trade.amount"
								:tone="trade.kind === 'dividend' ? 'signed' : 'transfer'"
								explicit
								class="type-title-medium"
							/>
							<span class="type-body-small">{{ lineOf(trade) }}</span>
						</span>
					</span>
				</button>
			</SwipeReveal>
		</li>
	</ul>

	<AlertDialog :open="pending !== null && blocked" role="dialog" :title="t('investments.deleteBlockedTitle')" @close="pending = null">
		{{
			t(pending && isFund(pending.symbol) ? 'investments.deleteBlockedFundBody' : 'investments.deleteBlockedBody', {
				symbol: pending ? nameOf(pending) : '',
			})
		}}
		<template #actions>
			<Button variant="text" @click="pending = null">{{ t('common.noGoBack') }}</Button>
		</template>
	</AlertDialog>

	<AlertDialog
		:open="pending !== null && !blocked"
		:title="pending ? t('investments.deleteTitle', { kind: kindLabel(pending).toLowerCase(), symbol: nameOf(pending) }) : ''"
		:icon="DELETE"
		@close="pending = null"
	>
		{{ t('investments.deleteBody', { account: pending?.accountName ?? '' }) }}
		<template #actions>
			<Button variant="text" @click="pending = null">{{ t('common.noGoBack') }}</Button>
			<Button class="tone-error" @click="remove">{{ t('common.yesDelete') }}</Button>
		</template>
	</AlertDialog>
</template>
