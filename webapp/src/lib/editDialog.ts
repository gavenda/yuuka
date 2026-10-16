import { computed, ref, shallowRef } from 'vue';

/**
 * The state every add/edit dialog keeps: whether it is open, the row it was opened on (null for a new one),
 * the failure that belongs to no one field, and whether the form still holds what it opened with — which is
 * what lets closing it ask before discarding. `resetValidation` clears the touched fields, so a form opened
 * again is not covered in last time's red.
 */
export function useEditDialog<T, F extends object>(form: F, resetValidation: () => void) {
	const open = ref(false);
	const editing = shallowRef<T | null>(null);
	const error = ref<string | null>(null);
	const opened = ref('');
	const dirty = computed(() => JSON.stringify(form) !== opened.value);

	/** Opens the dialog on `target` (null for a new one), with the form holding `values`. */
	function show(target: T | null, values: F): void {
		editing.value = target;
		error.value = null;
		resetValidation();
		Object.assign(form, values);
		opened.value = JSON.stringify(form);
		open.value = true;
	}

	return { open, editing, error, dirty, show };
}
