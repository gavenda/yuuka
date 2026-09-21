import { afterEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick, ref, type App } from 'vue';
import PreferenceSelect from './PreferenceSelect.vue';
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
				h(PreferenceSelect, {
					id: 'field',
					label: 'Account',
					options,
					modelValue: value.value,
					'onUpdate:modelValue': (next: string) => (value.value = next),
					...extra,
				}),
		}),
	);
	app.mount(host);

	const trigger = () => host!.querySelector<HTMLButtonElement>('button#field')!;
	// A closing dialog lingers in the DOM for its leave transition, which jsdom never finishes, so it is the field that says whether it is open.
	const dialog = () =>
		trigger().getAttribute('aria-expanded') === 'true' ? document.body.querySelector<HTMLElement>('[role="dialog"]') : null;
	const radios = () => [...document.body.querySelectorAll<HTMLInputElement>('input[type="radio"]')];
	const press = async (key: string) => {
		(document.activeElement ?? document.body).dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
		await nextTick();
	};
	const openIt = async () => {
		trigger().click();
		await nextTick();
		await nextTick();
	};

	return { value, trigger, dialog, radios, press, openIt };
}

afterEach(() => {
	app?.unmount();
	host?.remove();
	document.body.innerHTML = '';
});

describe('PreferenceSelect', () => {
	it('reads as a row: the label, with the chosen option beneath it, and the placeholder when nothing is chosen', () => {
		const chosen = mount('b');
		expect(chosen.trigger().textContent).toContain('Account');
		expect(chosen.trigger().textContent).toContain('Savings');
		app?.unmount();
		host?.remove();

		const empty = mount('');
		expect(empty.trigger().textContent).toContain('Select an account');
		expect(empty.dialog()).toBeNull();
	});

	it('opens a dialog of radio buttons on click, marking the chosen one and leaving the placeholder out', async () => {
		const field = mount('b');
		await field.openIt();

		expect(field.dialog()?.querySelector('h2')?.textContent).toBe('Account');
		expect(field.radios().map((radio) => radio.parentElement?.textContent?.trim())).toEqual(['Checking', 'Savings', 'Cash']);
		expect(field.radios().map((radio) => radio.checked)).toEqual([false, true, false]);
		expect(document.activeElement).toBe(field.radios()[1]);
	});

	it('chooses by clicking a radio, and closes', async () => {
		const field = mount('');
		await field.openIt();

		field.radios()[2].click();
		await nextTick();

		expect(field.value.value).toBe('c');
		expect(field.dialog()).toBeNull();
	});

	it('closes when the chosen radio is chosen again', async () => {
		const field = mount('b');
		await field.openIt();

		field.radios()[1].click();
		await nextTick();

		expect(field.value.value).toBe('b');
		expect(field.dialog()).toBeNull();
	});

	it('moves through the radios with the arrow keys without choosing, and chooses with Enter', async () => {
		const field = mount('a');
		await field.openIt();

		await field.press('ArrowDown');
		expect(document.activeElement).toBe(field.radios()[1]);
		expect(field.value.value).toBe('a');
		expect(field.dialog()).not.toBeNull();

		await field.press('Enter');
		expect(field.value.value).toBe('b');
		expect(field.dialog()).toBeNull();
	});

	it('does not run past the ends of the list', async () => {
		const field = mount('c');
		await field.openIt();

		await field.press('ArrowDown');
		expect(document.activeElement).toBe(field.radios()[2]);

		await field.press('Home');
		expect(document.activeElement).toBe(field.radios()[0]);
	});

	it('leaves the value alone on Cancel', async () => {
		const field = mount('a');
		await field.openIt();

		[...document.body.querySelectorAll('button')].find((button) => button.textContent === 'Cancel')!.click();
		await nextTick();

		expect(field.value.value).toBe('a');
		expect(field.dialog()).toBeNull();
	});

	it('closes on Escape without letting it reach a dialog listening on the document', async () => {
		const reached = vi.fn();
		document.addEventListener('keydown', reached);

		const field = mount('a');
		await field.openIt();
		await field.press('Escape');

		expect(field.dialog()).toBeNull();
		expect(reached).not.toHaveBeenCalled();
		document.removeEventListener('keydown', reached);
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
		await field.openIt();

		expect(field.dialog()).toBeNull();
	});
});
