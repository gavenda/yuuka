<script setup lang="ts" generic="T extends string | number">
import AppIcon from '@/components/AppIcon.vue';
import type { IconPath } from '@/lib/icons';

/**
 * Material 3 Expressive's connected button group (`ConnectedButtonGroup.kt`): a full-width row of toggle
 * buttons where exactly one of the options is selected. Each is a radio button to assistive tech. A
 * button's place in the row gives it its colour — primary, secondary, tertiary, and round again — which
 * is the stylesheet's doing (`.connected-group`), so no caller passes one. An option's `icon` leads its label;
 * with `iconOnly` it stands alone, the button is only as wide as that takes, and the label is read out instead.
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
	<div class="connected-group" role="radiogroup" :aria-label="label">
		<button
			v-for="option in options"
			:key="option.value"
			type="button"
			role="radio"
			:aria-checked="modelValue === option.value"
			:aria-label="option.iconOnly ? option.label : undefined"
			:data-icon-only="option.iconOnly || undefined"
			:disabled="disabled"
			@click="$emit('update:modelValue', option.value)"
		>
			<AppIcon v-if="option.icon" :icon="option.icon" :size="20" />
			<template v-if="!option.iconOnly">{{ option.label }}</template>
		</button>
	</div>
</template>
