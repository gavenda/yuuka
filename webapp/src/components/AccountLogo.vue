<script setup lang="ts">
import { bankByKey } from '@/lib/banks';
import { computed, ref, watch } from 'vue';

const props = withDefaults(
	defineProps<{
		name: string;
		/** A preset bank's key. When it names a bank, that bank's logo and inversion are used instead of the two below. */
		bank?: string | null;
		logoUrl?: string | null;
		/** Inverts the logo's colours in dark mode, for a dark mark that would otherwise disappear. */
		invertDark?: boolean;
		/** The logo's height in pixels. Width follows the image's own proportions. */
		size?: number;
		/** How far a wide logo may stretch before it is scaled down, as a multiple of the height. */
		maxAspect?: number;
	}>(),
	{ bank: null, logoUrl: null, invertDark: false, size: 40, maxAspect: 5 },
);

const preset = computed(() => bankByKey(props.bank));
const src = computed(() => preset.value?.logo ?? props.logoUrl);
/** A bank's dark-mode logo, drawn in place of `src` there; CSS picks between the two, so a theme switch needs no reload. */
const srcDark = computed(() => preset.value?.logoDark ?? null);
// A bank's dark-mode logo is already drawn for a dark surface, so only a single logo is inverted.
const invert = computed(() => (preset.value ? Boolean(preset.value.invertDark) && !srcDark.value : props.invertDark));

/**
 * The URL points at someone else's server, so it can 404, expire or simply not
 * be an image. Falling back to the account's initial keeps the row intact
 * instead of leaving a broken-image glyph.
 */
const failed = ref(false);

watch(
	() => src.value,
	() => {
		failed.value = false;
	},
);

const showImage = computed(() => Boolean(src.value) && !failed.value);
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
	<template v-if="showImage">
		<img
			:src="src!"
			:alt="`${preset?.name ?? name} logo`"
			:style="imageStyle"
			class="shrink-0 object-contain object-right"
			:class="{ 'dark:invert': invert, 'dark:hidden': srcDark }"
			loading="lazy"
			decoding="async"
			referrerpolicy="no-referrer"
			@error="failed = true"
		/>
		<img
			v-if="srcDark"
			:src="srcDark"
			:alt="`${preset?.name ?? name} logo`"
			:style="imageStyle"
			class="hidden shrink-0 object-contain object-right dark:inline"
			loading="lazy"
			decoding="async"
			@error="failed = true"
		/>
	</template>

	<span
		v-else
		:style="fallbackStyle"
		class="type-title-small grid shrink-0 place-items-center rounded-sm bg-primary-container text-on-primary-container"
		aria-hidden="true"
	>
		{{ initial }}
	</span>
</template>
