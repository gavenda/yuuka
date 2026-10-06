<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import type { IconPath } from '@/lib/icons';
import { computed } from 'vue';
import { useLink, useRoute } from 'vue-router';

/**
 * One entry in the navigation rail, drawn so it can morph. The slim and the open rail are the same
 * element with different geometry, and CSS (`.rail-item` in `style.css`) moves it between them: the pill
 * grows from a small capsule to one that hugs the icon and its label, the icon settles into its new height,
 * and the label glides from under the icon to beside it. Nothing is swapped in or out, so nothing can pop.
 *
 * A `destination` keeps its label under the icon while slim. An `action` is icon-only while slim and
 * shows its label once the rail opens.
 */
const props = withDefaults(
	defineProps<{
		icon: IconPath;
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
const route = useRoute();
// A screen opened from another (the account types, from Settings) leaves the one it came from marked.
const isActive = computed(() => props.to !== undefined && (link.isExactActive.value || route.meta.parent === props.to));

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
		<!-- What gives the item its open width; never seen, never read out. -->
		<span class="rail-item-ghost" aria-hidden="true">{{ label }}</span>
		<span class="rail-item-pill" />
		<AppIcon :icon="icon" class="rail-item-icon" />
		<span class="rail-item-label">{{ label }}</span>
	</component>
</template>
