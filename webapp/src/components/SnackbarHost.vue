<script setup lang="ts">
import { dismissSnackbar, snackbarQueue } from '@/lib/snackbar';
import { Snackbar } from 'tailwind-material3';
import { computed, nextTick, ref, watch } from 'vue';
import { t } from '@/i18n';

/** Only the head of the queue is on screen; the next one takes over once it has gone. */
const current = computed(() => snackbarQueue[0] ?? null);

// Each message gets a snackbar of its own (keyed by id) that mounts shut and opens on the next tick, so it animates
// in; once it has animated out (`closed`) it leaves the queue and the next one takes its place.
const open = ref(false);
watch(
	() => current.value?.id,
	(id) => {
		open.value = false;
		if (id !== undefined) void nextTick(() => (open.value = current.value?.id === id));
	},
	{ immediate: true },
);

function act(): void {
	current.value?.action?.run();
}

function closed(id: number): void {
	dismissSnackbar(id);
}
</script>

<template>
	<!-- Clears the FAB on a phone, where the bottom bar and the FAB both sit under it. A message that goes by itself
	     needs no button to send it away; one that stays until dealt with does (`duration` null). -->
	<Snackbar
		v-if="current"
		:key="current.id"
		v-model:open="open"
		:message="current.message"
		:action-label="current.action?.label"
		:timeout="current.duration ?? 0"
		:closable="current.duration === null"
		:close-label="t('common.dismiss')"
		class="snackbar-host z-70! bottom-36! sm:bottom-2!"
		@action="act"
		@closed="closed(current.id)"
	/>
</template>
