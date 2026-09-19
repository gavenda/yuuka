<script setup lang="ts">
import { computed, ref, watch } from 'vue';

const props = withDefaults(
	defineProps<{
		name: string;
		logoUrl?: string | null;
		/** Inverts the logo's colours in dark mode, for a dark mark that would otherwise disappear. */
		invertDark?: boolean;
		/** The logo's height in pixels. Width follows the image's own proportions. */
		size?: number;
		/** How far a wide logo may stretch before it is scaled down, as a multiple of the height. */
		maxAspect?: number;
	}>(),
	{ logoUrl: null, invertDark: false, size: 40, maxAspect: 2.5 },
);

/**
 * The URL points at someone else's server, so it can 404, expire or simply not
 * be an image. Falling back to the account's initial keeps the row intact
 * instead of leaving a broken-image glyph.
 */
const failed = ref(false);

watch(
	() => props.logoUrl,
	() => {
		failed.value = false;
	},
);

const showImage = computed(() => Boolean(props.logoUrl) && !failed.value);
const initial = computed(() => props.name.trim().charAt(0).toUpperCase() || '?');

/**
 * Height is fixed and width is free, so a square icon stays square and a wide
 * wordmark stays wide — forcing both into one box letterboxes the wordmark into
 * a sliver and leaves the square swimming in padding.
 *
 * The floor keeps a square's worth of space reserved, so rows do not jump as
 * images arrive; the ceiling stops a very wide mark from crowding out the name.
 */
const imageStyle = computed(() => ({
	height: `${props.size}px`,
	minWidth: `${props.size}px`,
	maxWidth: `${Math.round(props.size * props.maxAspect)}px`,
}));

/** The fallback is a letter avatar, so it stays square whatever the logo would have been. */
const fallbackStyle = computed(() => ({ width: `${props.size}px`, height: `${props.size}px` }));
</script>

<template>
	<!-- No ring or background on the image: a transparent logo would otherwise
	     sit inside a visible box that is not part of the mark. -->
	<img
		v-if="showImage"
		:src="logoUrl!"
		:alt="`${name} logo`"
		:style="imageStyle"
		class="shrink-0 rounded object-contain"
		:class="{ 'dark:invert': invertDark }"
		loading="lazy"
		decoding="async"
		referrerpolicy="no-referrer"
		@error="failed = true"
	/>

	<span
		v-else
		:style="fallbackStyle"
		class="grid shrink-0 place-items-center rounded-sm bg-surface-container-high text-sm font-medium text-on-surface-variant"
		aria-hidden="true"
	>
		{{ initial }}
	</span>
</template>
