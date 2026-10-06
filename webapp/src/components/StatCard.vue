<script setup lang="ts">
import MoneyText from '@/components/MoneyText.vue';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { computed } from 'vue';

/**
 * One figure and what it is (`StatCard.kt`): the label in small capitals, the amount, and a line under it
 * when there is something to add. `tone` draws the card on a secondary or tertiary container, with its
 * figure in the container's own content colour — the inflow colour is not made for a tinted container, so
 * there the sign alone carries the direction.
 */
const props = withDefaults(
	defineProps<{
		label: string;
		amount: number;
		currency?: string;
		caption?: string;
		/** Tints an inflow, for figures where direction carries meaning. */
		signed?: boolean;
		/** Renders at hero size. Use for the one figure a view leads with. */
		hero?: boolean;
		/** A smaller, heavier figure, for a card among several on one row. */
		compact?: boolean;
		tone?: 'surface' | 'secondary' | 'tertiary';
	}>(),
	{ currency: DEFAULT_CURRENCY, signed: false, hero: false, compact: false, tone: 'surface' },
);

const card = computed(() => (props.tone === 'secondary' ? 'card-secondary' : props.tone === 'tertiary' ? 'card-tertiary' : 'card'));
const figure = computed(() => (props.hero ? 'type-display-small' : props.compact ? 'type-compact-figure' : 'type-headline-medium'));
</script>

<template>
	<div class="p-5" :class="card">
		<p class="type-label-small uppercase">{{ label }}</p>
		<!-- Proportional figures: these are standalone numbers, not a column. -->
		<MoneyText
			:amount="amount"
			:currency="currency"
			:tone="signed && tone === 'surface' ? 'signed' : 'neutral'"
			class="mt-2 block [font-variant-numeric:normal]"
			:class="figure"
		/>
		<p v-if="caption" class="type-body-small mt-1">{{ caption }}</p>
	</div>
</template>
