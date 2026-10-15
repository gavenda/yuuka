import { ApiError } from '@/lib/api';
import { clearCache } from '@/lib/cache';
import * as row from '@/testing/fixtures';
import { installIndexedDB } from '@/testing/indexedDb';
import { installLocalStorage, MemoryStorage } from '@/testing/memoryStorage';
import { createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp, type App } from 'vue';

/**
 * The list is an endless one: these specs pin that the next page is asked for by the foot of the list
 * coming within reach and by nothing that is pressed, that it carries on while the foot stays in reach,
 * and that a page that could not be had is asked for once rather than for as long as it does.
 */
const api = vi.hoisted(() => ({
	listAccounts: vi.fn(),
	listAccountTypes: vi.fn(),
	listCategories: vi.fn(),
	listTags: vi.fn(),
	settings: vi.fn(),
	getRoundUpRule: vi.fn(),
	listTransactions: vi.fn(),
}));

vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), api }));

/** Stands in for the browser's observer, which happy-dom never calls back: a spec says what it sees. */
class FakeObserver {
	static all: FakeObserver[] = [];
	connected = false;
	private readonly callback: IntersectionObserverCallback;

	constructor(callback: IntersectionObserverCallback) {
		this.callback = callback;
		FakeObserver.all.push(this);
	}

	observe(): void {
		this.connected = true;
	}

	disconnect(): void {
		this.connected = false;
	}

	see(isIntersecting: boolean): void {
		this.callback([{ isIntersecting } as IntersectionObserverEntry], this as unknown as IntersectionObserver);
	}
}

installIndexedDB();

const { default: TransactionsView } = await import('./TransactionsView.vue');

const offline = () => new ApiError(0, 'Network error. Check your connection.');

/** A page of [count] rows starting at [from], out of [total]. */
const page = (from: number, count: number, total: number) => ({
	transactions: Array.from({ length: count }, (_, index) => row.transaction(`txn_${from + index}`)),
	total,
});

/** Everything pending has settled: the API's answers, the copy's reads and what Vue drew for them. */
const settled = async () => {
	for (let turn = 0; turn < 5; turn++) await new Promise<void>((resolve) => setTimeout(resolve));
};

let app: App | undefined;
let host: HTMLElement;

/** Mounts the screen as the shell does, with the strips it teleports into, once its first page is in. */
async function mount() {
	document.body.innerHTML = '<div id="bar-search"></div><div id="top-bar"></div>';
	host = document.createElement('div');
	document.body.append(host);

	app = createApp(TransactionsView);
	app.use(createPinia());
	app.mount(host);
	await settled();

	/** The foot of the list sliding into reach, or out of it. */
	const reach = (inReach: boolean) => FakeObserver.all.find((observer) => observer.connected)?.see(inReach);
	const rows = () => host.querySelectorAll('.group-row').length;
	const button = (label: string) => [...host.querySelectorAll('button')].find((candidate) => candidate.textContent?.trim() === label);
	const offsets = () => api.listTransactions.mock.calls.map(([query]) => query.offset);

	return { reach, rows, button, offsets };
}

beforeEach(async () => {
	installLocalStorage(new MemoryStorage());
	await clearCache();
	FakeObserver.all = [];
	vi.stubGlobal('IntersectionObserver', FakeObserver);

	for (const fn of Object.values(api)) fn.mockReset();
	api.listAccounts.mockResolvedValue({ accounts: [] });
	api.listAccountTypes.mockResolvedValue({ accountTypes: [] });
	api.listCategories.mockResolvedValue({ categories: [] });
	api.listTags.mockResolvedValue({ tags: [] });
	api.settings.mockResolvedValue({ settings: row.settings() });
	api.getRoundUpRule.mockResolvedValue({ roundUpRule: row.roundUpRule() });
});

afterEach(() => {
	app?.unmount();
	app = undefined;
	document.body.innerHTML = '';
	vi.unstubAllGlobals();
});

describe('TransactionsView', () => {
	it('asks for the next page when the foot of the list comes within reach, with nothing to press', async () => {
		api.listTransactions.mockResolvedValueOnce(page(0, 50, 60)).mockResolvedValueOnce(page(50, 10, 60));

		const view = await mount();

		expect(view.rows()).toBe(50);
		expect(view.offsets()).toEqual([0]);
		expect(host.textContent).toContain('Calculating…');
		expect(host.textContent).not.toContain('Show more');

		view.reach(true);
		await settled();

		expect(view.offsets()).toEqual([0, 50]);
		expect(view.rows()).toBe(60);
		// Nothing is left, so the foot gives way to the end of the list.
		expect(host.textContent).not.toContain('Calculating…');
		expect(host.textContent).toContain("That's every last one, all accounted for.");
	});

	it('leaves the next page alone while the foot is out of reach', async () => {
		api.listTransactions.mockResolvedValueOnce(page(0, 50, 60));

		const view = await mount();
		view.reach(false);
		await settled();

		expect(view.offsets()).toEqual([0]);
	});

	it('carries on, page after page, for as long as the foot stays in reach', async () => {
		api.listTransactions
			.mockResolvedValueOnce(page(0, 50, 150))
			.mockResolvedValueOnce(page(50, 50, 150))
			.mockResolvedValueOnce(page(100, 50, 150));

		const view = await mount();
		// Seen once: a page that lands without pushing the foot out of reach is not reported again.
		view.reach(true);
		await settled();

		expect(view.offsets()).toEqual([0, 50, 100]);
		expect(view.rows()).toBe(150);
	});

	it('asks once for a page that cannot be had, keeps the rows, and asks again only when told to', async () => {
		api.listTransactions
			.mockResolvedValueOnce(page(0, 50, 60))
			.mockRejectedValueOnce(offline())
			.mockResolvedValueOnce(page(50, 10, 60));

		const view = await mount();
		view.reach(true);
		await settled();

		expect(view.offsets()).toEqual([0, 50]);
		expect(view.rows()).toBe(50);
		expect(host.querySelector('[role="alert"]')?.textContent).toBe('Network error. Check your connection.');

		view.button('Try again')!.click();
		await settled();

		expect(view.offsets()).toEqual([0, 50, 50]);
		expect(view.rows()).toBe(60);
		expect(host.querySelector('[role="alert"]')).toBeNull();
	});
});
