<script setup lang="ts">
import AlertDialog from '@/components/AlertDialog.vue';
import AppIcon from '@/components/AppIcon.vue';
import { CLOSE, DELETE } from '@/lib/icons';
import { enterPane, leavePane, paneRoom } from '@/lib/formPane';
import { compactWindow } from '@/lib/compact';
import { isTopDialog, useModalCount } from '@/lib/modal';
import { Button, Dialog, IconButton } from 'tailwind-material3';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue';
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
const formId = useId();
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

/** A dialog over the page: tailwind-material3's `Dialog`, full-screen on a phone. */
const asDialog = computed(() => props.open && !inPane.value);
useModalCount(() => asDialog.value);

// The dialog would focus its first control, which on a phone is the close button; a form starts at its first field.
watch(asDialog, async (shown) => {
	if (!shown) return;
	await nextTick();
	await new Promise((resolve) => requestAnimationFrame(resolve));
	panel.value?.querySelector<HTMLElement>('[autofocus], input:not([type="hidden"]), textarea, [role="combobox"]')?.focus();
});

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
	if (!isTopDialog(null)) return;
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
					<IconButton variant="standard" class="m-1" :label="t('common.close')" :disabled="submitting" @click="close">
						<AppIcon :icon="CLOSE" />
					</IconButton>
					<h2 class="type-title-large min-w-0 flex-1 truncate text-on-surface">{{ title }}</h2>
					<Button v-if="save" type="submit" class="mr-3" :disabled="!saveEnabled || submitting">
						{{ t('common.save') }}
					</Button>
				</header>
				<div class="flex min-h-0 flex-1 flex-col gap-3.5 overflow-y-auto px-5 py-3"><slot /></div>
			</form>
		</Transition>
	</Teleport>

	<!-- Over the page: Material's full-screen dialog on a phone (close, the title, Save in the bar) and its basic
	     dialog beside a rail (Cancel and Save at the foot). Closing goes through `close`, which asks first when the
	     form holds changes, so the dialog never shuts itself. -->
	<Dialog v-else :open="open" :fullscreen="compactWindow" persistent :close-label="t('common.close')" @cancel="close">
		<template #headline>{{ title }}</template>
		<form
			:id="formId"
			ref="panel"
			novalidate
			class="flex flex-col gap-3.5 pt-1 text-on-surface sm:w-[512px] sm:max-w-full [--surface-under:var(--color-surface)] sm:[--surface-under:var(--color-surface-container-high)]"
			@submit.prevent="submit"
		>
			<slot />
		</form>
		<template v-if="save || !compactWindow" #actions>
			<template v-if="compactWindow">
				<Button type="submit" :form="formId" :disabled="!saveEnabled || submitting">{{ t('common.save') }}</Button>
			</template>
			<template v-else>
				<Button variant="text" :disabled="submitting" @click="close">
					{{ save ? t('common.cancel') : t('common.close') }}
				</Button>
				<Button v-if="save" type="submit" :form="formId" :disabled="!saveEnabled || submitting">{{ t('common.save') }}</Button>
			</template>
		</template>
	</Dialog>

	<AlertDialog :open="open && confirmingDiscard" :title="t('form.discardTitle')" :icon="DELETE" @close="confirmingDiscard = false">
		{{ t('form.discardBody') }}
		<template #actions>
			<Button variant="text" @click="confirmingDiscard = false">{{ t('common.noKeepEditing') }}</Button>
			<Button class="tone-error" @click="discard">{{ t('common.yesDiscard') }}</Button>
		</template>
	</AlertDialog>
</template>
