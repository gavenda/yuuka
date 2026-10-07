<script setup lang="ts">
import CatMark from '@/components/CatMark.vue';
import { onBeforeUnmount, onMounted, ref } from 'vue';

/**
 * What a screen says when it has nothing to list (`EmptyState.kt`): the cat, a line or two, and room for an
 * action, straight on the page. With `fill`, where it is all the screen holds, it takes the height left under
 * whatever is above it — less the room the page keeps clear beneath — and sits in the middle of that, as
 * `fillParentMaxHeight()` does on Android.
 */
const props = defineProps<{ title: string; description?: string; fill?: boolean }>();

const root = ref<HTMLElement | null>(null);
const minHeight = ref<string>();

function measure(): void {
	const el = root.value;
	if (!el) return;
	// What is kept clear below: the element's own margin, and the padding of everything it sits in.
	let below = parseFloat(getComputedStyle(el).marginBottom) || 0;
	for (let parent = el.parentElement; parent; parent = parent.parentElement) {
		below += parseFloat(getComputedStyle(parent).paddingBottom) || 0;
	}
	const top = el.getBoundingClientRect().top + window.scrollY;
	minHeight.value = `${Math.max(0, window.innerHeight - top - below)}px`;
}

onMounted(() => {
	if (!props.fill) return;
	measure();
	window.addEventListener('resize', measure);
});

onBeforeUnmount(() => window.removeEventListener('resize', measure));
</script>

<template>
	<div ref="root" class="flex flex-col items-center justify-center gap-1 px-6 py-8 text-center text-on-surface" :style="{ minHeight }">
		<CatMark class="mb-3" />
		<p class="type-title-small">{{ title }}</p>
		<p v-if="description" class="type-body-small">{{ description }}</p>
		<div v-if="$slots.default" class="pt-3"><slot /></div>
	</div>
</template>
