import { onBeforeUnmount, onMounted, watch, type Ref } from 'vue';

/** Dialogs and sheets that are open, so the page is let go of only when the last one closes. */
let openCount = 0;

/**
 * What every dialog and sheet shares: Escape closes it, the page underneath stops scrolling while it is up,
 * and focus moves into it on opening so a keyboard user is not left behind at whatever opened it.
 * `panel` is the element focus goes into; pass `focusFirst` false for a surface that places focus itself.
 */
export function useModal(open: () => boolean, close: () => void, panel: Ref<HTMLElement | null>, focusFirst = true): void {
	let counted = false;

	function count(isOpen: boolean): void {
		if (isOpen === counted) return;
		counted = isOpen;
		openCount += isOpen ? 1 : -1;
		document.body.style.overflow = openCount > 0 ? 'hidden' : '';
	}

	function onKeydown(event: KeyboardEvent): void {
		if (event.key !== 'Escape' || !open() || event.defaultPrevented) return;
		// Only the dialog on top answers: one opened over another closes first.
		const dialogs = document.querySelectorAll('[role="dialog"], [role="alertdialog"]');
		if (dialogs.length && dialogs[dialogs.length - 1] !== panel.value) return;
		event.preventDefault();
		close();
	}

	watch(
		open,
		async (isOpen) => {
			count(isOpen);
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
	onBeforeUnmount(() => {
		document.removeEventListener('keydown', onKeydown);
		count(false);
	});
}
