<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { CLOSE } from '@/lib/icons';
import { dismissSnackbar, snackbarQueue } from '@/lib/snackbar';
import { computed, onBeforeUnmount, watch } from 'vue';
import { t } from '@/i18n';

/** Only the head of the queue is on screen; the next one takes over once it has gone. */
const current = computed(() => snackbarQueue[0] ?? null);

let timer: ReturnType<typeof setTimeout> | undefined;

watch(
	current,
	(entry) => {
		clearTimeout(timer);
		if (!entry || entry.duration === null) return;
		timer = setTimeout(() => dismissSnackbar(entry.id), entry.duration);
	},
	{ immediate: true },
);

onBeforeUnmount(() => clearTimeout(timer));

function act(): void {
	const entry = current.value;
	if (!entry) return;
	dismissSnackbar(entry.id);
	entry.action?.run();
}
</script>

<template>
	<!-- Clears the FAB on a phone, where the bottom bar and the FAB both sit under it. -->
	<div class="snackbar-host pointer-events-none fixed inset-x-4 bottom-40 z-[70] flex justify-center sm:bottom-6">
		<Transition
			enter-active-class="transition duration-300 ease-emphasized-decelerate"
			enter-from-class="translate-y-4 opacity-0"
			leave-active-class="transition duration-150 ease-standard-accelerate"
			leave-to-class="opacity-0"
			mode="out-in"
		>
			<div
				v-if="current"
				:key="current.id"
				role="status"
				class="type-body-medium pointer-events-auto flex min-h-12 w-full max-w-[600px] items-center gap-2 rounded-xs bg-inverse-surface py-1 pr-2 pl-4 text-inverse-on-surface shadow-elevation-3"
			>
				<p class="min-w-0 flex-1 py-2">{{ current.message }}</p>

				<button v-if="current.action" type="button" class="btn-text text-inverse-primary" @click="act">{{ current.action.label }}</button>

				<!-- A message that goes by itself needs no button to send it away; one that stays until dealt with does. -->
				<button
					v-if="current.duration === null"
					type="button"
					class="btn-icon text-inverse-on-surface"
					:aria-label="t('common.dismiss')"
					@click="dismissSnackbar(current.id)"
				>
					<AppIcon :icon="CLOSE" />
				</button>
			</div>
		</Transition>
	</div>
</template>
