<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { CHECK } from '@/lib/icons';

/**
 * A Material 3 switch: a small handle in an outlined track when off, a larger one in a filled track when on.
 * `icon` puts a check mark in the handle while it is on, so the state doesn't rest on colour alone
 * (`YuukaSwitch`); the settings screens' own switches go without one, as Android's do.
 */
withDefaults(defineProps<{ modelValue: boolean; label: string; icon?: boolean; disabled?: boolean }>(), { icon: true, disabled: false });
defineEmits<{ 'update:modelValue': [value: boolean] }>();
</script>

<template>
	<button
		type="button"
		role="switch"
		:aria-checked="modelValue"
		:aria-label="label"
		:disabled="disabled"
		class="focus-ring relative inline-flex h-8 w-13 shrink-0 cursor-pointer items-center rounded-full border-2 transition-colors disabled:cursor-not-allowed disabled:opacity-38"
		:class="modelValue ? 'border-primary bg-primary' : 'border-outline bg-surface-container-highest'"
		@click.stop="$emit('update:modelValue', !modelValue)"
	>
		<span
			class="absolute top-1/2 grid -translate-y-1/2 place-items-center rounded-full transition-[left,width,height,background-color] duration-200 ease-standard"
			:class="modelValue ? 'left-[22px] size-6 bg-on-primary' : 'left-1.5 size-4 bg-outline'"
		>
			<AppIcon
				v-if="icon"
				:icon="CHECK"
				:size="16"
				class="text-primary transition-opacity duration-200"
				:class="modelValue ? 'opacity-100' : 'opacity-0'"
			/>
		</span>
	</button>
</template>
