<script setup lang="ts">
import { DEFAULT_CURRENCY } from '@/lib/money';
import { displayMoney } from '@/lib/privacy';
import { computed } from 'vue';

const props = withDefaults(
	defineProps<{
		amount: number;
		currency?: string;
		/** Colour by sign: green for inflows, red for outflows. */
		signed?: boolean;
		/** Always show a leading + or -. */
		explicit?: boolean;
		/** Blue, regardless of sign — a transfer is neither an inflow nor an outflow. */
		transfer?: boolean;
	}>(),
	{ currency: DEFAULT_CURRENCY, signed: false, explicit: false, transfer: false },
);

const formatted = computed(() => {
	const text = displayMoney(Math.abs(props.amount), props.currency);
	if (props.explicit) return `${props.amount < 0 ? '−' : '+'}${text}`;
	return props.amount < 0 ? `−${text}` : text;
});

const tone = computed(() => {
	if (props.transfer) return 'text-blue-700 dark:text-blue-400';
	if (!props.signed) return 'text-slate-900 dark:text-slate-100';
	if (props.amount > 0) return 'text-emerald-700 dark:text-emerald-400';
	if (props.amount < 0) return 'text-rose-700 dark:text-rose-400';
	return 'text-slate-500 dark:text-slate-400';
});
</script>

<template>
	<span class="tabular" :class="tone">{{ formatted }}</span>
</template>
