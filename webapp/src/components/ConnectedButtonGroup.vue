<script setup lang="ts" generic="T extends string | number">
import AppIcon from '@/components/AppIcon.vue';
import type { IconPath } from '@/lib/icons';
import { Button, ButtonGroup, IconButton } from 'tailwind-material3';

/**
 * Material 3 Expressive's connected button group (`ConnectedButtonGroup.kt`), tailwind-material3's connected
 * `ButtonGroup`: a full-width row of toggle buttons where exactly one of the options is selected. Every button takes
 * Material's own toggle colours — the surface container at rest, primary when selected — which is the filled toggle's
 * doing, so no caller passes one. An option's `icon` leads its label; with `iconOnly` it stands alone as an icon
 * button, only as wide as that takes, and the label is read out instead.
 */
defineProps<{
	modelValue: T;
	options: { value: T; label: string; icon?: IconPath; iconOnly?: boolean }[];
	/** Names the group for assistive tech when no visible label sits above it. */
	label?: string;
	disabled?: boolean;
}>();

defineEmits<{ 'update:modelValue': [value: T] }>();
</script>

<template>
	<ButtonGroup
		variant="connected"
		selection="single"
		required
		:model-value="modelValue"
		:aria-label="label"
		class="w-full"
		@update:model-value="$emit('update:modelValue', $event as T)"
	>
		<template v-for="option in options" :key="option.value">
			<IconButton v-if="option.iconOnly && option.icon" :value="option.value" :label="option.label" :disabled="disabled">
				<AppIcon :icon="option.icon" />
			</IconButton>
			<Button v-else :value="option.value" :disabled="disabled" class="min-w-0 flex-1">
				<template v-if="option.icon" #icon><AppIcon :icon="option.icon" /></template>
				{{ option.label }}
			</Button>
		</template>
	</ButtonGroup>
</template>
