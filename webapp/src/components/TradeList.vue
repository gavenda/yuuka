<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import AlertDialog from '@/components/AlertDialog.vue';
import MoneyText from '@/components/MoneyText.vue';
import SwipeReveal from '@/components/SwipeReveal.vue';
import { ApiError } from '@/lib/api';
import { DELETE } from '@/lib/icons';
import { formatPrice } from '@/lib/investments';
import { MASK, useAmountVisibility } from '@/lib/privacy';
import { showSnackbar } from '@/lib/snackbar';
import { useInvestmentStore } from '@/stores/investments';
import type { Trade } from '@/types';
import { computed, ref } from 'vue';
import { t } from '@/i18n';

/**
 * Trades as a segmented list, each one's delete behind its swipe (`TradeRow` and `DeleteTradeDialog` on
 * Android). Deleting takes the trade's cash with it, so it asks first; a buy a later sell depends on cannot go,
 * and the dialog says so instead.
 */
const props = defineProps<{ trades: Trade[]; showSymbol?: boolean }>();

const store = useInvestmentStore();
const { hidden } = useAmountVisibility();
const pending = ref<Trade | null>(null);
const blocked = computed(() => (pending.value ? !store.canDelete(pending.value.id) : false));
const error = ref<string | null>(null);

const kindLabel = (trade: Trade) => t(`investments.${trade.kind}`);
const sharesShown = (count: number) => (hidden.value ? MASK : count.toLocaleString('en-US'));
const dayOf = (day: string) => new Date(`${day}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });

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
						:label="t('investments.deleteTrade', { name: `${trade.symbol} ${kindLabel(trade)}` })"
						danger
						@click="pending = trade"
					/>
				</template>
				<div class="group-row">
					<span class="flex items-center gap-4 px-4 py-3">
						<span class="flex min-w-0 flex-1 flex-col gap-0.5">
							<span class="type-title-medium truncate">{{ showSymbol ? `${kindLabel(trade)} · ${trade.symbol}` : kindLabel(trade) }}</span>
							<span class="type-body-small">{{
								t(trade.kind === 'dividend' ? 'investments.dividendLine' : 'investments.sharesAt', {
									shares: sharesShown(trade.shares),
									price: formatPrice(trade.price),
								})
							}}</span>
							<span class="type-body-small text-on-surface-variant">{{
								t('investments.tradeLine', { date: dayOf(trade.occurredOn), account: trade.accountName ?? '' })
							}}</span>
						</span>
						<!-- A buy or sell only turns cash into shares and back; a dividend is income. -->
						<MoneyText
							:amount="trade.amount"
							:tone="trade.kind === 'dividend' ? 'signed' : 'transfer'"
							explicit
							class="type-body-large shrink-0"
						/>
					</span>
				</div>
			</SwipeReveal>
		</li>
	</ul>

	<AlertDialog :open="pending !== null && blocked" role="dialog" :title="t('investments.deleteBlockedTitle')" @close="pending = null">
		{{ t('investments.deleteBlockedBody', { symbol: pending?.symbol ?? '' }) }}
		<template #actions>
			<button type="button" class="btn-text" @click="pending = null">{{ t('common.noGoBack') }}</button>
		</template>
	</AlertDialog>

	<AlertDialog
		:open="pending !== null && !blocked"
		:title="pending ? t('investments.deleteTitle', { kind: kindLabel(pending).toLowerCase(), symbol: pending.symbol }) : ''"
		:icon="DELETE"
		@close="pending = null"
	>
		{{ t('investments.deleteBody', { account: pending?.accountName ?? '' }) }}
		<template #actions>
			<button type="button" class="btn-text" @click="pending = null">{{ t('common.noGoBack') }}</button>
			<button type="button" class="btn-danger" @click="remove">{{ t('common.yesDelete') }}</button>
		</template>
	</AlertDialog>
</template>
