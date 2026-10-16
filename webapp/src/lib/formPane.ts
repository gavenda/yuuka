import { computed, ref, shallowRef } from 'vue';

/**
 * A form's supporting pane, as Android's `FullScreenDialogPane`: where the page beside the rail has room for
 * it, a `FormDialog` stands at the page's end instead of covering it, with the screen that opened it still
 * usable beside it. `App.vue` measures the page and holds the pane; this is what the two share.
 */

/** Material's supporting pane width, the gap from the page beside it, and the pane's own margin. */
export const FORM_PANE_WIDTH = 412;
export const FORM_PANE_SLOT = 8 + FORM_PANE_WIDTH + 16;

/** Room for the pane with the screen beside it still a list's width. */
export const FORM_PANE_MIN_PAGE = 360 + FORM_PANE_SLOT;

/** Set by the shell: the page is wide enough to keep its screen usable beside a form's pane. */
export const paneRoom = ref(false);

/** The forms standing in the pane, newest last, each with a way to ask whether it holds changes. */
const standing = shallowRef<{ id: symbol; dirty: () => boolean }[]>([]);

/** Whether a form is standing in the pane. */
export const paneOpen = computed(() => standing.value.length > 0);

/** Another form asked for while the one in the pane holds changes, waiting on the answer to discarding them. */
export const pendingSwitch = ref<(() => void) | null>(null);

export function enterPane(id: symbol, dirty: () => boolean): void {
	if (!standing.value.some((form) => form.id === id)) standing.value = [...standing.value, { id, dirty }];
}

export function leavePane(id: symbol): void {
	standing.value = standing.value.filter((form) => form.id !== id);
}

/**
 * Runs `action` — opening another form — straight away, unless the form in the pane holds changes: the screen
 * beside a pane can still be pressed, so opening a row there would otherwise throw them away. Then it asks
 * first, as closing the form would, and runs `action` on "Yes, discard them".
 */
export function whenDiscardable(action: () => void): void {
	const open = standing.value.at(-1);
	if (open?.dirty()) pendingSwitch.value = action;
	else action();
}
