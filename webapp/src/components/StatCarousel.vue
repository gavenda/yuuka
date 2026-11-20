<script setup lang="ts">
import StatCard from '@/components/StatCard.vue';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { Carousel } from 'tailwind-material3';

/** One card in a `StatCarousel`: the `StatCard` props that differ from stat to stat. */
export interface StatItem {
	label: string;
	amount: number;
	caption?: string;
	signed?: boolean;
	watermark?: boolean;
	/** Draws this card on its own container instead of the carousel's. */
	tone?: 'surface' | 'secondary' | 'tertiary';
}

/**
 * A row of related figures (`StatCarousel.kt`, a Material 3 uncontained carousel): tailwind-material3's
 * `Carousel` with the `uncontained` layout, so every card keeps its full width, a group shares one row
 * instead of a stack, and on a window too narrow for them all the next one peeks in from the edge and the row
 * scrolls sideways (or is dragged, with a mouse). It bleeds through the page's side padding, so its cards
 * scroll to the screen edge while the first still lines up with the rest of the content. The library rounds an
 * item to 28px and sets them 8px apart; this keeps the card's own 12px corners and Android's 12dp spacing.
 */
withDefaults(
	defineProps<{
		stats: StatItem[];
		currency?: string;
		tone?: 'surface' | 'secondary' | 'tertiary';
		/** Off where the row stands in a column beside another, so it does not scroll into the gap between them. */
		bleed?: boolean;
	}>(),
	{ currency: DEFAULT_CURRENCY, tone: 'surface', bleed: true },
);
</script>

<template>
	<Carousel
		layout="uncontained"
		:item-width="232"
		height="auto"
		class="[&>div]:items-center [&>div]:gap-3! [&>div>*]:rounded-[12px]!"
		:class="{ '-mx-4 scroll-px-4 px-4': bleed }"
	>
		<StatCard
			v-for="stat in stats"
			:key="stat.label"
			:label="stat.label"
			:amount="stat.amount"
			:currency="currency"
			:caption="stat.caption"
			:signed="stat.signed"
			:watermark="stat.watermark"
			:tone="stat.tone ?? tone"
		/>
	</Carousel>
</template>
