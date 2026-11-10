<script setup lang="ts">
import { Switch } from 'tailwind-material3';

/**
 * A Material 3 switch (tailwind-material3's `Switch`). `icon` puts a check mark in the handle while it is on, so the
 * state doesn't rest on colour alone (`YuukaSwitch`); the settings screens' own switches go without one, as
 * Android's do.
 */
withDefaults(defineProps<{ modelValue: boolean; label: string; icon?: boolean; disabled?: boolean }>(), { icon: true, disabled: false });
defineEmits<{ 'update:modelValue': [value: boolean] }>();
</script>

<template>
	<Switch
		:model-value="modelValue"
		:aria-label="label"
		:icons="icon ? 'selected' : 'none'"
		:disabled="disabled"
		class="shrink-0"
		@click.stop
		@update:model-value="$emit('update:modelValue', $event)"
	/>
</template>
