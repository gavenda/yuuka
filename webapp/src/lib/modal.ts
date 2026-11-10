import { computed, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue';

/** Dialogs that are open, so the page is let go of only when the last one closes. */
const openCount = ref(0);

/** Whether any dialog is up — something a reload would close under the person. */
export const modalOpen = computed(() => openCount.value > 0);

/**
 * Counts a dialog that is up while `open` says so, and holds the page still underneath it. A tailwind-material3
 * `Dialog` brings its own focus trap and Escape, so this is all it needs; `useModal` is the rest for a surface that
 * is not one.
 */
export function useModalCount(open: () => boolean): void {
	let counted = false;

	function count(isOpen: boolean): void {
		if (isOpen === counted) return;
		counted = isOpen;
		openCount.value += isOpen ? 1 : -1;
		document.body.style.overflow = openCount.value > 0 ? 'hidden' : '';
	}

	watch(open, count, { immediate: true });
	onBeforeUnmount(() => count(false));
}

/** Whether `panel` is the dialog on top: an open native `<dialog>` (a library one) or an element with a dialog role. */
export function isTopDialog(panel: HTMLElement | null): boolean {
	const dialogs = document.querySelectorAll('dialog[open], [role="dialog"]:not(dialog), [role="alertdialog"]:not(dialog)');
	return !dialogs.length || dialogs[dialogs.length - 1] === panel;
}

/**
 * What a dialog drawn by the app itself shares: Escape closes it, the page underneath stops scrolling while it is
 * up, and focus moves into it on opening so a keyboard user is not left behind at whatever opened it.
 * `panel` is the element focus goes into; pass `focusFirst` false for a surface that places focus itself.
 */
export function useModal(open: () => boolean, close: () => void, panel: Ref<HTMLElement | null>, focusFirst = true): void {
	useModalCount(open);

	function onKeydown(event: KeyboardEvent): void {
		if (event.key !== 'Escape' || !open() || event.defaultPrevented) return;
		// Only the dialog on top answers: one opened over another closes first.
		if (!isTopDialog(panel.value)) return;
		event.preventDefault();
		close();
	}

	watch(
		open,
		async (isOpen) => {
			if (!isOpen || !focusFirst) return;
			await new Promise((resolve) => requestAnimationFrame(resolve));
			const target = panel.value?.querySelector<HTMLElement>(
				'[autofocus], input:not([type="hidden"]), textarea, [role="combobox"], button',
			);
			(target ?? panel.value)?.focus();
		},
		{ immediate: true },
	);

	onMounted(() => document.addEventListener('keydown', onKeydown));
	onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown));
}
