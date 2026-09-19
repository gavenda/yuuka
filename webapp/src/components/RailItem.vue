<script setup lang="ts">
import { computed } from 'vue';
import { useLink } from 'vue-router';

/**
 * One entry in the navigation rail, drawn so it can morph. The slim and the open rail are the same
 * element with different geometry, and CSS (`.rail-item` in `style.css`) moves it between them: the pill
 * grows from a small capsule to a full row, the icon settles into its new height, and the label glides from
 * under the icon to beside it. Nothing is swapped in or out, so nothing can pop.
 *
 * A `destination` keeps its label under the icon while slim. An `action` is icon-only while slim and
 * shows its label once the rail opens.
 */
const props = withDefaults(
	defineProps<{
		/** A Material icon path in a 24-unit box. */
		icon: string;
		label: string;
		expanded: boolean;
		/** Makes it a link; without one it is a button and the parent handles `click`. */
		to?: string;
		variant?: 'destination' | 'action';
	}>(),
	{ variant: 'destination', to: undefined },
);

const emit = defineEmits<{ click: [] }>();

const link = useLink({ to: computed(() => props.to ?? '/') });
const isActive = computed(() => props.to !== undefined && link.isActive.value);

function onClick(event: MouseEvent): void {
	if (props.to !== undefined) link.navigate(event);
	emit('click');
}
</script>

<template>
	<component
		:is="to !== undefined ? 'a' : 'button'"
		class="rail-item"
		:href="to !== undefined ? link.href.value : undefined"
		:type="to !== undefined ? undefined : 'button'"
		:data-variant="variant"
		:data-expanded="expanded"
		:data-active="isActive"
		:aria-current="isActive ? 'page' : undefined"
		@click="onClick"
	>
		<span class="rail-item-pill" />
		<svg viewBox="0 0 24 24" class="rail-item-icon" fill="currentColor" aria-hidden="true">
			<path :d="icon" />
		</svg>
		<span class="rail-item-label">{{ label }}</span>
	</component>
</template>
