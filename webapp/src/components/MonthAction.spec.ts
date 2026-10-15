import { afterEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick, ref, type App } from 'vue';
import MonthAction from './MonthAction.vue';
import { currentMonthAction } from '@/lib/monthAction';

const apps: App[] = [];

/** Mounts a screen's month action over a month it can change, and hands back that month to read. */
function mount(initial: string) {
	const month = ref(initial);
	const host = document.createElement('div');
	document.body.append(host);

	const app = createApp(
		defineComponent({
			render: () => h(MonthAction, { modelValue: month.value, 'onUpdate:modelValue': (next: string) => (month.value = next) }),
		}),
	);
	app.mount(host);
	apps.push(app);

	return { month, unmount: () => app.unmount() };
}

const dialog = () => document.body.querySelector<HTMLElement>('[role="alertdialog"], [role="dialog"]');
const cell = (label: string) =>
	[...(dialog()?.querySelectorAll<HTMLButtonElement>('button[aria-pressed]') ?? [])].find((b) => b.textContent?.trim() === label);

afterEach(() => {
	while (apps.length) apps.pop()?.unmount();
	document.body.innerHTML = '';
});

describe('MonthAction', () => {
	it('draws nothing itself and puts its month forward for the shell, following the screen as it changes', async () => {
		const screen = mount('2026-10');

		expect(dialog()).toBeNull();
		expect(document.body.querySelector('button')).toBeNull();
		expect(currentMonthAction.value?.month).toBe('2026-10');

		screen.month.value = '2026-09';
		await nextTick();
		expect(currentMonthAction.value?.month).toBe('2026-09');
	});

	it('opens its picker on the year being shown when the shell asks, and picking a month closes it', async () => {
		const screen = mount('2025-03');

		currentMonthAction.value?.open();
		await nextTick();
		expect(dialog()?.textContent).toContain('2025');

		const march = new Intl.DateTimeFormat(undefined, { month: 'short' }).format(new Date(2025, 2, 1));
		expect(cell(march)?.getAttribute('aria-pressed')).toBe('true');

		const july = new Intl.DateTimeFormat(undefined, { month: 'short' }).format(new Date(2025, 6, 1));
		cell(july)?.click();
		await nextTick();

		expect(screen.month.value).toBe('2025-07');
		expect(currentMonthAction.value?.month).toBe('2025-07');
		// The dialog fades out rather than vanishing.
		await vi.waitFor(() => expect(dialog()).toBeNull());
	});

	it('takes its month back when the screen goes, but never the one a newer screen has put forward', () => {
		const leaving = mount('2026-01');
		const arriving = mount('2026-02');

		leaving.unmount();
		expect(currentMonthAction.value?.month).toBe('2026-02');

		arriving.unmount();
		expect(currentMonthAction.value).toBeNull();
	});
});
