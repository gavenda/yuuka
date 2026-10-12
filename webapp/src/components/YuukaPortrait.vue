<script setup lang="ts">
import { scallopPath } from '@/lib/chart';
import type { YuukaMood } from '@/lib/yuuka';
import { useId } from 'vue';

/**
 * Yuuka herself, where a screen has nothing else to show (`YuukaPortrait.kt`) — an empty list, or the end of
 * one — and in About. A scalloped badge — the hem of her pyjamas — in the secondary container, which is their
 * blue, masks her: from its middle down she is cut off at its edge, and her head, bow and halo stand clear above
 * it. She is decoration, like the cat print, so she is hidden from assistive tech: the words beside her say what
 * the screen has to. Never beside a figure, in any other form, a chart or any other dialog. 192 wide unless a class says
 * otherwise.
 */
defineProps<{ mood: YuukaMood }>();

/** The art's own size, and the badge in it: as wide as the art, sitting on its foot. */
const WIDTH = 620;
const HEIGHT = 740;
const BADGE = scallopPath(WIDTH / 2, HEIGHT - WIDTH / 2, WIDTH / 2);

const mask = useId();
</script>

<template>
	<svg :viewBox="`0 0 ${WIDTH} ${HEIGHT}`" class="pointer-events-none w-48 shrink-0" aria-hidden="true">
		<!-- All of her above the badge's middle, and beneath it only what is inside the badge. -->
		<clipPath :id="mask">
			<rect :width="WIDTH" :height="HEIGHT - WIDTH / 2" />
			<path :d="BADGE" />
		</clipPath>
		<path :d="BADGE" class="fill-secondary-container" />
		<image :href="`/yuuka/${mood}.webp`" :width="WIDTH" :height="HEIGHT" :clip-path="`url(#${mask})`" />
	</svg>
</template>
