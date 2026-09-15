<script setup lang="ts">
defineProps<{ title: string; description?: string }>();
</script>

<template>
	<div
		class="flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 px-6 py-12 text-center dark:border-slate-700"
	>
		<p class="text-sm font-medium text-slate-700 dark:text-slate-300">{{ title }}</p>
		<p v-if="description" class="mt-1 max-w-sm text-sm text-slate-500 dark:text-slate-400">{{ description }}</p>
		<div class="mt-4"><slot /></div>
	</div>
</template>
