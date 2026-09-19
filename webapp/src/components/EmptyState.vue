<script setup lang="ts">
defineProps<{ title: string; description?: string }>();
</script>

<template>
	<div class="flex flex-col items-center justify-center rounded-md border border-dashed border-outline px-6 py-12 text-center">
		<p class="text-sm font-medium text-on-surface">{{ title }}</p>
		<p v-if="description" class="mt-1 max-w-sm text-sm text-on-surface-variant">{{ description }}</p>
		<div class="mt-4"><slot /></div>
	</div>
</template>
