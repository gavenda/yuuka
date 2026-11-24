/**
 * PSE stocks and UITFs, worked out in the browser. The twin of Android's `domain/Investments.kt`, and both follow
 * the server's arithmetic (`server/investments.ts`), so a trade made offline shows the figures the server will
 * agree with once it lands. Money is in centavos; a price in ten-thousandths of a peso (`…E4`), since the PSE's
 * smallest tick is ₱0.0001 and a NAVPU is quoted to four decimals; a quantity in ten-thousandths of a share or
 * a unit (`units`), since a fund's units are fractional — a stock's are always whole shares.
 */
import { currencySymbol } from '@/lib/money';
import type { Security, Trade, TradeKind } from '@/types';

/** Ten-thousandths of a unit in one share or unit. */
export const UNIT = 10_000;

/** A fund's symbol is `UITF-` and its id on uitf.com.ph; no PSE symbol has a hyphen. */
export const FUND_PREFIX = 'UITF-';

/** Whether `symbol` names a UITF rather than a PSE stock. */
export const isFund = (symbol: string) => symbol.startsWith(FUND_PREFIX);

/**
 * The value in centavos of `units` at `priceE4`, rounded half up. The product of two ten-thousandths can pass what
 * a double holds exactly, so it is worked out in BigInt.
 */
export const valueOf = (units: number, priceE4: number) => Number((BigInt(units) * BigInt(priceE4) + 500_000n) / 1_000_000n);

/** The units `gross` centavos buy at `priceE4`, to the ten-thousandth, rounded half up: what a trust bank gives for an amount. */
export function unitsFor(gross: number, priceE4: number): number {
	if (priceE4 <= 0) return 0;
	return Number((BigInt(gross) * 1_000_000n + BigInt(priceE4) / 2n) / BigInt(priceE4));
}

/** A trade's cash before fees: what the trust bank said (`gross`), or the units at the price. */
export const tradeGross = (units: number, priceE4: number, gross: number | null = null) => gross ?? valueOf(units, priceE4);

/** The cash a trade moves, signed: a buy pays the gross and the fees, a sell and a dividend bring in the gross less them. */
export function tradeCash(kind: TradeKind, units: number, priceE4: number, fees: number, gross: number | null = null): number {
	const value = tradeGross(units, priceE4, gross);
	return kind === 'buy' ? -(value + fees) : value - fees;
}

/** The word for a trade, as `investments.*` names it: a fund's are a subscription, a redemption and a distribution. */
export function tradeWord(kind: TradeKind, symbol: string): 'buy' | 'sell' | 'dividend' | 'subscribe' | 'redeem' | 'distribution' {
	if (!isFund(symbol)) return kind;
	return kind === 'buy' ? 'subscribe' : kind === 'sell' ? 'redeem' : 'distribution';
}

/** What a security is called where one word is wanted: a stock's ticker, a fund's name (its symbol means nothing to anyone). */
export const securityLabel = (symbol: string, name: string | null | undefined) => (isFund(symbol) ? (name ?? symbol) : symbol);

/** What a fund's trades are called on its cash leg, as the server writes it. */
const FUND_VERBS: Record<TradeKind, string> = { buy: 'Subscribe', sell: 'Redeem', dividend: 'Distribution on' };

/**
 * A trade's cash leg's payee and notes, as the server writes them: a stock's ticker and `Buy 1,000 BDO`, a fund's
 * name and `Subscribe 1,234.5678 units`.
 */
export function legLabels(kind: TradeKind, symbol: string, name: string | null, units: number): { payee: string; notes: string } {
	const count = formatUnits(units);
	if (isFund(symbol)) return { payee: (name ?? symbol).slice(0, 120), notes: `${FUND_VERBS[kind]} ${count} units` };
	return { payee: symbol, notes: `${kind[0]!.toUpperCase()}${kind.slice(1)} ${count} ${symbol}` };
}

function unitsDecimal(units: number, grouped: boolean): string {
	const whole = Math.floor(units / UNIT);
	const fraction = String(units % UNIT)
		.padStart(4, '0')
		.replace(/0+$/, '');
	const head = grouped ? whole.toLocaleString('en-US') : String(whole);
	return fraction ? `${head}.${fraction}` : head;
}

/** Shares or units as they are read: grouped, with as many of the four decimals as they need. */
export const formatUnits = (units: number) => unitsDecimal(units, true);

/** `formatUnits` without grouping, as a field holds it. */
export const unitsInput = (units: number) => unitsDecimal(units, false);

/**
 * A typed quantity in ten-thousandths, split on the decimal point like money: a whole number of shares for a
 * stock (`whole`), up to four decimals of a unit for a fund. Null unless above zero.
 */
export function parseUnits(input: string, whole: boolean): number | null {
	const pattern = whole ? /^(\d+)()$/ : /^(\d*)(?:\.(\d{0,4}))?$/;
	const match = pattern.exec(input.trim().replaceAll(',', ''));
	if (!match) return null;
	const [, head = '', fraction = ''] = match;
	if (head === '' && fraction === '') return null;
	const value = Number(head || '0') * UNIT + Number(fraction.padEnd(4, '0'));
	return Number.isSafeInteger(value) && value > 0 ? value : null;
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

/**
 * A typed price in ten-thousandths of a peso, split on the decimal point like money: null unless above zero. A
 * NAVPU a trust bank quotes to six decimals is taken and rounded half up to four, as the server reads it.
 */
export function parsePrice(input: string): number | null {
	const match = /^(\d*)(?:\.(\d{0,8}))?$/.exec(input.trim().replaceAll(',', ''));
	if (!match) return null;
	const [, whole = '', fraction = ''] = match;
	if (whole === '' && fraction === '') return null;
	const digits = fraction.padEnd(5, '0');
	const value = Number(whole || '0') * 10_000 + Number(digits.slice(0, 4)) + (digits[4]! >= '5' ? 1 : 0);
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

/** The least commission most PSE brokers charge on a trade, in centavos: ₱20. */
export const MIN_COMMISSION = 2_000;

/**
 * Commission 0.25% (at least `minCommission`, ₱20 unless the broker waives it), 12% VAT on it, the PSE's 0.005%,
 * SCCP's 0.01%, and on a sell the 0.1% stock transaction tax.
 *
 * Each fee is rounded to the nearest centavo, unless the broker `roundsUp` as DragonFi does: it charges the
 * commission with its VAT, and the PSE and SCCP fees together, each rounded up to the next centavo. The lines
 * still add up to what is charged — the commission and the PSE fee are shown to the nearest centavo, and the VAT
 * and the SCCP fee take what is left of their group.
 */
export function pseFees(gross: number, sell: boolean, minCommission: number = MIN_COMMISSION, roundsUp = false): PseFees {
	if (gross <= 0) return { commission: 0, vat: 0, pseFee: 0, sccp: 0, salesTax: 0, total: 0 };
	if (roundsUp) return pseFeesRoundedUp(gross, sell, minCommission);
	const commission = Math.max(share(gross, 25, 10_000), minCommission);
	const fees = {
		commission,
		vat: share(commission, 12, 100),
		pseFee: share(gross, 5, 100_000),
		sccp: share(gross, 1, 10_000),
		salesTax: sell ? share(gross, 1, 1_000) : 0,
	};
	return { ...fees, total: fees.commission + fees.vat + fees.pseFee + fees.sccp + fees.salesTax };
}

/** A hundred-thousandth of a centavo: fine enough that every fee on a whole-centavo gross is exact in it. */
const FINE = 100_000;

function pseFeesRoundedUp(gross: number, sell: boolean, minCommission: number): PseFees {
	const commission = Math.max(gross * 250, minCommission * FINE);
	const vat = (commission * 12) / 100;
	const pseFee = gross * 5;
	const sccp = gross * 10;
	const nearest = (fine: number) => Math.floor((fine + FINE / 2) / FINE);
	const up = (fine: number) => Math.ceil(fine / FINE);
	const fees = {
		commission: nearest(commission),
		vat: up(commission + vat) - nearest(commission),
		pseFee: nearest(pseFee),
		sccp: up(pseFee + sccp) - nearest(pseFee),
		salesTax: sell ? up(gross * 100) : 0,
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
	/** Ten-thousandths of a share or a unit held. */
	units: number;
	/** What the units held cost, buy fees included. */
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
			position = { accountId: trade.accountId, symbol: trade.symbol, units: 0, costBasis: 0, realised: 0, dividends: 0 };
			byKey.set(key, position);
		}
		const gross = tradeGross(trade.units, trade.price, trade.gross);
		if (trade.kind === 'buy') {
			position.units += trade.units;
			position.costBasis += gross + trade.fees;
		} else if (trade.kind === 'sell') {
			if (trade.units > position.units) return null;
			const cost = Math.round((position.costBasis * trade.units) / position.units);
			position.realised += gross - trade.fees - cost;
			position.costBasis -= cost;
			position.units -= trade.units;
		} else {
			position.dividends += gross - trade.fees;
		}
	}
	return [...byKey.values()];
}

/**
 * The units of `symbol` in `accountId` held at the start of `day` (`YYYY-MM-DD`), its own trades left out: what
 * a dividend or a distribution paid that day is most likely paid on. Zero for a history that does not stand.
 */
export function unitsHeldBefore(trades: readonly Trade[], accountId: string, symbol: string, day: string): number {
	const before = trades.filter((trade) => trade.accountId === accountId && trade.symbol === symbol && trade.occurredOn.slice(0, 10) < day);
	return positions(before)?.[0]?.units ?? 0;
}

/** What a unit cost on average, in ten-thousandths of a peso: `costBasis` centavos over `units`. */
export const averagePriceE4 = (costBasis: number, units: number) => (units === 0 ? 0 : Math.round((costBasis * 1_000_000) / units));

/** A position with what it is worth: one row of the Investments screen. */
export interface Holding {
	position: Position;
	symbol: string;
	/** Whether it is a UITF rather than a PSE stock. */
	fund: boolean;
	name: string;
	accountName: string;
	/** The latest close, or the average cost when the stock has no price yet. */
	priceE4: number;
	changeBp: number | null;
	asOf: string | null;
	units: number;
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
	details: { name: string; accountName: string; priceE4: number; changeBp: number | null; asOf: string | null },
): Holding {
	const marketValue = valueOf(position.units, details.priceE4);
	const previousCloseE4 =
		details.changeBp === null ? details.priceE4 : Math.round((details.priceE4 * 10_000) / (10_000 + details.changeBp));
	return {
		...details,
		position,
		symbol: position.symbol,
		fund: isFund(position.symbol),
		units: position.units,
		costBasis: position.costBasis,
		avgCostE4: averagePriceE4(position.costBasis, position.units),
		marketValue,
		unrealised: marketValue - position.costBasis,
		previousCloseE4,
		dayChange: marketValue - valueOf(position.units, previousCloseE4),
	};
}

/** Every position, valued at its security's latest close or NAVPU. */
export function holdings(
	trades: readonly Trade[],
	securities: ReadonlyMap<string, Security>,
	account: (id: string) => { name: string } | undefined,
): Holding[] {
	return (positions(trades) ?? []).map((position) => {
		const security = securities.get(position.symbol);
		const owner = account(position.accountId);
		return holdingOf(position, {
			name: security?.name ?? trades.find((trade) => trade.symbol === position.symbol)?.name ?? position.symbol,
			accountName: owner?.name ?? trades.find((trade) => trade.accountId === position.accountId)?.accountName ?? '',
			priceE4: security?.price ?? averagePriceE4(position.costBasis, position.units),
			changeBp: security?.changeBp ?? null,
			asOf: security?.asOf ?? null,
		});
	});
}
