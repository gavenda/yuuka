import { describe, expect, it } from 'vitest';
import type { Trade } from '@/types';
import {
	boardLot,
	etfDividendTax,
	holdingOf,
	ibkrFees,
	securityLabel,
	tickerOf,
	totalsByCurrency,
	formatUnits,
	legLabels,
	parsePrice,
	parseUnits,
	positions,
	priceInput,
	pseFees,
	tradeCash,
	tradeWord,
	UNIT,
	unitsFor,
	unitsHeldBefore,
	valueOf,
} from './investments';

/** The same figures `test/investments.spec.ts` and Android's `InvestmentsTest` pin: all three must agree. `shares` are whole. */
const trade = (kind: Trade['kind'], occurredOn: string, shares: number, price: number, fees = 0): Trade => ({
	id: `${kind}${occurredOn}`,
	accountId: 'a',
	accountName: null,
	symbol: 'X',
	name: null,
	kind,
	occurredOn,
	units: shares * UNIT,
	price,
	gross: null,
	fees,
	amount: tradeCash(kind, shares * UNIT, price, fees),
	transactionId: `t${occurredOn}`,
	createdAt: '',
	updatedAt: '',
});

describe('investments', () => {
	it('values units to the centavo, rounding half up', () => {
		expect(valueOf(1_000_000, 1_180_000)).toBe(1_180_000);
		expect(valueOf(30_000, 5)).toBe(0);
		expect(valueOf(100_000, 5)).toBe(1);
		// 1,234.5678 units at ₱1,942.3725, past what a double multiplies exactly.
		expect(valueOf(12_345_678, 19_423_725)).toBe(239_799_054);
	});

	it("signs a trade's cash by what it does, a fund's from what the trust bank said", () => {
		expect(tradeCash('buy', 100_000, 1_000_000, 2_000)).toBe(-102_000);
		expect(tradeCash('sell', 100_000, 1_000_000, 2_000)).toBe(98_000);
		expect(tradeCash('dividend', 100_000, 10_000, 100)).toBe(900);
		// ₱10,000.00 for 5.1483 units, which at ₱1,942.3725 come to ₱9,999.92.
		expect(tradeCash('buy', 51_483, 19_423_725, 0, 1_000_000)).toBe(-1_000_000);
	});

	it('takes shares out at the average cost and realises the rest', () => {
		expect(
			positions([
				trade('buy', '2026-01-01', 100, 100_000),
				trade('buy', '2026-02-01', 100, 200_000),
				trade('sell', '2026-03-01', 50, 300_000),
			]),
		).toEqual([{ accountId: 'a', symbol: 'X', units: 1_500_000, costBasis: 225_000, realised: 75_000, dividends: 0 }]);
	});

	it('refuses a history that sells more than it holds', () => {
		expect(positions([trade('sell', '2026-01-01', 1, 100_000), trade('buy', '2026-02-01', 100, 100_000)])).toBeNull();
	});

	it("works a day's trades through in time order, an untimed one first", () => {
		expect(positions([trade('buy', '2026-01-01T10:00', 100, 100_000), trade('sell', '2026-01-01T09:30', 10, 100_000)])).toBeNull();
		expect(positions([trade('sell', '2026-01-01T09:30', 10, 100_000), trade('buy', '2026-01-01', 100, 100_000)])?.[0]?.units).toBe(900_000);
	});

	it('charges the PSE schedule, with the ₱20 minimum commission', () => {
		expect(pseFees(1_180_000, false)).toEqual({ commission: 2_950, vat: 354, pseFee: 59, sccp: 118, salesTax: 0, total: 3_481 });
		expect(pseFees(100_000, true)).toEqual({ commission: 2_000, vat: 240, pseFee: 5, sccp: 10, salesTax: 100, total: 2_355 });
		// A broker that waives the minimum (DragonFi) charges the 0.25% alone: ₱2.50 on ₱1,000.00.
		expect(pseFees(100_000, true, 0)).toEqual({ commission: 250, vat: 30, pseFee: 5, sccp: 10, salesTax: 100, total: 395 });
	});

	it('rounds fees up by group for a broker that does, as a DragonFi statement reads', () => {
		// 400 RCR at ₱6.03: commission with VAT ₱6.7536 is charged ₱6.76, PSE and SCCP ₱0.3618 is charged ₱0.37.
		expect(pseFees(241_200, false, 0, true)).toEqual({ commission: 603, vat: 73, pseFee: 12, sccp: 25, salesTax: 0, total: 713 });
	});

	it('parses a price to four decimals, split on the point', () => {
		expect(parsePrice('118')).toBe(1_180_000);
		expect(parsePrice('0.57')).toBe(5_700);
		expect(parsePrice('0.0001')).toBe(1);
		expect(parsePrice('0.00001')).toBeNull();
		expect(parsePrice('0.840905')).toBe(8_409);
		expect(parsePrice('0.840950')).toBe(8_410);
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

describe('unitsHeldBefore', () => {
	it('is what was held at the start of the day, as a dividend is paid on', () => {
		const trades = [
			trade('buy', '2026-01-05', 1000, 100_000),
			trade('sell', '2026-02-10T10:00', 400, 110_000),
			trade('buy', '2026-03-01', 200, 105_000),
		];
		expect(unitsHeldBefore(trades, 'a', 'X', '2026-01-05')).toBe(0);
		expect(unitsHeldBefore(trades, 'a', 'X', '2026-02-10')).toBe(10_000_000);
		expect(unitsHeldBefore(trades, 'a', 'X', '2026-02-11')).toBe(6_000_000);
		expect(unitsHeldBefore(trades, 'a', 'X', '2026-03-02')).toBe(8_000_000);
		expect(unitsHeldBefore(trades, 'b', 'X', '2026-03-02')).toBe(0);
	});
});

describe('UITFs', () => {
	it('gives the units an amount buys at a NAVPU, to the ten-thousandth', () => {
		expect(unitsFor(1_000_000, 19_423_725)).toBe(51_483);
		expect(unitsFor(500_000, 1_000_000)).toBe(500_000);
	});

	it('reads and writes units with up to four decimals, a stock in whole shares', () => {
		expect(parseUnits('5.1483', false)).toBe(51_483);
		expect(parseUnits('1,000', false)).toBe(10_000_000);
		expect(parseUnits('1.23456', false)).toBeNull();
		expect(parseUnits('1.5', true)).toBeNull();
		expect(parseUnits('100', true)).toBe(1_000_000);
		expect(formatUnits(51_483)).toBe('5.1483');
		expect(formatUnits(12_345_000)).toBe('1,234.5');
		expect(formatUnits(1_000_000)).toBe('100');
	});

	it('names a fund’s trades and its cash leg as the server does', () => {
		expect(tradeWord('buy', 'UITF-128')).toBe('subscribe');
		expect(tradeWord('sell', 'UITF-128')).toBe('redeem');
		expect(tradeWord('dividend', 'UITF-128')).toBe('distribution');
		expect(tradeWord('buy', 'BDO')).toBe('buy');
		expect(legLabels('buy', 'UITF-128', 'BDO PESO MONEY MARKET FUND', 51_483)).toEqual({
			payee: 'BDO PESO MONEY MARKET FUND',
			notes: 'Subscribe 5.1483 units',
		});
		expect(legLabels('sell', 'BDO', 'BDO Unibank, Inc.', 1_000_000)).toEqual({ payee: 'BDO', notes: 'Sell 100 BDO' });
	});
});

/** The same figures Android's `InvestmentsTest` pins for an ETF. */
describe('ETFs', () => {
	it('reads an ETF by its ticker, on its leg as anywhere else', () => {
		expect(tickerOf('ETF-VWRA.L')).toBe('VWRA');
		expect(tickerOf('ETF-VOO')).toBe('VOO');
		expect(tickerOf('BDO')).toBe('BDO');
		expect(securityLabel('ETF-CSPX.L', 'iShares Core S&P 500')).toBe('CSPX');
		expect(legLabels('buy', 'ETF-CSPX.L', 'iShares Core S&P 500', 15_000)).toEqual({ payee: 'CSPX', notes: 'Buy 1.5 CSPX' });
	});

	it('charges IBKR Fixed in the US: half a cent a share, a dollar at least, 1% of the trade at most', () => {
		// 10 shares of $612.47: $0.05 by the share, so the $1.00 minimum.
		expect(ibkrFees(612_470, 100_000, 'US', 'USD')).toBe(100);
		// 1,000 shares of $50: $5.00, under 1% of $50,000.
		expect(ibkrFees(5_000_000, 10_000_000, 'US', 'USD')).toBe(500);
		// Half a share of $10: 1% of $5.00 caps it at $0.05.
		expect(ibkrFees(500, 5_000, 'US', 'USD')).toBe(5);
		// A twentieth of a share of $15: never under a cent.
		expect(ibkrFees(75, 500, 'US', 'USD')).toBe(1);
	});

	it('charges IBKR Fixed on the LSE: 0.05%, at least $4 or £3 an order, less for a fractional one', () => {
		expect(ibkrFees(105_015, 15_000, 'LSE', 'USD')).toBe(170);
		expect(ibkrFees(105_015, 10_000, 'LSE', 'USD')).toBe(400);
		expect(ibkrFees(2_000_000, 100_000, 'LSE', 'USD')).toBe(1_000);
		expect(ibkrFees(50_000, 10_000, 'LSE', 'GBP')).toBe(300);
		expect(ibkrFees(50_000, 5_000, 'LSE', 'GBP')).toBe(100);
	});

	it('withholds 25% from a US-listed ETF’s dividend and nothing from a UCITS one’s', () => {
		expect(etfDividendTax(10_000, 'US')).toBe(2_500);
		expect(etfDividendTax(10_000, 'LSE')).toBe(0);
	});

	it('adds holdings a currency at a time, the display currency first', () => {
		const holding = (symbol: string, currency: string, value: number) =>
			holdingOf(
				{ accountId: 'a', symbol, units: UNIT, costBasis: value, realised: 0, dividends: 0 },
				{ name: symbol, accountName: 'a', currency, priceE4: value * 100, changeBp: null, asOf: null },
			);
		const totals = totalsByCurrency([holding('ETF-VOO', 'USD', 600), holding('BDO', 'PHP', 120), holding('ALI', 'PHP', 30)], 'PHP');
		expect(totals.map((total) => [total.currency, total.marketValue])).toEqual([
			['PHP', 150],
			['USD', 600],
		]);
	});
});
