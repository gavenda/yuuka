<script setup lang="ts">
import { useModal } from '@/lib/modal';
import { ref } from 'vue';

/**
 * Material 3's basic dialog (`AlertDialog` on Android): an optional headline, supporting text and a row of
 * text buttons at the foot — a question that wants an answer before anything else happens, or a short
 * list to pick from. Put the buttons in the `actions` slot, the dismissing one first.
 */
const props = defineProps<{ open: boolean; title?: string }>();
const emit = defineEmits<{ close: [] }>();

const panel = ref<HTMLElement | null>(null);
useModal(
	() => props.open,
	() => emit('close'),
	panel,
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
			<div v-if="open" class="fixed inset-0 z-[60] flex items-center justify-center bg-scrim/32 p-6">
				<!-- Clicking the scrim dismisses; clicks inside the panel must not. -->
				<div class="absolute inset-0" @click="emit('close')" />

				<div
					ref="panel"
					role="alertdialog"
					aria-modal="true"
					:aria-label="title"
					tabindex="-1"
					class="dialog-enter relative flex max-h-full w-full max-w-[560px] min-w-[280px] flex-col rounded-xl bg-surface-container-high p-6 shadow-elevation-3 outline-none [--surface-under:var(--color-surface-container-high)] sm:w-auto sm:min-w-[320px]"
				>
					<h2 v-if="title" class="type-headline-small pb-4 text-on-surface">{{ title }}</h2>

					<div class="type-body-medium min-h-0 overflow-y-auto text-on-surface-variant"><slot /></div>

					<div class="flex flex-none flex-wrap justify-end gap-2 pt-6"><slot name="actions" /></div>
				</div>
			</div>
		</Transition>
	</Teleport>
</template>
