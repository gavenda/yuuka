<script setup lang="ts">
import { ref, watch } from 'vue';

const props = withDefaults(defineProps<{ logoUrl: string | null; invertDark?: boolean }>(), { invertDark: false });

/** A remote URL can 404 or not be an image; the card then simply has no mark. */
const failed = ref(false);

watch(
	() => props.logoUrl,
	() => {
		failed.value = false;
	},
);

/**
 * Opaque at the right edge and fading out before the middle, so the mark reads
 * as part of the card rather than a picture pasted on top of it — and the name
 * and balance on the left stay over plain surface.
 *
 * Both spellings: Safari still wants the prefixed property.
 */
</script>

<template>
	<!-- Decorative: the account's name is already beside it, so this is hidden
	     from assistive tech rather than announced twice. -->
	<img
		v-if="logoUrl && !failed"
		:src="logoUrl"
		alt=""
		aria-hidden="true"
		class="pointer-events-none inset-y-0 h-5 object-contain object-right select-none"
		:class="{ 'dark:invert': invertDark }"
		loading="lazy"
		decoding="async"
		referrerpolicy="no-referrer"
		@error="failed = true"
	/>
</template>
