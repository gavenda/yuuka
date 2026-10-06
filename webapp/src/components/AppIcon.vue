<script setup lang="ts">
import type { IconPath } from '@/lib/icons';
import { computed } from 'vue';

/** One Material icon from `lib/icons.ts`, in the current text colour. Decorative: whatever it sits in carries the label. */
const props = withDefaults(defineProps<{ icon: IconPath; size?: number }>(), { size: 24 });

const d = computed(() => (typeof props.icon === 'string' ? props.icon : props.icon.d));
const viewBox = computed(() => (typeof props.icon === 'string' ? '0 0 24 24' : props.icon.viewBox));
</script>

<template>
	<svg :viewBox="viewBox" :width="size" :height="size" class="shrink-0" fill="currentColor" aria-hidden="true">
		<path :d="d" />
	</svg>
</template>
