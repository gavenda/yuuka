import { describe, expect, it } from 'vitest';
import type { Payee } from '@/types';
import { isExhausted, rankPayees } from './payees';

/** Server order is most-used-first, which `rankPayees` must preserve on ties. */
const make = (payee: string): Payee => ({
	id: payee,
	payee,
	kind: 'expense',
	accountId: null,
	accountName: null,
	toAccountId: null,
	toAccountName: null,
	categoryId: null,
	categoryName: null,
	categoryColor: null,
	notes: '',
	usedCount: 1,
	lastUsedAt: '2026-09-01T00:00:00.000Z',
});

const all = ['Corner Market', 'Tesco Corner', 'Baker Street', 'Cornershop'].map(make);
const names = (list: Payee[]) => list.map((entry) => entry.payee);

describe('rankPayees', () => {
	it('offers the most-used entries when nothing is typed', () => {
		expect(names(rankPayees(all, ''))).toEqual(names(all));
		expect(names(rankPayees(all, '   '))).toEqual(names(all));
	});

	it('matches anywhere in the name', () => {
		expect(names(rankPayees(all, 'corner'))).toContain('Tesco Corner');
	});

	it('floats prefix matches above mid-string ones', () => {
		// Typing "cor" should reach "Corner Market" before "Tesco Corner".
		const ranked = names(rankPayees(all, 'cor'));
		expect(ranked.indexOf('Corner Market')).toBeLessThan(ranked.indexOf('Tesco Corner'));
		expect(ranked.indexOf('Cornershop')).toBeLessThan(ranked.indexOf('Tesco Corner'));
	});

	it('keeps the server ordering among equally good matches', () => {
		// Both start with "corner", so neither should overtake the other.
		expect(names(rankPayees(all, 'corner')).slice(0, 2)).toEqual(['Corner Market', 'Cornershop']);
	});

	it('is case-insensitive', () => {
		expect(names(rankPayees(all, 'BAKER'))).toEqual(['Baker Street']);
	});

	it('returns nothing when nothing matches', () => {
		expect(rankPayees(all, 'zzz')).toEqual([]);
	});

	it('honours the limit', () => {
		expect(rankPayees(all, '', 2)).toHaveLength(2);
		expect(rankPayees(all, 'cor', 1)).toHaveLength(1);
	});

	it('copes with an empty history', () => {
		expect(rankPayees([], 'anything')).toEqual([]);
	});
});

describe('isExhausted', () => {
	it('is true once the only match is exactly what was typed', () => {
		expect(isExhausted([make('Corner Market')], 'Corner Market')).toBe(true);
		expect(isExhausted([make('Corner Market')], 'corner market')).toBe(true);
	});

	it('is false while there is still something to choose', () => {
		expect(isExhausted([make('Corner Market')], 'corner')).toBe(false);
		expect(isExhausted([make('Corner Market'), make('Cornershop')], 'Corner Market')).toBe(false);
		expect(isExhausted([], 'corner')).toBe(false);
	});
});
