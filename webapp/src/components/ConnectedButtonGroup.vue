<script setup lang="ts" generic="T extends string | number">
/**
 * Material 3 Expressive's connected button group (`ConnectedButtonGroup.kt`): a full-width row of toggle
 * buttons where exactly one of the options is selected. Each is a radio button to assistive tech.
 */
defineProps<{
	modelValue: T;
	options: { value: T; label: string }[];
	/** Names the group for assistive tech when no visible label sits above it. */
	label?: string;
	disabled?: boolean;
}>();

defineEmits<{ 'update:modelValue': [value: T] }>();
</script>

<template>
	<div class="connected-group" role="radiogroup" :aria-label="label">
		<button
			v-for="option in options"
			:key="option.value"
			type="button"
			role="radio"
			:aria-checked="modelValue === option.value"
			:disabled="disabled"
			@click="$emit('update:modelValue', option.value)"
		>
			{{ option.label }}
		</button>
	</div>
</template>
