<script setup lang="ts">
import { DEFAULT_CURRENCY } from '@/lib/money';
import { displayMoney } from '@/lib/privacy';
import { computed } from 'vue';

/**
 * An amount, through `displayMoney` so the hide-amounts switch can never miss a figure, in the tone
 * `MoneyText.kt` gives it. A routine outflow stays in the colour of the text around it — `error` is for
 * errors, and a purchase is not one:
 *
 * - `neutral` (the default): the surrounding colour, whatever the sign.
 * - `signed`: an inflow is `primary`; anything else stays neutral.
 * - `signed-alert`: `signed`, with below zero in `error` — for a figure where that is worth noticing, such
 *   as an account's balance or a transaction's amount.
 * - `transfer`: `secondary`, a movement between your own accounts being neither.
 */
const props = withDefaults(
	defineProps<{
		amount: number;
		currency?: string;
		tone?: 'neutral' | 'signed' | 'signed-alert' | 'transfer';
		/** Always show a leading + or -. */
		explicit?: boolean;
	}>(),
	{ currency: DEFAULT_CURRENCY, tone: 'neutral', explicit: false },
);

const formatted = computed(() => {
	const text = displayMoney(Math.abs(props.amount), props.currency);
	if (props.explicit) return `${props.amount < 0 ? '−' : '+'}${text}`;
	return props.amount < 0 ? `−${text}` : text;
});

const colour = computed(() => {
	if (props.tone === 'transfer') return 'text-secondary';
	if (props.tone === 'neutral') return '';
	if (props.amount > 0) return 'text-primary';
	return props.tone === 'signed-alert' && props.amount < 0 ? 'text-error' : '';
});
</script>

<template>
	<span class="tabular" :class="colour">{{ formatted }}</span>
</template>
