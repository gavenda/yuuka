<script setup lang="ts">
/**
 * A screen's top bar: what stays put while the page scrolls beneath it, as the bar in a screen's own
 * `Scaffold` does on Android. It is drawn in the shell's sticky strip (`#top-bar` in `App.vue`) rather than
 * in the page, so it sits above the unsent-changes banner and keeps its place between screens. A window
 * with a rail has no titled bar — the rail marks the destination — so this holds only what a screen pins
 * there itself: the month switcher, a search field.
 */
</script>

<template>
	<Teleport defer to="#top-bar">
		<div class="px-4 pt-2 pb-4 sm:pt-4"><slot /></div>
	</Teleport>
</template>
