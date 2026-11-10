<script setup lang="ts">
import CatMark from '@/components/CatMark.vue';
import MoneyText from '@/components/MoneyText.vue';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { Card } from 'tailwind-material3';
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
		tone?: 'surface' | 'secondary' | 'tertiary';
		/** Lets the cat into the card's bottom corner, for the one card a screen leads with. */
		watermark?: boolean;
	}>(),
	{ currency: DEFAULT_CURRENCY, signed: false, hero: false, tone: 'surface', watermark: false },
);

/** tailwind-material3's filled `Card`, on a tinted container for a tone. */
const card = computed(() =>
	props.tone === 'secondary'
		? 'bg-secondary-container! text-on-secondary-container!'
		: props.tone === 'tertiary'
			? 'bg-tertiary-container! text-on-tertiary-container!'
			: '',
);
const figure = computed(() => (props.hero ? 'type-display-small' : 'type-headline-medium'));
</script>

<template>
	<Card :padded="false" class="p-5" :class="card">
		<CatMark v-if="watermark" />
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
	</Card>
</template>
