<script setup lang="ts">
import { computed } from 'vue';

export type ActionIconName = 'edit' | 'archive' | 'restore' | 'delete' | 'adjust' | 'pause' | 'resume';

const props = withDefaults(defineProps<{ icon: ActionIconName; label: string; danger?: boolean; disabled?: boolean }>(), {
	danger: false,
	disabled: false,
});

/**
 * Drawn in a shared 20-unit box at a single stroke weight, so the row of
 * actions reads as one set rather than four borrowed glyphs.
 */
const PATHS: Record<ActionIconName, string[]> = {
	// Pencil: body quadrilateral plus the ferrule line across it.
	edit: ['M13.2 3.6a1.7 1.7 0 0 1 2.4 2.4l-8.3 8.3-3.2.8.8-3.2z', 'M11.9 4.9l2.4 2.4'],
	// Box with a lid and a label slot.
	archive: ['M2.75 4.75h14.5v3H2.75z', 'M4.25 7.75v7.5h11.5v-7.5', 'M8 10.75h4'],
	// A u-turn arrow: bringing something back out.
	restore: ['M6.5 5.5 3 9l3.5 3.5', 'M3 9h9a4 4 0 1 1 0 8H8'],
	// Bin with lid, handle and two slots.
	delete: ['M4 6h12M8 6V4h4v2M6 6l1 10h6l1-10', 'M9 9v4.5M11 9v4.5'],
	// A balance scale: post, beam and two pans.
	// Two bars.
	pause: ['M7.5 4.5v11', 'M12.5 4.5v11'],
	// A triangle pointing right.
	resume: ['M6.5 4.5l9 5.5-9 5.5z'],
	adjust: ['M10 3v14', 'M4 6h12', 'M4 6l-2 4.5a2.5 2.5 0 0 0 5 0z', 'M16 6l-2 4.5a2.5 2.5 0 0 0 5 0z', 'M7 17h6'],
};

const paths = computed(() => PATHS[props.icon]);
</script>

<template>
	<!-- Icon-only, so the label has to be carried by `aria-label`; `title` gives
	     sighted users the same word on hover. -->
	<button type="button" class="btn-icon-sm" :class="danger ? 'text-error' : ''" :aria-label="label" :title="label" :disabled="disabled">
		<svg viewBox="0 0 20 20" class="h-[18px] w-[18px]" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
			<path v-for="d in paths" :key="d" :d="d" stroke-linecap="round" stroke-linejoin="round" />
		</svg>
	</button>
</template>
