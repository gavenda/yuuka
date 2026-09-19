import { afterEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick, ref, type App } from 'vue';
import SelectField from './SelectField.vue';
import type { SelectOption } from '@/lib/selectOptions';

const OPTIONS: SelectOption[] = [
	{ value: '', label: 'Select an account', disabled: true },
	{ value: 'a', label: 'Checking' },
	{ value: 'b', label: 'Savings' },
	{ value: 'c', label: 'Cash' },
];

let app: App | undefined;
let host: HTMLElement | undefined;

/** Mounts the field with a value it can change, and hands back that value to read. */
function mount(initial = '', options = OPTIONS, extra: Record<string, unknown> = {}) {
	const value = ref(initial);
	host = document.createElement('div');
	document.body.append(host);

	app = createApp(
		defineComponent({
			render: () =>
				h(SelectField, {
					id: 'field',
					options,
					modelValue: value.value,
					'onUpdate:modelValue': (next: string) => (value.value = next),
					...extra,
				}),
		}),
	);
	app.mount(host);

	const trigger = () => host!.querySelector<HTMLButtonElement>('button[role="combobox"]')!;
	const listbox = () => document.body.querySelector<HTMLElement>('[role="listbox"]');
	const closed = () => trigger().getAttribute('aria-expanded') === 'false';
	const options$ = () => [...document.body.querySelectorAll<HTMLElement>('[role="option"]')];
	const press = async (key: string) => {
		trigger().dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
		await nextTick();
	};

	return { value, trigger, listbox, options: options$, press, closed };
}

afterEach(() => {
	app?.unmount();
	host?.remove();
	document.body.innerHTML = '';
});

describe('SelectField', () => {
	it('shows the chosen option, and the placeholder when nothing is chosen', () => {
		const chosen = mount('b');
		expect(chosen.trigger().textContent).toContain('Savings');
		app?.unmount();
		host?.remove();

		const empty = mount('');
		expect(empty.trigger().textContent).toContain('Select an account');
		expect(empty.trigger().getAttribute('aria-expanded')).toBe('false');
	});

	it('opens a listbox of its options on click, marking the chosen one', async () => {
		const field = mount('b');
		field.trigger().click();
		await nextTick();

		expect(field.trigger().getAttribute('aria-expanded')).toBe('true');
		expect(field.listbox()).not.toBeNull();
		expect(field.options().map((option) => option.textContent?.trim())).toEqual(['Select an account', 'Checking', 'Savings', 'Cash']);
		expect(field.options().map((option) => option.getAttribute('aria-selected'))).toEqual(['false', 'false', 'true', 'false']);
	});

	it('chooses with the arrow keys and Enter, skipping a disabled option', async () => {
		const field = mount('');
		await field.press('ArrowDown'); // opens on the first choosable option
		expect(field.trigger().getAttribute('aria-activedescendant')).toBe('field-option-1');

		await field.press('ArrowDown');
		await field.press('Enter');

		expect(field.value.value).toBe('b');
		expect(field.closed()).toBe(true);
	});

	it('does not run past the ends of the list', async () => {
		const field = mount('c');
		await field.press('ArrowDown');
		await field.press('ArrowDown');
		expect(field.trigger().getAttribute('aria-activedescendant')).toBe('field-option-3');

		await field.press('Home');
		expect(field.trigger().getAttribute('aria-activedescendant')).toBe('field-option-1');
	});

	it('will not choose a disabled option by clicking it', async () => {
		const field = mount('a');
		field.trigger().click();
		await nextTick();

		field.options()[0].click();
		await nextTick();

		expect(field.value.value).toBe('a');
	});

	it('chooses by clicking, and closes', async () => {
		const field = mount('');
		field.trigger().click();
		await nextTick();

		field.options()[3].click();
		await nextTick();

		expect(field.value.value).toBe('c');
		expect(field.closed()).toBe(true);
	});

	it('closes on Escape without letting it reach a dialog listening on the document', async () => {
		const reached = vi.fn();
		document.addEventListener('keydown', reached);

		const field = mount('a');
		field.trigger().click();
		await nextTick();
		await field.press('Escape');

		expect(field.closed()).toBe(true);
		expect(reached).not.toHaveBeenCalled();
		document.removeEventListener('keydown', reached);
	});

	it('jumps to an option by typing, choosing it outright while closed', async () => {
		const field = mount('a');
		await field.press('s');
		expect(field.value.value).toBe('b');
	});

	it('stands in for a required native field, so a form cannot submit it empty', () => {
		const field = mount('', OPTIONS, { required: true });
		const stand = host!.querySelector<HTMLInputElement>('input[required]');

		expect(stand).not.toBeNull();
		expect(stand!.value).toBe('');
		expect(field.trigger().getAttribute('aria-required')).toBe('true');
	});

	it('opens for no one while disabled', async () => {
		const field = mount('a', OPTIONS, { disabled: true });
		field.trigger().click();
		await field.press('ArrowDown');

		expect(field.listbox()).toBeNull();
	});
});
