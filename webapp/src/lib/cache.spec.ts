import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { installLocalStorage as install, MemoryStorage } from '@/testing/memoryStorage';
import { adoptCacheFor, clearCache, dropCacheFamily, readCache, snapshotKey, writeCache } from './cache';

describe('local cache', () => {
	let store: MemoryStorage;

	beforeEach(() => {
		store = new MemoryStorage();
		install(store);
		vi.useFakeTimers();
		vi.setSystemTime(1_000);
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	it('reads back what was written', () => {
		writeCache('ledger', { accounts: [1, 2] });
		expect(readCache('ledger')).toEqual({ accounts: [1, 2] });
	});

	it('reads as missing when nothing was written', () => {
		expect(readCache('ledger')).toBeNull();
	});

	it('ignores an entry written under another version', () => {
		store.setItem('yuuka.cache.ledger', JSON.stringify({ v: 0, at: 1, data: { accounts: [] } }));
		expect(readCache('ledger')).toBeNull();
	});

	it('ignores an entry that is not JSON', () => {
		store.setItem('yuuka.cache.ledger', '{oops');
		expect(readCache('ledger')).toBeNull();
	});

	it('ignores a value the guard rejects', () => {
		writeCache('ledger', { accounts: 'nope' });
		const isLedger = (data: unknown): data is { accounts: unknown[] } => Array.isArray((data as { accounts?: unknown }).accounts);
		expect(readCache('ledger', isLedger)).toBeNull();
	});

	it('keeps only the newest variants of a family when asked to', () => {
		for (const [index, month] of ['2026-06', '2026-07', '2026-08', '2026-09'].entries()) {
			vi.setSystemTime(1_000 + index);
			writeCache(`summary:${month}`, month, 2);
		}

		expect(readCache('summary:2026-06')).toBeNull();
		expect(readCache('summary:2026-07')).toBeNull();
		expect(readCache('summary:2026-08')).toBe('2026-08');
		expect(readCache('summary:2026-09')).toBe('2026-09');
	});

	it('does not trim one family to make room for another', () => {
		writeCache('summary:2026-09', 'a', 1);
		writeCache('transactions:month=2026-09', 'b', 1);

		expect(readCache('summary:2026-09')).toBe('a');
		expect(readCache('transactions:month=2026-09')).toBe('b');
	});

	it('drops a family except the variant to keep', () => {
		writeCache('summary:2026-08', 'a');
		writeCache('summary:2026-09', 'b');
		writeCache('ledger', 'c');

		dropCacheFamily('summary', 'summary:2026-09');

		expect(readCache('summary:2026-08')).toBeNull();
		expect(readCache('summary:2026-09')).toBe('b');
		expect(readCache('ledger')).toBe('c');
	});

	it('does not confuse a family with another that shares its prefix', () => {
		writeCache('summary:2026-09', 'a');
		writeCache('summaryextra:x', 'b');

		dropCacheFamily('summary');

		expect(readCache('summary:2026-09')).toBeNull();
		expect(readCache('summaryextra:x')).toBe('b');
	});

	it('makes room by dropping the oldest entries when the quota is full', () => {
		vi.setSystemTime(1_000);
		writeCache('summary:old', 'old');
		vi.setSystemTime(2_000);
		writeCache('summary:newer', 'newer');

		// The first attempt is refused, as if the oldest entry were what stood in the way.
		store.failNext = 1;

		vi.setSystemTime(3_000);
		writeCache('ledger', 'kept');

		expect(readCache('ledger')).toBe('kept');
		expect(readCache('summary:old')).toBeNull();
		expect(readCache('summary:newer')).toBe('newer');
	});

	it('gives up quietly when even an empty cache has no room', () => {
		store.full = true;

		expect(() => writeCache('ledger', 'x')).not.toThrow();
		expect(readCache('ledger')).toBeNull();
	});

	it('is a no-op when storage is unavailable', () => {
		install(() => {
			throw new DOMException('blocked', 'SecurityError');
		});

		expect(() => writeCache('ledger', 'x')).not.toThrow();
		expect(readCache('ledger')).toBeNull();
		expect(() => clearCache()).not.toThrow();
		expect(() => adoptCacheFor('auth0|a')).not.toThrow();
	});

	it('clears its own entries and nothing else', () => {
		store.setItem('yuuka.theme', 'dark');
		writeCache('ledger', 'x');
		adoptCacheFor('auth0|a');

		clearCache();

		expect(readCache('ledger')).toBeNull();
		expect(store.getItem('yuuka.cache.owner')).toBeNull();
		expect(store.getItem('yuuka.theme')).toBe('dark');
	});

	describe('ownership', () => {
		it('keeps the copy for the same person', () => {
			adoptCacheFor('auth0|a');
			writeCache('ledger', 'mine');

			adoptCacheFor('auth0|a');

			expect(readCache('ledger')).toBe('mine');
		});

		it('discards the copy when a different person signs in', () => {
			adoptCacheFor('auth0|a');
			writeCache('ledger', 'theirs');

			adoptCacheFor('auth0|b');

			expect(readCache('ledger')).toBeNull();
			expect(store.getItem('yuuka.cache.owner')).toBe('auth0|b');
		});

		it('discards a copy that has no recorded owner', () => {
			writeCache('ledger', 'unknown');
			adoptCacheFor('auth0|a');
			expect(readCache('ledger')).toBeNull();
		});

		it('discards the copy and records no owner when the person cannot be identified', () => {
			adoptCacheFor('auth0|a');
			writeCache('ledger', 'theirs');

			adoptCacheFor(undefined);

			expect(readCache('ledger')).toBeNull();
			expect(store.getItem('yuuka.cache.owner')).toBeNull();
		});
	});

	describe('snapshotKey', () => {
		it('gives the same key however the filters were ordered', () => {
			expect(snapshotKey('transactions', { month: '2026-09', accountId: 'a' })).toBe(
				snapshotKey('transactions', { accountId: 'a', month: '2026-09' }),
			);
		});

		it('leaves out filters that are not set', () => {
			expect(snapshotKey('transactions', { month: '2026-09', search: '', accountId: undefined })).toBe('transactions:month=2026-09');
		});

		it('tells different filters apart', () => {
			expect(snapshotKey('transactions', { month: '2026-09' })).not.toBe(snapshotKey('transactions', { month: '2026-08' }));
			expect(snapshotKey('transactions', {})).toBe('transactions:');
		});
	});
});
