<script setup lang="ts">
/**
 * Something Yuuka says, in a bubble with a tail on its start side (`SpeechBubble` in `YuukaPortrait.kt`): it goes
 * after her portrait in a row, where the tail, halfway up the bubble, points back at her. About and the end of the transaction list have the only ones.
 */
</script>

<template>
	<p class="speech-bubble type-body-medium text-on-surface"><slot /></p>
</template>

<style scoped>
/* A rounded box, set in from its start side by the 8px the tail crosses. */
.speech-bubble {
	position: relative;
	margin-inline-start: 8px;
	border-radius: 20px;
	background-color: var(--color-surface-container-high);
	padding: 12px 16px;
	text-align: start;
}

.speech-bubble::before {
	content: '';
	position: absolute;
	inset-block-start: 50%;
	inset-inline-start: -8px;
	width: 8px;
	height: 16px;
	translate: 0 -50%;
	background-color: inherit;
	clip-path: polygon(100% 0, 0 50%, 100% 100%);
}

.speech-bubble:dir(rtl)::before {
	clip-path: polygon(0 0, 100% 50%, 0 100%);
}
</style>
