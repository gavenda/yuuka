<script setup lang="ts">
import { useModal } from '@/lib/modal';
import { ref } from 'vue';

/**
 * Material 3's modal bottom sheet (`ModalBottomSheet` on Android): for what is picked from a short list or
 * applied live — the filters, adjusting a balance, the Save the Change and default-account pickers. It
 * rises from the bottom edge under a drag handle, and on a wide window keeps Material's 640dp and sits in
 * the middle of that edge. A tap on the scrim, or Escape, closes it.
 */
const props = defineProps<{ open: boolean; label: string }>();
const emit = defineEmits<{ close: [] }>();

const panel = ref<HTMLElement | null>(null);
useModal(
	() => props.open,
	() => emit('close'),
	panel,
	false,
);
</script>

<template>
	<Teleport to="body">
		<Transition
			enter-active-class="transition-opacity duration-200 ease-standard-decelerate"
			enter-from-class="opacity-0"
			leave-active-class="transition-opacity duration-150 ease-standard-accelerate"
			leave-to-class="opacity-0"
		>
			<div v-if="open" class="fixed inset-0 z-50 flex items-end justify-center bg-scrim/32">
				<div class="absolute inset-0" @click="emit('close')" />

				<div
					ref="panel"
					role="dialog"
					aria-modal="true"
					:aria-label="label"
					tabindex="-1"
					class="sheet-enter relative flex max-h-[calc(100dvh-4.5rem)] w-full max-w-[640px] flex-col rounded-t-xl bg-surface-container-low text-on-surface shadow-elevation-1 outline-none [--surface-under:var(--color-surface-container-low)]"
				>
					<div class="mx-auto my-[22px] h-1 w-8 flex-none rounded-full bg-on-surface-variant" aria-hidden="true" />
					<div class="min-h-0 overflow-y-auto"><slot /></div>
				</div>
			</div>
		</Transition>
	</Teleport>
</template>
