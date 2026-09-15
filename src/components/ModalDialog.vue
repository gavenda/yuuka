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
			enter-active-class="transition duration-150 ease-out"
			enter-from-class="opacity-0"
			leave-active-class="transition duration-100 ease-in"
			leave-to-class="opacity-0"
		>
			<div v-if="open" class="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/50 p-0 backdrop-blur-sm sm:items-center sm:p-4">
				<!-- Clicking the backdrop dismisses; clicks inside the panel must not. -->
				<div class="absolute inset-0" @click="emit('close')" />

				<div
					ref="panel"
					role="dialog"
					aria-modal="true"
					:aria-label="title"
					class="relative max-h-[92vh] w-full max-w-lg overflow-y-auto rounded-t-2xl bg-white shadow-xl sm:rounded-2xl dark:bg-slate-900"
				>
					<header class="flex items-center justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-800">
						<h2 class="text-base font-semibold text-slate-900 dark:text-white">{{ title }}</h2>
						<button type="button" class="btn-ghost -mr-2 px-2 py-1" aria-label="Close" @click="emit('close')">
							<svg viewBox="0 0 20 20" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.75">
								<path d="M5 5l10 10M15 5L5 15" stroke-linecap="round" />
							</svg>
						</button>
					</header>

					<div class="px-5 py-5"><slot /></div>
				</div>
			</div>
		</Transition>
	</Teleport>
</template>
