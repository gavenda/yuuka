<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { ADD, ARCHIVE, BALANCE, DELETE, EDIT, PAUSE, PLAY_ARROW, UNARCHIVE, type IconPath } from '@/lib/icons';
import { computed } from 'vue';

export type ActionIconName = 'edit' | 'archive' | 'restore' | 'delete' | 'adjust' | 'pause' | 'resume' | 'add';

/** One of a row's actions behind its swipe (`ActionIconButton` on Android): a narrow filled icon button, primary, or error when it destroys something. */
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
	add: ADD,
};

const icon = computed(() => ICONS[props.icon]);
</script>

<template>
	<!-- Icon-only, so the label has to be carried by `aria-label`; `title` gives
	     sighted users the same word on hover. -->
	<button
		type="button"
		class="btn-icon-narrow"
		:class="danger ? 'btn-icon-filled-danger' : 'btn-icon-filled'"
		:aria-label="label"
		:title="label"
		:disabled="disabled"
	>
		<AppIcon :icon="icon" />
	</button>
</template>
