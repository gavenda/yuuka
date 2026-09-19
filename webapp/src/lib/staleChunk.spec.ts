import { describe, expect, it } from 'vitest';
import { RELOAD_WINDOW_MS, shouldReloadForStaleChunk } from './staleChunk';

function fakeStore(initial: Record<string, string> = {}) {
	const values = new Map(Object.entries(initial));
	return {
		getItem: (key: string) => values.get(key) ?? null,
		setItem: (key: string, value: string) => void values.set(key, value),
	};
}

describe('shouldReloadForStaleChunk', () => {
	it('reloads on the first failure', () => {
		expect(shouldReloadForStaleChunk(1_000_000, fakeStore())).toBe(true);
	});

	it('refuses a second reload straight after, so a chunk that is really gone cannot loop', () => {
		const store = fakeStore();
		expect(shouldReloadForStaleChunk(1_000_000, store)).toBe(true);
		expect(shouldReloadForStaleChunk(1_000_000 + RELOAD_WINDOW_MS - 1, store)).toBe(false);
	});

	it('reloads again once the window has passed, for a later deploy in the same tab', () => {
		const store = fakeStore();
		expect(shouldReloadForStaleChunk(1_000_000, store)).toBe(true);
		expect(shouldReloadForStaleChunk(1_000_000 + RELOAD_WINDOW_MS, store)).toBe(true);
	});

	it('declines when storage is unusable rather than risk a loop', () => {
		const broken = {
			getItem: () => {
				throw new Error('blocked');
			},
			setItem: () => {
				throw new Error('blocked');
			},
		};
		expect(shouldReloadForStaleChunk(1_000_000, broken)).toBe(false);
	});
});
