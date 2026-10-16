<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { registerFab } from '@/lib/fab';
import { ADD, type IconPath } from '@/lib/icons';
import { onBeforeUnmount, reactive, toRefs } from 'vue';

/**
 * The one action a screen leads with (`ScreenFab` on Android). From `sm` up the navigation rail shows it
 * (see `NavRail`), so this only draws the floating button that sits above the bottom bar on a phone; either
 * way it is the same action, registered here and run through `click`.
 */
const props = withDefaults(defineProps<{ label: string; icon?: IconPath }>(), { icon: () => ADD });
const emit = defineEmits<{ click: [] }>();

// Refs into the props, so the rail's entry follows a label or icon that changes.
const unregister = registerFab(reactive({ ...toRefs(props), run: () => emit('click') }));
onBeforeUnmount(unregister);
</script>

<template>
	<div class="fixed right-4 bottom-24 z-20 sm:hidden">
		<button type="button" class="fab relative" @click="emit('click')">
			<AppIcon :icon="icon" />
			{{ label }}
		</button>
	</div>
</template>
