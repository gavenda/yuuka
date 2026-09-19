<script setup lang="ts">
/** A Material 3 switch: a small handle in an outlined track when off, a larger one in a filled track when on. */
defineProps<{ modelValue: boolean; label: string }>();
defineEmits<{ 'update:modelValue': [value: boolean] }>();
</script>

<template>
	<button
		type="button"
		role="switch"
		:aria-checked="modelValue"
		:aria-label="label"
		class="focus-ring relative inline-flex h-8 w-13 shrink-0 cursor-pointer items-center rounded-full border-2 transition-colors"
		:class="modelValue ? 'border-primary bg-primary' : 'border-outline bg-surface-container-highest'"
		@click="$emit('update:modelValue', !modelValue)"
	>
		<span
			class="absolute top-1/2 -translate-y-1/2 rounded-full transition-[left,width,height,background-color] duration-200 ease-standard"
			:class="modelValue ? 'left-[22px] size-6 bg-on-primary' : 'left-1.5 size-4 bg-outline'"
		/>
	</button>
</template>
