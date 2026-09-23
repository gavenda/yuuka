<script setup lang="ts">
import { DEFAULT_CURRENCY } from '@/lib/money';
import { displayMoney } from '@/lib/privacy';
import { computed } from 'vue';

const props = withDefaults(
	defineProps<{
		label: string;
		amount: number;
		currency?: string;
		caption?: string;
		/** Tints the value by sign, for figures where direction carries meaning. */
		signed?: boolean;
		/** Renders at hero size. Use for the one figure a view leads with. */
		hero?: boolean;
	}>(),
	{ currency: DEFAULT_CURRENCY, signed: false, hero: false },
);

const formatted = computed(() => {
	const text = displayMoney(Math.abs(props.amount), props.currency);
	return props.amount < 0 ? `−${text}` : text;
});

const tone = computed(() => {
	if (!props.signed) return 'text-on-surface';
	if (props.amount > 0) return 'text-secondary';
	if (props.amount < 0) return 'text-error';
	return 'text-on-surface';
});
</script>

<template>
	<div class="card p-5">
		<p class="type-title-small text-on-surface-variant">{{ label }}</p>
		<!-- Proportional figures: these are standalone numbers, not a column. -->
		<p class="mt-2 font-medium" :class="[tone, hero ? 'text-4xl sm:text-5xl' : 'text-2xl']">{{ formatted }}</p>
		<p v-if="caption" class="mt-1 text-sm text-on-surface-variant">{{ caption }}</p>
	</div>
</template>
