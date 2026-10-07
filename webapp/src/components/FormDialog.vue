<script setup lang="ts">
import AlertDialog from '@/components/AlertDialog.vue';
import AppIcon from '@/components/AppIcon.vue';
import { CLOSE, DELETE } from '@/lib/icons';
import { useModal } from '@/lib/modal';
import { ref } from 'vue';
import { t } from '@/i18n';

/**
 * A task that takes the screen over — a form, or a list to search — as Android's `FullScreenDialog`: on a
 * phone it is Material 3's full-screen dialog, with close where the drawer button would be, the title, and
 * Save as the bar's one action; Material keeps that shape for a phone, so a window with a rail gets an
 * ordinary dialog of the same content with its buttons at the foot.
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
	}>(),
	{ save: true, saveEnabled: true, submitting: false, dirty: false },
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

useModal(() => props.open, close, panel);
</script>

<template>
	<Teleport to="body">
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
						<button v-if="save" type="submit" class="btn-text mr-2" :disabled="!saveEnabled || submitting">{{ t('common.save') }}</button>
					</header>

					<h2 class="type-headline-small flex-none px-6 pt-6 pb-4 text-on-surface max-sm:hidden">{{ title }}</h2>

					<div class="flex min-h-0 flex-1 flex-col gap-3.5 overflow-y-auto px-5 py-3 sm:flex-initial sm:px-6 sm:py-0"><slot /></div>

					<footer class="flex flex-none justify-end gap-2 p-6 max-sm:hidden">
						<button type="button" class="btn-text" :disabled="submitting" @click="close">
							{{ save ? t('common.cancel') : t('common.close') }}
						</button>
						<button v-if="save" type="submit" class="btn-text" :disabled="!saveEnabled || submitting">{{ t('common.save') }}</button>
					</footer>
				</form>
			</div>
		</Transition>
	</Teleport>

	<AlertDialog :open="open && confirmingDiscard" :title="t('form.discardTitle')" :icon="DELETE" @close="confirmingDiscard = false">
		{{ t('form.discardBody') }}
		<template #actions>
			<button type="button" class="btn-text" @click="confirmingDiscard = false">{{ t('common.noKeepEditing') }}</button>
			<button type="button" class="btn-text text-error" @click="discard">{{ t('common.yesDiscard') }}</button>
		</template>
	</AlertDialog>
</template>
