<script setup lang="ts">
import SpeechBubble from '@/components/SpeechBubble.vue';
import YuukaPortrait from '@/components/YuukaPortrait.vue';
import { t } from '@/i18n';

/**
 * The end of a list that pages itself, and only once there is no more of it to load (`ListEnd` on Android):
 * Yuuka, asleep over closed books, saying so, so the last row is not mistaken for a page that stopped short.
 * Transactions and the investment activity end with it.
 */
</script>

<template>
	<div class="flex items-end justify-center gap-1 px-4 pt-6 pb-2">
		<YuukaPortrait mood="asleep" class="w-24!" />
		<!-- As tall as the badge she is in, so the tail is level with her face. -->
		<div class="flex min-h-24 min-w-0 items-center">
			<SpeechBubble>{{ t('transactions.end') }}</SpeechBubble>
		</div>
	</div>
</template>
