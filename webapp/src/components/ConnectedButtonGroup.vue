<script setup lang="ts" generic="T extends string | number">
/** A connected button group of toggle buttons with exactly one selected. */
defineProps<{
	modelValue: T;
	options: { value: T; label: string }[];
	/** Names the group for assistive tech when no visible label sits above it. */
	label?: string;
	/** Shorter, for placing beside other controls on a row. */
	dense?: boolean;
}>();

defineEmits<{ 'update:modelValue': [value: T] }>();
</script>

<template>
	<div class="connected-group" :class="{ 'connected-group-dense': dense }" role="group" :aria-label="label">
		<button
			v-for="option in options"
			:key="option.value"
			type="button"
			:aria-pressed="modelValue === option.value"
			@click="$emit('update:modelValue', option.value)"
		>
			{{ option.label }}
		</button>
	</div>
</template>
