<script setup lang="ts">
/**
 * One setting: what it is and why on the left, the control on the right. On a wide screen that reads as a
 * row in a list; on a phone the control drops beneath the text, unless it is small enough (`inline`, a
 * switch) to stay beside it. Give `for` the id of the control so the title labels it.
 */
defineProps<{ title: string; description?: string; for?: string; inline?: boolean }>();
</script>

<template>
	<div
		class="grid gap-3 py-4 sm:items-center sm:gap-8"
		:class="inline ? 'grid-cols-[minmax(0,1fr)_auto] items-center' : 'sm:grid-cols-[minmax(0,1fr)_minmax(0,18rem)]'"
	>
		<div class="min-w-0">
			<component :is="$props.for ? 'label' : 'p'" :for="$props.for" class="block text-sm text-on-surface">{{ title }}</component>
			<p v-if="description" class="mt-0.5 text-xs text-on-surface-variant">{{ description }}</p>
		</div>

		<div class="min-w-0"><slot /></div>
	</div>
</template>
