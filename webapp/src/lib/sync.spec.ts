import { ApiError } from '@/lib/api';
import * as row from '@/testing/fixtures';
import { installIndexedDB } from '@/testing/indexedDb';
import { installLocalStorage, MemoryStorage } from '@/testing/memoryStorage';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * What a full sync and a slice refresh do to the local copy, as the root CLAUDE.md has them: the
 * whole lists are replaced from the API, and only once that has succeeded is what is held per month
 * and per page thrown away. A sync that cannot reach the API leaves the last-seen ledger alone.
 */
const api = vi.hoisted(() => ({
	listAccounts: vi.fn(),
	listAccountTypes: vi.fn(),
	listCategories: vi.fn(),
	listTags: vi.fn(),
	settings: vi.fn(),
	getRoundUpRule: vi.fn(),
	listSubscriptions: vi.fn(),
	listPayees: vi.fn(),
	summary: vi.fn(),
	listTransactions: vi.fn(),
}));

vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), api }));

installIndexedDB();

const cache = await import('./cache');
const { fullSync, refreshSlices } = await import('./sync');
const { useBudgetStore } = await import('@/stores/budget');

const SEPTEMBER = { month: '2026-09' };
const offline = () => new ApiError(0, 'Network error. Check your connection.');

function answer(id: string): void {
	const ledger = row.ledger(id);
	api.listAccounts.mockResolvedValue({ accounts: ledger.accounts });
	api.listAccountTypes.mockResolvedValue({ accountTypes: ledger.accountTypes });
	api.listCategories.mockResolvedValue({ categories: ledger.categories });
	api.listTags.mockResolvedValue({ tags: ledger.tags });
	api.settings.mockResolvedValue({ settings: ledger.settings });
	api.getRoundUpRule.mockResolvedValue({ roundUpRule: ledger.roundUpRule });
	api.listSubscriptions.mockResolvedValue({ subscriptions: [row.subscription(id)] });
	api.listPayees.mockResolvedValue({ payees: [row.payee(id)] });
}

/** A copy with something in every kind of table. */
async function seed(): Promise<void> {
	await cache.replaceLedger(row.ledger('old'));
	await cache.replaceSubscriptions([row.subscription('old')]);
	await cache.replacePayees([row.payee('old')]);
	await cache.writeSummary(row.summary('2026-09'), 6);
	await cache.writeTransactionPage(SEPTEMBER, [row.transaction('old')], 1, 8);
}

beforeEach(async () => {
	installLocalStorage(new MemoryStorage());
	await cache.clearCache();
	setActivePinia(createPinia());
	for (const fn of Object.values(api)) fn.mockReset();
});

describe('full sync', () => {
	it('replaces the whole lists, then discards what is held per month and per page', async () => {
		await seed();
		answer('fresh');

		await fullSync();

		expect(row.ids((await cache.readLedger())!.accounts)).toEqual(['fresh']);
		expect(row.ids((await cache.readSubscriptions())!)).toEqual(['fresh']);
		expect(row.ids((await cache.readPayees())!)).toEqual(['fresh']);
		expect(await cache.readSummary('2026-09')).toBeNull();
		expect(await cache.readTransactionPage(SEPTEMBER, 50)).toBeNull();
	});

	it('leaves the last-seen copy as it was when the API cannot be reached', async () => {
		await seed();
		for (const call of Object.values(api)) call.mockRejectedValue(offline());

		await fullSync();

		expect(row.ids((await cache.readLedger())!.accounts)).toEqual(['old']);
		expect(row.ids((await cache.readPayees())!)).toEqual(['old']);
		expect(await cache.readSummary('2026-09')).not.toBeNull();
		expect(await cache.readTransactionPage(SEPTEMBER, 50)).not.toBeNull();
	});

	it('discards nothing per month when only part of the lists could be had', async () => {
		await seed();
		answer('fresh');
		api.listPayees.mockRejectedValue(offline());

		await fullSync();

		expect(await cache.readSummary('2026-09')).not.toBeNull();
		expect(await cache.readTransactionPage(SEPTEMBER, 50)).not.toBeNull();
	});

	it('has the month on show fetched again once its copy is gone', async () => {
		answer('fresh');
		api.summary.mockResolvedValueOnce(row.summary('2026-09', 'before')).mockResolvedValueOnce(row.summary('2026-09', 'after'));
		const budget = useBudgetStore();
		budget.month = '2026-09';
		await budget.load();

		await fullSync();

		expect(await cache.readSummary('2026-09')).toMatchObject({ marker: 'after' });
	});
});

describe('slice refresh', () => {
	it('drops only what the slices it names held', async () => {
		await seed();

		await refreshSlices(['summary']);

		expect(await cache.readSummary('2026-09')).toBeNull();
		expect(await cache.readTransactionPage(SEPTEMBER, 50)).not.toBeNull();

		await refreshSlices(['transactions']);

		expect(await cache.readTransactionPage(SEPTEMBER, 50)).toBeNull();
		expect(row.ids((await cache.readLedger())!.accounts)).toEqual(['old']);
	});

	it('replaces the payees when told they moved', async () => {
		await seed();
		answer('fresh');

		await refreshSlices(['payees']);

		expect(row.ids((await cache.readPayees())!)).toEqual(['fresh']);
	});
});
