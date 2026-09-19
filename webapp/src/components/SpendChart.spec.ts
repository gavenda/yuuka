import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createApp, nextTick, type App } from 'vue';
import SpendChart from './SpendChart.vue';
import { formatLongDate } from '@/lib/dates';
import { currencySymbol } from '@/lib/money';
import { displayMoney, useAmountVisibility } from '@/lib/privacy';
import { installLocalStorage, MemoryStorage } from '@/testing/memoryStorage';

let app: App | undefined;
let host: HTMLElement | undefined;

// Spent on the 1st, 3rd and 5th: an average of 3000 across the three days that had any spending.
const DAYS = [
	{ date: '2026-09-01', amount: 1000 },
	{ date: '2026-09-03', amount: 3000 },
	{ date: '2026-09-05', amount: 5000 },
];

function mount(props: Record<string, unknown> = {}) {
	host = document.createElement('div');
	document.body.append(host);
	app = createApp(SpendChart, { month: '2026-09', days: DAYS, ...props });
	app.mount(host);

	return {
		bars: () => [...host!.querySelectorAll('[data-bar]')],
		badges: () => [...host!.querySelectorAll('[data-badge]')],
		hits: () => [...host!.querySelectorAll<SVGRectElement>('[data-hit]')],
		tooltip: () => host!.querySelector<HTMLElement>('[data-tooltip]'),
		text: () => host!.textContent ?? '',
	};
}

beforeEach(() => {
	installLocalStorage(new MemoryStorage());
	useAmountVisibility().hidden.value = false;
});

afterEach(() => {
	app?.unmount();
	host?.remove();
	document.body.innerHTML = '';
});

describe('SpendChart', () => {
	it('draws one bar for every day of the month', () => {
		expect(mount().bars()).toHaveLength(30);
	});

	it('sets apart the days at or above the average, and only those', () => {
		const chart = mount();

		expect(chart.badges()).toHaveLength(2);
		expect(host!.querySelector('[data-average]')).not.toBeNull();
	});

	it('puts the display currency in the badge', () => {
		const chart = mount({ currency: 'PHP' });
		const badgeText = [...host!.querySelectorAll('text')].filter((node) => node.classList.contains('fill-on-tertiary-container'));

		expect(chart.badges()).toHaveLength(2);
		expect(badgeText).toHaveLength(2);
		for (const node of badgeText) expect(node.textContent?.trim()).toBe(currencySymbol('PHP'));
	});

	it('falls back to the code for a currency the platform does not know', () => {
		mount({ currency: 'ZZZ' });
		const badgeText = [...host!.querySelectorAll('text')].filter((node) => node.classList.contains('fill-on-tertiary-container'));

		expect(badgeText[0].textContent?.trim()).toBe(currencySymbol('ZZZ'));
	});

	it('names a day when it is tapped, and lets go when it is tapped again', async () => {
		const chart = mount();
		expect(chart.tooltip()).toBeNull();

		chart.hits()[2].dispatchEvent(new MouseEvent('click', { bubbles: true }));
		await nextTick();
		expect(chart.tooltip()?.textContent).toContain(formatLongDate('2026-09-03'));
		expect(chart.tooltip()?.textContent).toContain(displayMoney(3000, 'PHP'));

		chart.hits()[2].dispatchEvent(new MouseEvent('click', { bubbles: true }));
		await nextTick();
		expect(chart.tooltip()).toBeNull();
	});

	it('moves the tooltip from one day to another', async () => {
		const chart = mount();

		chart.hits()[0].dispatchEvent(new MouseEvent('click', { bubbles: true }));
		chart.hits()[4].dispatchEvent(new MouseEvent('click', { bubbles: true }));
		await nextTick();

		expect(chart.tooltip()?.textContent).toContain(formatLongDate('2026-09-05'));
	});

	it('masks the axis figures and the tooltip amount along with every other amount', async () => {
		useAmountVisibility().toggle();
		const chart = mount();
		chart.hits()[2].dispatchEvent(new MouseEvent('click', { bubbles: true }));
		await nextTick();

		const axis = host!.querySelector('[data-average-label]');
		expect(axis?.textContent?.trim()).toBe('••');
		expect(chart.tooltip()?.textContent).not.toContain('30.00');
	});

	it('draws nothing but a note for a month with no spending', () => {
		const chart = mount({ days: [] });

		expect(chart.bars()).toHaveLength(0);
		expect(chart.text()).toContain('No spending recorded this month.');
	});

	it('swaps to the table of figures on request', async () => {
		const chart = mount();
		host!.querySelector<HTMLButtonElement>('button')!.click();
		await nextTick();

		expect(chart.bars()).toHaveLength(0);
		expect(host!.querySelectorAll('tbody tr')).toHaveLength(3);
	});
});
