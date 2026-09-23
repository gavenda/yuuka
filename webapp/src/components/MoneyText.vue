<script setup lang="ts">
import { DEFAULT_CURRENCY } from '@/lib/money';
import { displayMoney } from '@/lib/privacy';
import { computed } from 'vue';

const props = withDefaults(
	defineProps<{
		amount: number;
		currency?: string;
		/** Colour by sign: secondary for inflows, red for outflows. */
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
	if (props.transfer) return 'text-primary';
	if (!props.signed) return 'text-on-surface';
	if (props.amount > 0) return 'text-secondary';
	if (props.amount < 0) return 'text-error';
	return 'text-on-surface-variant';
});
</script>

<template>
	<span class="tabular" :class="tone">{{ formatted }}</span>
</template>
