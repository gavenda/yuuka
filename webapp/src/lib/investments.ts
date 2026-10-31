/**
 * PSE investments, worked out in the browser. The twin of Android's `domain/Investments.kt`, and both follow
 * the server's arithmetic (`server/investments.ts`), so a trade made offline shows the figures the server will
 * agree with once it lands. Money is in centavos; a price in ten-thousandths of a peso (`…E4`), since the PSE's
 * smallest tick is ₱0.0001; shares are whole.
 */
import { currencySymbol } from '@/lib/money';
import type { Security, Trade, TradeKind } from '@/types';

/** The value in centavos of `shares` at `priceE4`, rounded half up. */
export const valueOf = (shares: number, priceE4: number) => Math.floor((shares * priceE4 + 50) / 100);

/** The cash a trade moves, signed: a buy pays the gross and the fees, a sell and a dividend bring in the gross less them. */
export function tradeCash(kind: TradeKind, shares: number, priceE4: number, fees: number): number {
	const gross = valueOf(shares, priceE4);
	return kind === 'buy' ? -(gross + fees) : gross - fees;
}

function priceDecimal(priceE4: number): string {
	const whole = Math.floor(priceE4 / 10_000);
	let fraction = String(priceE4 % 10_000).padStart(4, '0');
	while (fraction.length > 2 && fraction.endsWith('0')) fraction = fraction.slice(0, -1);
	return `${whole.toLocaleString('en-US')}.${fraction}`;
}

/** A price as PSE quotes it: at least two decimals, up to four when the tick needs them. */
export const formatPrice = (priceE4: number) => `${currencySymbol('PHP')}${priceDecimal(priceE4)}`;

/** `formatPrice` without the peso sign or grouping, as a field holds it. */
export const priceInput = (priceE4: number) => priceDecimal(priceE4).replaceAll(',', '');

/** `part` as a signed percentage of `whole`, one decimal. */
export function percentChange(part: number, whole: number): string {
	if (whole === 0) return '—';
	const value = (part * 100) / whole;
	return `${value >= 0 ? '+' : ''}${value.toFixed(1)}%`;
}

/** A typed price in ten-thousandths of a peso, split on the decimal point like money: null unless above zero with at most four decimals. */
export function parsePrice(input: string): number | null {
	const match = /^(\d*)(?:\.(\d{0,4}))?$/.exec(input.trim().replaceAll(',', ''));
	if (!match) return null;
	const [, whole = '', fraction = ''] = match;
	if (whole === '' && fraction === '') return null;
	const value = Number(whole || '0') * 10_000 + Number(fraction.padEnd(4, '0'));
	return Number.isSafeInteger(value) && value > 0 ? value : null;
}

/** A typed share count: a whole number above zero, or null. */
export function parseShares(input: string): number | null {
	const text = input.trim().replaceAll(',', '');
	if (!/^\d+$/.test(text)) return null;
	const value = Number(text);
	return Number.isSafeInteger(value) && value > 0 ? value : null;
}

export interface PseFees {
	commission: number;
	vat: number;
	pseFee: number;
	sccp: number;
	salesTax: number;
	total: number;
}

const share = (amount: number, numerator: number, denominator: number) =>
	Math.floor((amount * numerator + Math.floor(denominator / 2)) / denominator);

/** Commission 0.25% (₱20 at least), 12% VAT on it, the PSE's 0.005%, SCCP's 0.01%, and on a sell the 0.1% stock transaction tax. */
export function pseFees(gross: number, sell: boolean): PseFees {
	if (gross <= 0) return { commission: 0, vat: 0, pseFee: 0, sccp: 0, salesTax: 0, total: 0 };
	const commission = Math.max(share(gross, 25, 10_000), 2_000);
	const fees = {
		commission,
		vat: share(commission, 12, 100),
		pseFee: share(gross, 5, 100_000),
		sccp: share(gross, 1, 10_000),
		salesTax: sell ? share(gross, 1, 1_000) : 0,
	};
	return { ...fees, total: fees.commission + fees.vat + fees.pseFee + fees.sccp + fees.salesTax };
}

/** The 10% final tax withheld from a cash dividend. */
export const dividendTax = (gross: number) => share(gross, 10, 100);

/** The PSE's board lot for a stock trading at `priceE4`: the fewest shares the main board takes. */
export function boardLot(priceE4: number): number {
	if (priceE4 < 100) return 1_000_000;
	if (priceE4 < 500) return 100_000;
	if (priceE4 < 5_000) return 10_000;
	if (priceE4 < 50_000) return 1_000;
	if (priceE4 < 500_000) return 100;
	if (priceE4 < 10_000_000) return 10;
	return 5;
}

export interface Position {
	accountId: string;
	symbol: string;
	shares: number;
	/** What the shares held cost, buy fees included. */
	costBasis: number;
	realised: number;
	dividends: number;
}

/**
 * Works the trades through in date order, average cost, as the server does. Null when a sell takes more than is
 * held at that point.
 */
export function positions(trades: readonly Trade[]): Position[] | null {
	const ordered = [...trades].sort((a, b) => a.occurredOn.localeCompare(b.occurredOn) || a.createdAt.localeCompare(b.createdAt));
	const byKey = new Map<string, Position>();
	for (const trade of ordered) {
		const key = `${trade.accountId}\u0000${trade.symbol}`;
		let position = byKey.get(key);
		if (!position) {
			position = { accountId: trade.accountId, symbol: trade.symbol, shares: 0, costBasis: 0, realised: 0, dividends: 0 };
			byKey.set(key, position);
		}
		const gross = valueOf(trade.shares, trade.price);
		if (trade.kind === 'buy') {
			position.shares += trade.shares;
			position.costBasis += gross + trade.fees;
		} else if (trade.kind === 'sell') {
			if (trade.shares > position.shares) return null;
			const cost = Math.round((position.costBasis * trade.shares) / position.shares);
			position.realised += gross - trade.fees - cost;
			position.costBasis -= cost;
			position.shares -= trade.shares;
		} else {
			position.dividends += gross - trade.fees;
		}
	}
	return [...byKey.values()];
}

/** A position with what it is worth: one row of the Investments screen. */
export interface Holding {
	position: Position;
	symbol: string;
	name: string;
	accountName: string;
	bank: string | null;
	/** The latest close, or the average cost when the stock has no price yet. */
	priceE4: number;
	changeBp: number | null;
	asOf: string | null;
	shares: number;
	costBasis: number;
	avgCostE4: number;
	marketValue: number;
	unrealised: number;
	previousCloseE4: number;
	dayChange: number;
}

/** Fills in what a position is worth at `priceE4`. */
export function holdingOf(
	position: Position,
	details: { name: string; accountName: string; bank: string | null; priceE4: number; changeBp: number | null; asOf: string | null },
): Holding {
	const marketValue = valueOf(position.shares, details.priceE4);
	const previousCloseE4 =
		details.changeBp === null ? details.priceE4 : Math.round((details.priceE4 * 10_000) / (10_000 + details.changeBp));
	return {
		...details,
		position,
		symbol: position.symbol,
		shares: position.shares,
		costBasis: position.costBasis,
		avgCostE4: position.shares === 0 ? 0 : Math.round((position.costBasis * 100) / position.shares),
		marketValue,
		unrealised: marketValue - position.costBasis,
		previousCloseE4,
		dayChange: marketValue - valueOf(position.shares, previousCloseE4),
	};
}

/** Every position, valued at its stock's latest close. */
export function holdings(
	trades: readonly Trade[],
	securities: ReadonlyMap<string, Security>,
	account: (id: string) => { name: string; bank: string | null } | undefined,
): Holding[] {
	return (positions(trades) ?? []).map((position) => {
		const security = securities.get(position.symbol);
		const owner = account(position.accountId);
		return holdingOf(position, {
			name: security?.name ?? trades.find((trade) => trade.symbol === position.symbol)?.name ?? position.symbol,
			accountName: owner?.name ?? trades.find((trade) => trade.accountId === position.accountId)?.accountName ?? '',
			bank: owner?.bank ?? null,
			priceE4: security?.price ?? (position.shares > 0 ? Math.round((position.costBasis * 100) / position.shares) : 0),
			changeBp: security?.changeBp ?? null,
			asOf: security?.asOf ?? null,
		});
	});
}
