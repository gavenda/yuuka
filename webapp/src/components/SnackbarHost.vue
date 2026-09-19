<script setup lang="ts">
import { dismissSnackbar, snackbarQueue } from '@/lib/snackbar';
import { computed, onBeforeUnmount, watch } from 'vue';

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
	<div class="pointer-events-none fixed inset-x-4 bottom-40 z-40 flex justify-center sm:bottom-6">
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
				class="pointer-events-auto flex min-h-12 w-full max-w-md items-center gap-2 rounded-xs bg-inverse-surface py-2 pr-2 pl-4 text-sm text-inverse-on-surface shadow-elevation-3"
			>
				<p class="min-w-0 flex-1 py-1">{{ current.message }}</p>

				<button v-if="current.action" type="button" class="btn-text text-inverse-primary" @click="act">{{ current.action.label }}</button>

				<button type="button" class="btn-icon size-10 text-inverse-on-surface" aria-label="Dismiss" @click="dismissSnackbar(current.id)">
					<svg viewBox="0 0 20 20" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.75" aria-hidden="true">
						<path d="M5 5l10 10M15 5L5 15" stroke-linecap="round" />
					</svg>
				</button>
			</div>
		</Transition>
	</div>
</template>
