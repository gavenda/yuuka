<script setup lang="ts">
import { CAT_EARS, CAT_EYES, CAT_HEAD } from '@/lib/cat';

/**
 * One motif of the cat print. In the print's own colours it is what a screen with nothing to list shows
 * (`CatMark`); as a `watermark` it is drawn in the current text colour instead, large and faint, let into the
 * bottom corner of a card and cut off by its edge (`Modifier.catWatermark`) — the card must be `relative` and
 * `overflow-hidden`. Decoration either way, so it is hidden from assistive tech.
 */
defineProps<{ watermark?: boolean }>();
</script>

<template>
	<svg viewBox="0 0 100 80" class="pointer-events-none shrink-0" :class="watermark ? 'cat-watermark' : 'w-16'" aria-hidden="true">
		<path
			:d="CAT_HEAD"
			fill="none"
			:class="watermark ? 'stroke-current' : 'stroke-primary'"
			stroke-width="3.5"
			stroke-linecap="round"
			stroke-linejoin="round"
		/>
		<path :d="CAT_EYES" :class="watermark ? 'fill-current' : 'fill-primary'" />
		<path :d="CAT_EARS" :class="watermark ? 'fill-current' : 'fill-tertiary'" />
	</svg>
</template>

<style scoped>
/* 128 wide and centred 34 in from the card's right edge and 22 up from its foot, as Android places it. */
.cat-watermark {
	position: absolute;
	right: -30px;
	bottom: -29.2px;
	width: 128px;
	opacity: 0.14;
	rotate: -12deg;
}
</style>
