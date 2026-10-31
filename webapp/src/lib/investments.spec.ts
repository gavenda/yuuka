import { describe, expect, it } from 'vitest';
import type { Trade } from '@/types';
import { boardLot, parsePrice, positions, priceInput, pseFees, tradeCash, valueOf } from './investments';

/** The same figures `test/investments.spec.ts` and Android's `InvestmentsTest` pin: all three must agree. */
const trade = (kind: Trade['kind'], occurredOn: string, shares: number, price: number, fees = 0): Trade => ({
	id: `${kind}${occurredOn}`,
	accountId: 'a',
	accountName: null,
	symbol: 'X',
	name: null,
	kind,
	occurredOn,
	shares,
	price,
	fees,
	amount: tradeCash(kind, shares, price, fees),
	transactionId: `t${occurredOn}`,
	createdAt: '',
	updatedAt: '',
});

describe('investments', () => {
	it('values shares to the centavo, rounding half up', () => {
		expect(valueOf(100, 1_180_000)).toBe(1_180_000);
		expect(valueOf(3, 5)).toBe(0);
		expect(valueOf(10, 5)).toBe(1);
	});

	it("signs a trade's cash by what it does", () => {
		expect(tradeCash('buy', 10, 1_000_000, 2_000)).toBe(-102_000);
		expect(tradeCash('sell', 10, 1_000_000, 2_000)).toBe(98_000);
		expect(tradeCash('dividend', 10, 10_000, 100)).toBe(900);
	});

	it('takes shares out at the average cost and realises the rest', () => {
		expect(
			positions([
				trade('buy', '2026-01-01', 100, 100_000),
				trade('buy', '2026-02-01', 100, 200_000),
				trade('sell', '2026-03-01', 50, 300_000),
			]),
		).toEqual([{ accountId: 'a', symbol: 'X', shares: 150, costBasis: 225_000, realised: 75_000, dividends: 0 }]);
	});

	it('refuses a history that sells more than it holds', () => {
		expect(positions([trade('sell', '2026-01-01', 1, 100_000), trade('buy', '2026-02-01', 100, 100_000)])).toBeNull();
	});

	it('charges the PSE schedule, with the ₱20 minimum commission', () => {
		expect(pseFees(1_180_000, false)).toEqual({ commission: 2_950, vat: 354, pseFee: 59, sccp: 118, salesTax: 0, total: 3_481 });
		expect(pseFees(100_000, true)).toEqual({ commission: 2_000, vat: 240, pseFee: 5, sccp: 10, salesTax: 100, total: 2_355 });
	});

	it('parses a price to four decimals, split on the point', () => {
		expect(parsePrice('118')).toBe(1_180_000);
		expect(parsePrice('0.57')).toBe(5_700);
		expect(parsePrice('0.0001')).toBe(1);
		expect(parsePrice('0.00001')).toBeNull();
		expect(parsePrice('0')).toBeNull();
	});

	it('quotes a price as the PSE does', () => {
		expect(priceInput(1_180_000)).toBe('118.00');
		expect(priceInput(125)).toBe('0.0125');
		expect(priceInput(12_345_000)).toBe('1234.50');
	});

	it('knows the board lot by price band', () => {
		expect(boardLot(1_180_000)).toBe(10);
		expect(boardLot(5_700)).toBe(1_000);
		expect(boardLot(50)).toBe(1_000_000);
	});
});
