<script setup lang="ts">
import { CAT_EARS, CAT_EYES, CAT_HEAD } from '@/lib/cat';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

/**
 * The cat print as a backdrop (`Modifier.catPattern`), as it is over the fabric: the motif in staggered rows
 * laid out from the middle of the element, each tilted a little differently from its neighbours. The colours are
 * translucent, so it shows over whatever is already there. Position and size it with classes; it is decoration,
 * so it takes no clicks and is hidden from assistive tech.
 */
const props = withDefaults(
	defineProps<{
		/** How big the print is drawn: a whole screen's backdrop, or a strip or a card. */
		print?: 'large' | 'small';
		/** Where it thins out: towards the middle, from the left edge to nothing at the right, or nowhere. */
		fade?: 'centre' | 'trailing' | 'none';
	}>(),
	{ print: 'large', fade: 'centre' },
);

/** The motif's width and the cell each one has to itself. */
const PRINTS = {
	large: { motifWidth: 72, cellWidth: 132, cellHeight: 112 },
	small: { motifWidth: 30, cellWidth: 58, cellHeight: 46 },
};
/** The degrees a motif is turned by, taken in turn along a row and offset from one row to the next. */
const MOTIF_TILTS = [-14, 9, -6, 13];

const root = ref<SVGSVGElement | null>(null);
const area = ref({ width: 0, height: 0 });
let observer: ResizeObserver | undefined;

function measure(): void {
	const box = root.value?.getBoundingClientRect();
	if (box) area.value = { width: box.width, height: box.height };
}

const motifs = computed(() => {
	const { width, height } = area.value;
	const { motifWidth, cellWidth, cellHeight } = PRINTS[props.print];
	const placed: { key: string; transform: string; opacity: number }[] = [];
	if (!width || !height) return placed;

	const centreX = width / 2;
	const centreY = height / 2;
	const rows = Math.ceil(centreY / cellHeight) + 1;
	const columns = Math.ceil(centreX / cellWidth) + 1;
	for (let row = -rows; row <= rows; row++) {
		const shift = row % 2 === 0 ? 0 : 0.5;
		for (let column = -columns; column <= columns; column++) {
			const x = centreX + (column + shift) * cellWidth;
			const y = centreY + row * cellHeight;
			let opacity = 1;
			if (props.fade === 'centre') {
				// 0 in the middle, 1 at the nearer edges.
				const distance = Math.hypot((x - centreX) / centreX, (y - centreY) / centreY);
				opacity = Math.min(Math.max((distance - 0.4) / 0.5, 0), 1);
			} else if (props.fade === 'trailing') {
				opacity = Math.min(Math.max(1 - x / width, 0), 1);
			}
			if (opacity === 0) continue;
			const tilt = MOTIF_TILTS[(((column + 2 * row) % 4) + 4) % 4];
			placed.push({
				key: `${row}:${column}`,
				transform: `translate(${x} ${y}) rotate(${tilt}) scale(${motifWidth / 100}) translate(-50 -40)`,
				opacity,
			});
		}
	}
	return placed;
});

onMounted(() => {
	measure();
	if (root.value && typeof ResizeObserver !== 'undefined') {
		observer = new ResizeObserver(measure);
		observer.observe(root.value);
	}
});

onBeforeUnmount(() => observer?.disconnect());
</script>

<template>
	<svg ref="root" class="pointer-events-none overflow-hidden" aria-hidden="true">
		<g v-for="motif in motifs" :key="motif.key" :transform="motif.transform" :opacity="motif.opacity">
			<path :d="CAT_HEAD" class="fill-none stroke-primary" stroke-opacity="0.16" stroke-width="3.5" stroke-linejoin="round" />
			<path :d="CAT_EYES" class="fill-primary" fill-opacity="0.16" />
			<path :d="CAT_EARS" class="fill-tertiary" fill-opacity="0.28" />
		</g>
	</svg>
</template>
