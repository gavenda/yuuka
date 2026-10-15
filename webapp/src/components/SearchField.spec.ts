import { afterEach, describe, expect, it } from 'vitest';
import { createApp, defineComponent, h, nextTick, ref, type App } from 'vue';
import SearchField from './SearchField.vue';

let app: App | undefined;

/** Mounts the field over a query it can change, and hands back that query to read. */
function mount(initial = '') {
	const query = ref(initial);
	const host = document.createElement('div');
	document.body.append(host);

	app = createApp(
		defineComponent({
			render: () =>
				h(SearchField, {
					label: 'Search',
					placeholder: 'Search payee, notes or tag',
					modelValue: query.value,
					'onUpdate:modelValue': (next: string) => (query.value = next),
				}),
		}),
	);
	app.mount(host);

	const input = () => host.querySelector<HTMLInputElement>('input[type="search"]')!;
	const clear = () => host.querySelector<HTMLButtonElement>('button');

	return { query, input, clear };
}

afterEach(() => {
	app?.unmount();
	document.body.innerHTML = '';
});

describe('SearchField', () => {
	it('is named for assistive tech, says what it searches while empty, and hands on what is typed', async () => {
		const field = mount();

		expect(field.input().getAttribute('aria-label')).toBe('Search');
		expect(field.input().placeholder).toBe('Search payee, notes or tag');

		field.input().value = 'coffee';
		field.input().dispatchEvent(new Event('input'));
		await nextTick();

		expect(field.query.value).toBe('coffee');
	});

	it('offers to empty itself only once it holds something', async () => {
		const field = mount();
		expect(field.clear()).toBeNull();

		field.query.value = 'coffee';
		await nextTick();
		expect(field.clear()).not.toBeNull();

		field.clear()!.click();
		await nextTick();

		expect(field.query.value).toBe('');
		expect(field.clear()).toBeNull();
	});

	it('keeps the focus in the field while it is emptied, so the typing carries on', async () => {
		const field = mount('coffee');
		field.input().focus();

		// A press takes the focus on its way down; refusing that is what leaves it in the field.
		const press = new MouseEvent('mousedown', { bubbles: true, cancelable: true });
		field.clear()!.dispatchEvent(press);

		expect(press.defaultPrevented).toBe(true);
		expect(document.activeElement).toBe(field.input());
	});
});
