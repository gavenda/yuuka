<script setup lang="ts">
import AlertDialog from '@/components/AlertDialog.vue';
import AppIcon from '@/components/AppIcon.vue';
import { CLOSE, DELETE } from '@/lib/icons';
import { enterPane, leavePane, paneRoom } from '@/lib/formPane';
import { useModal } from '@/lib/modal';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { t } from '@/i18n';

/**
 * A task that takes the screen over — a form, or a list to search — as Android's `FullScreenDialog`: on a
 * phone it is Material 3's full-screen dialog, with close where the drawer button would be, the title, and
 * Save as the bar's one action; Material keeps that shape for a phone, so a window with a rail gets an
 * ordinary dialog of the same content with its buttons at the foot. Where the page beside the rail has room for
 * it (`paneRoom`, `lib/formPane.ts`) it is neither: it is Material's supporting pane, standing at the page's
 * end with the screen that opened it still usable beside it, and with the full-screen dialog's bar, since it
 * is as tall as the page. `beside` false keeps a dialog a dialog even then — the display currency, which
 * Settings opens while it may already have Account types beside it.
 *
 * - `save` false means there is nothing to save (a list that writes each choice as it is made), and the
 *   dialog has only its close. `saveEnabled` is what a form binds its validation to.
 * - `dirty` says the form no longer holds what it opened with; closing it then asks first, since a stray
 *   Escape would otherwise throw the entry away. Nothing closes while `submitting`.
 *
 * The content is laid out as a form's column, scrolling under the title, and is a real `<form>`: Enter in a
 * field saves when saving is possible.
 */
const props = withDefaults(
	defineProps<{
		open: boolean;
		title: string;
		save?: boolean;
		saveEnabled?: boolean;
		submitting?: boolean;
		dirty?: boolean;
		beside?: boolean;
	}>(),
	{ save: true, saveEnabled: true, submitting: false, dirty: false, beside: true },
);

const emit = defineEmits<{ close: []; save: [] }>();

const panel = ref<HTMLElement | null>(null);
const confirmingDiscard = ref(false);

function close(): void {
	if (props.submitting) return;
	if (props.dirty) confirmingDiscard.value = true;
	else emit('close');
}

function discard(): void {
	confirmingDiscard.value = false;
	emit('close');
}

function submit(): void {
	if (props.save && props.saveEnabled && !props.submitting) emit('save');
}

/** Standing in the supporting pane rather than over the page. */
const inPane = computed(() => props.beside && paneRoom.value);

useModal(() => props.open && !inPane.value, close, panel);

// The pane is not a dialog: nothing behind it is shut off, so it only registers itself (which is what widens
// the pane and lets the screen ask before replacing it), takes the focus as it opens, gives it back to what
// opened it as it goes, and answers Escape when no dialog stands over it.
const paneId = Symbol('form-pane');
let opener: HTMLElement | null = null;

watch(
	() => props.open && inPane.value,
	async (standing) => {
		if (standing) {
			enterPane(paneId, () => props.dirty);
			const active = document.activeElement;
			if (!panel.value?.contains(active)) opener = active instanceof HTMLElement ? active : null;
			await nextTick();
			await new Promise((resolve) => requestAnimationFrame(resolve));
			if (panel.value?.contains(document.activeElement)) return;
			panel.value?.querySelector<HTMLElement>('[autofocus], input:not([type="hidden"]), textarea, [role="combobox"], button')?.focus();
		} else {
			leavePane(paneId);
			const hadFocus = panel.value?.contains(document.activeElement) ?? false;
			if (!props.open && hadFocus && opener?.isConnected) opener.focus();
			opener = null;
		}
	},
	{ immediate: true },
);

function onKeydown(event: KeyboardEvent): void {
	if (event.key !== 'Escape' || !props.open || !inPane.value || event.defaultPrevented) return;
	if (document.querySelector('[role="dialog"], [role="alertdialog"]')) return;
	event.preventDefault();
	close();
}

onMounted(() => document.addEventListener('keydown', onKeydown));
onBeforeUnmount(() => {
	document.removeEventListener('keydown', onKeydown);
	leavePane(paneId);
});
</script>

<template>
	<!-- In the shell's supporting pane (`#form-pane` in `App.vue`), which widens while a form stands in it. A
	     form replacing another fades in over it as that one fades out. -->
	<Teleport v-if="inPane" to="#form-pane" defer>
		<Transition
			enter-active-class="transition-opacity duration-200 ease-standard-decelerate"
			enter-from-class="opacity-0"
			leave-active-class="transition-opacity duration-150 ease-standard-accelerate"
			leave-to-class="opacity-0"
		>
			<form
				v-if="open"
				ref="panel"
				:aria-label="title"
				novalidate
				class="absolute inset-0 flex flex-col rounded-xl bg-surface-container [--surface-under:var(--color-surface-container)]"
				@submit.prevent="submit"
			>
				<header class="flex h-16 flex-none items-center gap-1 px-1 pt-2">
					<button type="button" class="btn-icon m-1" :aria-label="t('common.close')" :disabled="submitting" @click="close">
						<AppIcon :icon="CLOSE" />
					</button>
					<h2 class="type-title-large min-w-0 flex-1 truncate text-on-surface">{{ title }}</h2>
					<button v-if="save" type="submit" class="btn-primary mr-3" :disabled="!saveEnabled || submitting">
						{{ t('common.save') }}
					</button>
				</header>
				<div class="flex min-h-0 flex-1 flex-col gap-3.5 overflow-y-auto px-5 py-3"><slot /></div>
			</form>
		</Transition>
	</Teleport>

	<Teleport v-else to="body">
		<Transition
			enter-active-class="transition-opacity duration-200 ease-standard-decelerate"
			enter-from-class="opacity-0"
			leave-active-class="transition-opacity duration-150 ease-standard-accelerate"
			leave-to-class="opacity-0"
		>
			<div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center sm:bg-scrim/32 sm:p-6">
				<!-- Clicking the scrim dismisses; clicks inside the panel must not. -->
				<div class="absolute inset-0 max-sm:hidden" @click="close" />

				<form
					ref="panel"
					role="dialog"
					aria-modal="true"
					:aria-label="title"
					novalidate
					class="relative flex size-full flex-col bg-surface outline-none [--surface-under:var(--color-surface)] max-sm:sheet-enter sm:dialog-enter sm:h-auto sm:max-h-full sm:max-w-[560px] sm:rounded-xl sm:bg-surface-container-high sm:shadow-elevation-3 sm:[--surface-under:var(--color-surface-container-high)]"
					@submit.prevent="submit"
				>
					<!-- A phone's bar: close, the title, and Save as its one action. -->
					<header class="flex h-16 flex-none items-center gap-1 px-1 sm:hidden">
						<button type="button" class="btn-icon m-1" :aria-label="t('common.close')" :disabled="submitting" @click="close">
							<AppIcon :icon="CLOSE" />
						</button>
						<h2 class="type-title-large min-w-0 flex-1 truncate text-on-surface">{{ title }}</h2>
						<button v-if="save" type="submit" class="btn-primary mr-2" :disabled="!saveEnabled || submitting">
							{{ t('common.save') }}
						</button>
					</header>

					<h2 class="type-headline-small flex-none px-6 pt-6 pb-4 text-on-surface max-sm:hidden">{{ title }}</h2>

					<div class="flex min-h-0 flex-1 flex-col gap-3.5 overflow-y-auto px-5 py-3 sm:flex-initial sm:px-6 sm:py-0"><slot /></div>

					<footer class="flex flex-none justify-end gap-2 p-6 max-sm:hidden">
						<button type="button" class="btn-text" :disabled="submitting" @click="close">
							{{ save ? t('common.cancel') : t('common.close') }}
						</button>
						<button v-if="save" type="submit" class="btn-primary" :disabled="!saveEnabled || submitting">{{ t('common.save') }}</button>
					</footer>
				</form>
			</div>
		</Transition>
	</Teleport>

	<AlertDialog :open="open && confirmingDiscard" :title="t('form.discardTitle')" :icon="DELETE" @close="confirmingDiscard = false">
		{{ t('form.discardBody') }}
		<template #actions>
			<button type="button" class="btn-text" @click="confirmingDiscard = false">{{ t('common.noKeepEditing') }}</button>
			<button type="button" class="btn-danger" @click="discard">{{ t('common.yesDiscard') }}</button>
		</template>
	</AlertDialog>
</template>
