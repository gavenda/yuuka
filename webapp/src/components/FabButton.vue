<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { registerFab, type FabAction } from '@/lib/fab';
import { ADD, type IconPath } from '@/lib/icons';
import { onBeforeUnmount, reactive, ref, watch } from 'vue';

/**
 * The one action a screen leads with (`ScreenFab` on Android). From `sm` up the navigation rail shows it
 * (see `NavRail`), so this only draws the floating button that sits above the bottom bar on a phone; either
 * way it is the same action, registered here and run through `click`. With `actions` the button opens a
 * menu of them instead, for a screen whose leading action is really a few.
 */
const props = withDefaults(defineProps<{ label: string; icon?: IconPath; disabled?: boolean; actions?: FabAction[] }>(), {
	icon: ADD,
	disabled: false,
	actions: () => [],
});
const emit = defineEmits<{ click: [] }>();

const entry = reactive({
	label: props.label,
	icon: props.icon,
	disabled: props.disabled,
	run: () => emit('click'),
	actions: props.actions,
});
watch(
	() => [props.label, props.icon, props.disabled, props.actions] as const,
	([label, icon, disabled, actions]) => {
		entry.label = label;
		entry.icon = icon;
		entry.disabled = disabled;
		entry.actions = actions;
	},
);

const unregister = registerFab(entry);
onBeforeUnmount(unregister);

const menuOpen = ref(false);

function press(): void {
	if (props.actions.length) menuOpen.value = !menuOpen.value;
	else emit('click');
}

function choose(action: FabAction): void {
	menuOpen.value = false;
	action.run();
}
</script>

<template>
	<div class="fixed right-4 bottom-24 z-20 flex flex-col items-end gap-2 sm:hidden">
		<div v-if="menuOpen" class="fixed inset-0" aria-hidden="true" @click="menuOpen = false" />
		<div v-if="menuOpen" role="menu" class="menu relative min-w-48">
			<button v-for="action in actions" :key="action.label" type="button" role="menuitem" class="menu-item" @click="choose(action)">
				<AppIcon :icon="action.icon" />
				{{ action.label }}
			</button>
		</div>

		<button
			type="button"
			class="fab relative"
			:disabled="disabled"
			:aria-haspopup="actions.length ? 'menu' : undefined"
			:aria-expanded="actions.length ? menuOpen : undefined"
			@click="press"
		>
			<AppIcon :icon="icon" />
			{{ label }}
		</button>
	</div>
</template>
