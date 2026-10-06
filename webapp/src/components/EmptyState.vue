<script setup lang="ts">
/** What a screen says when it has nothing to list (`EmptyState.kt`): an outlined card with a line or two, and room for an action. */
defineProps<{ title: string; description?: string }>();
</script>

<template>
	<div class="flex flex-col items-center gap-1 rounded-lg border border-outline-variant px-6 py-8 text-center text-on-surface">
		<p class="type-title-small">{{ title }}</p>
		<p v-if="description" class="type-body-small">{{ description }}</p>
		<div v-if="$slots.default" class="pt-3"><slot /></div>
	</div>
</template>
