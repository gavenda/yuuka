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
	<!-- The full width of the page, as on Android: an outlined card with the arrows at its ends and the month between them. -->
	<div class="flex w-full items-center gap-1 rounded-md border border-outline-variant px-1">
		<button type="button" class="btn-icon" aria-label="Previous month" @click="shift(-1)">
			<svg viewBox="0 0 20 20" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
				<path d="M12 4l-6 6 6 6" stroke-linecap="round" stroke-linejoin="round" />
			</svg>
		</button>

		<span class="type-label-large min-w-0 flex-1 py-3 text-center text-on-surface">{{ label }}</span>

		<button type="button" class="btn-icon" aria-label="Next month" @click="shift(1)">
			<svg viewBox="0 0 20 20" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
				<path d="M8 4l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
			</svg>
		</button>

		<button v-if="!isCurrent" type="button" class="btn-text btn-sm" @click="emit('update:modelValue', currentMonth())">Today</button>
	</div>
</template>
