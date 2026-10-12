<script setup lang="ts">
import { CAT_EARS, CAT_EYES, CAT_HEAD } from '@/lib/cat';

/**
 * One motif of the cat print as a watermark (`Modifier.catWatermark`): drawn in the current text colour, large
 * and faint, let into the bottom corner of a card and cut off by its edge — the card must be `relative` and
 * `overflow-hidden`. Decoration, so it is hidden from assistive tech.
 */
</script>

<template>
	<svg viewBox="0 0 100 80" class="cat-watermark pointer-events-none shrink-0" aria-hidden="true">
		<path :d="CAT_HEAD" fill="none" class="stroke-current" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round" />
		<path :d="CAT_EYES" class="fill-current" />
		<path :d="CAT_EARS" class="fill-current" />
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
