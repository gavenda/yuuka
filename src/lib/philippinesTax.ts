/**
 * Philippines take-home pay estimate: the mandatory employee contributions
 * (SSS, PhilHealth, Pag-IBIG) and BIR withholding tax under the TRAIN law,
 * derived from a monthly gross salary. Everything here is a pure client-side
 * calculation in minor units (centavos) — nothing is persisted or sent to the
 * API, it only produces a number the caller may choose to save as a planned
 * income amount.
 */

/** Converts a whole-peso amount into centavos, for the tables below. */
function peso(amount: number): number {
	return amount * 100;
}

export interface ContributionLine {
	label: string;
	amount: number;
}

export interface NetPayBreakdown {
	gross: number;
	contributions: ContributionLine[];
	totalContributions: number;
	taxableIncome: number;
	incomeTax: number;
	netPay: number;
}

/**
 * SSS employee share: 5% of the Monthly Salary Credit, which brackets actual
 * pay into ₱500 steps between a ₱5,000 floor and a ₱35,000 ceiling.
 */
function sssContribution(gross: number): number {
	const MIN_MSC = peso(5_000);
	const MAX_MSC = peso(35_000);
	const STEP = peso(500);
	const msc = Math.min(Math.max(Math.ceil(gross / STEP) * STEP, MIN_MSC), MAX_MSC);
	return Math.round(msc * 0.05);
}

/**
 * PhilHealth employee share: 2.5% of monthly basic pay (half of the 5% total
 * premium), floored at a ₱10,000 salary and capped at a ₱100,000 one.
 */
function philHealthContribution(gross: number): number {
	const FLOOR = peso(10_000);
	const CEILING = peso(100_000);
	const base = Math.min(Math.max(gross, FLOOR), CEILING);
	return Math.round(base * 0.025);
}

/**
 * Pag-IBIG employee share: 1% of pay up to ₱1,500, 2% above it, fixed at
 * ₱200 once pay passes the ₱10,000 Monthly Fund Salary cap.
 */
function pagIbigContribution(gross: number): number {
	const THRESHOLD = peso(1_500);
	const CAP = peso(10_000);
	if (gross > CAP) return peso(200);
	return Math.round(gross * (gross <= THRESHOLD ? 0.01 : 0.02));
}

interface TaxBracket {
	over: number;
	base: number;
	rate: number;
}

/**
 * BIR monthly withholding tax table under the TRAIN law, effective 2023.
 * Checked highest bracket first; `base` already carries the running total
 * from every bracket below it, so only the excess over its own floor needs
 * the marginal rate applied.
 */
const TAX_BRACKETS: TaxBracket[] = [
	{ over: peso(666_667), base: peso(200_833) + 33, rate: 0.35 },
	{ over: peso(166_667), base: peso(40_833) + 33, rate: 0.3 },
	{ over: peso(66_667), base: peso(10_833) + 33, rate: 0.25 },
	{ over: peso(33_333), base: peso(2_500), rate: 0.2 },
	{ over: peso(20_833), base: 0, rate: 0.15 },
	{ over: 0, base: 0, rate: 0 },
];

function withholdingTax(taxableIncome: number): number {
	const bracket = TAX_BRACKETS.find((candidate) => taxableIncome > candidate.over)!;
	return bracket.base + Math.round((taxableIncome - bracket.over) * bracket.rate);
}

const ZERO_BREAKDOWN: Omit<NetPayBreakdown, 'contributions'> = {
	gross: 0,
	totalContributions: 0,
	taxableIncome: 0,
	incomeTax: 0,
	netPay: 0,
};

/**
 * Computes take-home pay from a monthly gross salary, both in centavos.
 * Contributions are deducted from gross to reach taxable income, and
 * withholding tax is deducted from that to reach net pay.
 */
export function computeNetPay(grossMinor: number): NetPayBreakdown {
	const gross = Math.max(0, Math.round(grossMinor));
	const contributions: ContributionLine[] = [
		{ label: 'SSS', amount: gross > 0 ? sssContribution(gross) : 0 },
		{ label: 'PhilHealth', amount: gross > 0 ? philHealthContribution(gross) : 0 },
		{ label: 'Pag-IBIG', amount: gross > 0 ? pagIbigContribution(gross) : 0 },
	];

	if (gross === 0) return { ...ZERO_BREAKDOWN, contributions };

	const totalContributions = contributions.reduce((sum, line) => sum + line.amount, 0);
	const taxableIncome = Math.max(0, gross - totalContributions);
	const incomeTax = withholdingTax(taxableIncome);
	const netPay = gross - totalContributions - incomeTax;

	return { gross, contributions, totalContributions, taxableIncome, incomeTax, netPay };
}
