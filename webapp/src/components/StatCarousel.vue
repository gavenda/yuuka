<script setup lang="ts">
import StatCard from '@/components/StatCard.vue';
import { DEFAULT_CURRENCY } from '@/lib/money';

/** One card in a `StatCarousel`: the `StatCard` props that differ from stat to stat. */
export interface StatItem {
	label: string;
	amount: number;
	caption?: string;
	signed?: boolean;
}

/**
 * A row of related figures (`StatCarousel.kt`, a Material 3 uncontained carousel): every card keeps its
 * full width, so a group shares one row instead of a stack, and on a window too narrow for them all the
 * next one peeks in from the edge and the row scrolls sideways. It bleeds through the page's side padding,
 * so its cards scroll to the screen edge while the first still lines up with the rest of the content.
 */
withDefaults(defineProps<{ stats: StatItem[]; currency?: string; tone?: 'surface' | 'secondary' | 'tertiary' }>(), {
	currency: DEFAULT_CURRENCY,
	tone: 'surface',
});
</script>

<template>
	<div class="-mx-4 flex items-center gap-3 overflow-x-auto px-4 [scrollbar-width:none]">
		<StatCard
			v-for="stat in stats"
			:key="stat.label"
			class="w-[232px] shrink-0"
			:label="stat.label"
			:amount="stat.amount"
			:currency="currency"
			:caption="stat.caption"
			:signed="stat.signed"
			:tone="tone"
		/>
	</div>
</template>
