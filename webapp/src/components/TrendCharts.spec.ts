import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createApp, nextTick, type App, type Component } from 'vue';
import IncomeSpendChart from './IncomeSpendChart.vue';
import NetWorthChart from './NetWorthChart.vue';
import { displayMoney, useAmountVisibility } from '@/lib/privacy';
import { installLocalStorage, MemoryStorage } from '@/testing/memoryStorage';
import type { TrendMonth } from '@/types';

let app: App | undefined;
let host: HTMLElement | undefined;

const MONTHS: TrendMonth[] = [
	{ month: '2026-08', income: 5_000_000, expenses: 3_000_000, net: 2_000_000, netWorth: 10_000_000 },
	{ month: '2026-09', income: 5_000_000, expenses: 6_000_000, net: -1_000_000, netWorth: 9_000_000 },
	{ month: '2026-10', income: 5_500_000, expenses: 2_500_000, net: 3_000_000, netWorth: 12_000_000 },
];

function mount(component: Component) {
	host = document.createElement('div');
	document.body.append(host);
	app = createApp(component, { months: MONTHS, currency: 'PHP' });
	app.mount(host);
	return host;
}

beforeEach(() => {
	installLocalStorage(new MemoryStorage());
	useAmountVisibility().hidden.value = false;
});

afterEach(() => {
	app?.unmount();
	host?.remove();
	app = undefined;
	host = undefined;
});

describe('IncomeSpendChart', () => {
	it('says what the months kept, and gives every month a row of the table', () => {
		const root = mount(IncomeSpendChart);

		expect(root.textContent).toContain(`Kept ${displayMoney(4_000_000, 'PHP')} across 3 months`);
		expect(root.querySelectorAll('tbody tr')).toHaveLength(3);
	});

	it('names a month in a tooltip when it is tapped', async () => {
		const root = mount(IncomeSpendChart);
		root.querySelectorAll<SVGRectElement>('rect.cursor-pointer')[1].dispatchEvent(new Event('click'));
		await nextTick();

		const tooltip = root.querySelector('.shadow-elevation-2');
		expect(tooltip?.textContent).toContain(displayMoney(6_000_000, 'PHP'));
	});
});

describe('NetWorthChart', () => {
	it('says how far net worth moved since the first month', () => {
		expect(mount(NetWorthChart).textContent).toContain(`Up ${displayMoney(2_000_000, 'PHP')} since`);
	});

	it('masks its axis while amounts are hidden', async () => {
		const root = mount(NetWorthChart);
		useAmountVisibility().hidden.value = true;
		await nextTick();

		const axis = [...root.querySelectorAll('svg[aria-hidden="true"] text')].map((node) => node.textContent?.trim());
		expect(axis.length).toBeGreaterThan(0);
		expect(axis.every((label) => label === '••')).toBe(true);
	});
});
