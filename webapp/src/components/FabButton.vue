<script setup lang="ts">
import { registerFab } from '@/lib/fab';
import { ADD } from '@/lib/icons';
import { onBeforeUnmount, reactive, watch } from 'vue';

/**
 * The one action a screen leads with. From `sm` up the navigation rail shows it (see `NavRail`), so this
 * only draws the floating button that sits above the bottom bar on a phone; either way it is the same
 * action, registered here and run through `click`. The icon is an add sign unless the action is something
 * else, such as saving.
 */
const props = withDefaults(defineProps<{ label: string; icon?: string; disabled?: boolean }>(), { icon: ADD, disabled: false });
const emit = defineEmits<{ click: [] }>();

const entry = reactive({ label: props.label, icon: props.icon, disabled: props.disabled, run: () => emit('click') });
watch(
	() => [props.label, props.icon, props.disabled] as const,
	([label, icon, disabled]) => {
		entry.label = label;
		entry.icon = icon;
		entry.disabled = disabled;
	},
);

const unregister = registerFab(entry);
onBeforeUnmount(unregister);
</script>

<template>
	<button type="button" class="fab fixed right-4 bottom-24 z-20 sm:hidden" :disabled="disabled" @click="emit('click')">
		<svg viewBox="0 0 24 24" class="h-6 w-6" fill="currentColor" aria-hidden="true">
			<path :d="icon" />
		</svg>
		{{ label }}
	</button>
</template>
