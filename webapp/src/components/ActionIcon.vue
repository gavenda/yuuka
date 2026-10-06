<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { ARCHIVE, BALANCE, DELETE, EDIT, PAUSE, PLAY_ARROW, UNARCHIVE, type IconPath } from '@/lib/icons';
import { computed } from 'vue';

export type ActionIconName = 'edit' | 'archive' | 'restore' | 'delete' | 'adjust' | 'pause' | 'resume';

/** A row's icon-only actions, sharing one visual language (`ActionIconButton` on Android). */
const props = withDefaults(defineProps<{ icon: ActionIconName; label: string; danger?: boolean; disabled?: boolean }>(), {
	danger: false,
	disabled: false,
});

const ICONS: Record<ActionIconName, IconPath> = {
	edit: EDIT,
	archive: ARCHIVE,
	restore: UNARCHIVE,
	delete: DELETE,
	adjust: BALANCE,
	pause: PAUSE,
	resume: PLAY_ARROW,
};

const icon = computed(() => ICONS[props.icon]);
</script>

<template>
	<!-- Icon-only, so the label has to be carried by `aria-label`; `title` gives
	     sighted users the same word on hover. -->
	<button
		type="button"
		class="btn-icon m-1"
		:class="danger && !disabled ? 'text-error' : ''"
		:aria-label="label"
		:title="label"
		:disabled="disabled"
	>
		<AppIcon :icon="icon" />
	</button>
</template>
