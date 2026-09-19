<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = defineProps<{ open: boolean; title: string }>();
const emit = defineEmits<{ close: [] }>();

const panel = ref<HTMLElement | null>(null);

function onKeydown(event: KeyboardEvent): void {
	if (event.key === 'Escape' && props.open) emit('close');
}

// Focus moves into the dialog on open so keyboard users are not left behind
// at the trigger, and the page underneath stops scrolling.
watch(
	() => props.open,
	async (open) => {
		document.body.style.overflow = open ? 'hidden' : '';
		if (!open) return;
		await new Promise((resolve) => requestAnimationFrame(resolve));
		panel.value?.querySelector<HTMLElement>('input, select, textarea, button')?.focus();
	},
);

onMounted(() => document.addEventListener('keydown', onKeydown));
onBeforeUnmount(() => {
	document.removeEventListener('keydown', onKeydown);
	document.body.style.overflow = '';
});
</script>

<template>
	<Teleport to="body">
		<Transition
			enter-active-class="transition duration-200 ease-standard-decelerate"
			enter-from-class="opacity-0"
			leave-active-class="transition duration-150 ease-standard-accelerate"
			leave-to-class="opacity-0"
		>
			<div v-if="open" class="fixed inset-0 z-50 flex items-end justify-center bg-scrim/32 p-0 sm:items-center sm:p-4">
				<!-- Clicking the scrim dismisses; clicks inside the panel must not. -->
				<div class="absolute inset-0" @click="emit('close')" />

				<!-- A bottom sheet on a phone (extra-large top corners, with a handle) and a
				     centred basic dialog once there is room. -->
				<div
					ref="panel"
					role="dialog"
					aria-modal="true"
					:aria-label="title"
					class="dialog-enter relative max-h-[92vh] w-full max-w-lg overflow-y-auto rounded-t-xl bg-surface-container-high [--surface-under:var(--color-surface-container-high)] shadow-elevation-3 sm:rounded-xl"
				>
					<div class="mx-auto mt-3 h-1 w-8 rounded-full bg-on-surface-variant/40 sm:hidden" aria-hidden="true" />

					<header class="flex items-center justify-between gap-2 px-6 pt-4 pb-2 sm:pt-6">
						<h2 class="text-2xl text-on-surface">{{ title }}</h2>
						<button type="button" class="btn-icon -mr-2" aria-label="Close" @click="emit('close')">
							<svg viewBox="0 0 20 20" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.75" aria-hidden="true">
								<path d="M5 5l10 10M15 5L5 15" stroke-linecap="round" />
							</svg>
						</button>
					</header>

					<div class="px-6 pt-4 pb-6"><slot /></div>
				</div>
			</div>
		</Transition>
	</Teleport>
</template>
