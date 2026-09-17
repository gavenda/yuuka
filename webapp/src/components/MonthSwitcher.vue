<script setup lang="ts">
import { addMonths, currentMonth, formatMonth } from '@/lib/dates';
import { computed } from 'vue';

const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{ 'update:modelValue': [string] }>();

const isCurrent = computed(() => props.modelValue === currentMonth());
const label = computed(() => formatMonth(props.modelValue));

const shift = (delta: number) => emit('update:modelValue', addMonths(props.modelValue, delta));
</script>

<template>
	<div class="inline-flex items-center gap-1 rounded-lg border border-slate-300 bg-white p-1 dark:border-slate-700 dark:bg-slate-900">
		<button type="button" class="btn-ghost px-2 py-1" aria-label="Previous month" @click="shift(-1)">
			<svg viewBox="0 0 20 20" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2">
				<path d="M12 4l-6 6 6 6" stroke-linecap="round" stroke-linejoin="round" />
			</svg>
		</button>

		<span class="min-w-[9.5rem] text-center text-sm font-medium text-slate-900 dark:text-slate-100">{{ label }}</span>

		<button type="button" class="btn-ghost px-2 py-1" aria-label="Next month" @click="shift(1)">
			<svg viewBox="0 0 20 20" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2">
				<path d="M8 4l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
			</svg>
		</button>

		<button v-if="!isCurrent" type="button" class="btn-ghost ml-1 px-2 py-1 text-xs" @click="emit('update:modelValue', currentMonth())">
			Today
		</button>
	</div>
</template>
