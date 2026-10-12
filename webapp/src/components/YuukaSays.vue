<script setup lang="ts">
import SpeechBubble from '@/components/SpeechBubble.vue';
import YuukaPortrait from '@/components/YuukaPortrait.vue';
import type { YuukaMood } from '@/lib/yuuka';

/**
 * Yuuka saying a line (`YuukaSays` in `YuukaPortrait.kt`): her portrait with a speech bubble beside it, the
 * bubble as tall as the badge she is in so its tail is level with her face. The line is the default slot.
 */
defineProps<{ mood: YuukaMood }>();
</script>

<template>
	<div class="flex items-end justify-center gap-1">
		<YuukaPortrait :mood="mood" class="w-24!" />
		<div class="flex min-h-24 min-w-0 items-center">
			<SpeechBubble><slot /></SpeechBubble>
		</div>
	</div>
</template>
