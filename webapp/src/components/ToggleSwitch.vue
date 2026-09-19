<script setup lang="ts">
/** A Material 3 switch: a small handle in an outlined track when off, a larger one in a filled track when on, with a check mark in it so the state doesn't rest on colour alone. */
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
			class="absolute top-1/2 grid -translate-y-1/2 place-items-center rounded-full transition-[left,width,height,background-color] duration-200 ease-standard"
			:class="modelValue ? 'left-[22px] size-6 bg-on-primary' : 'left-1.5 size-4 bg-outline'"
		>
			<svg
				viewBox="0 0 24 24"
				class="size-4 text-on-primary-container transition-opacity duration-200"
				:class="modelValue ? 'opacity-100' : 'opacity-0'"
				fill="currentColor"
				aria-hidden="true"
			>
				<path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" />
			</svg>
		</span>
	</button>
</template>
