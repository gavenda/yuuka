import { describe, expect, it } from 'vitest';
import { computeNetPay } from './philippinesTax';

describe('computeNetPay', () => {
	it('is all zero for no gross pay', () => {
		const breakdown = computeNetPay(0);
		expect(breakdown.totalContributions).toBe(0);
		expect(breakdown.incomeTax).toBe(0);
		expect(breakdown.netPay).toBe(0);
		expect(breakdown.contributions.every((line) => line.amount === 0)).toBe(true);
	});

	it('clamps a negative gross to zero', () => {
		expect(computeNetPay(-500_00).netPay).toBe(0);
	});

	it('matches a hand-computed ₱25,000 monthly salary', () => {
		const breakdown = computeNetPay(25_000_00);

		expect(breakdown.contributions).toEqual([
			{ label: 'SSS', amount: 1_250_00 },
			{ label: 'PhilHealth', amount: 625_00 },
			{ label: 'Pag-IBIG', amount: 200_00 },
		]);
		expect(breakdown.totalContributions).toBe(2_075_00);
		expect(breakdown.taxableIncome).toBe(22_925_00);
		expect(breakdown.incomeTax).toBe(313_80);
		expect(breakdown.netPay).toBe(22_611_20);
	});

	it('owes no tax at or below the ₱20,833 exempt threshold', () => {
		const breakdown = computeNetPay(15_000_00);
		expect(breakdown.incomeTax).toBe(0);
	});

	it('owes no tax when contributions consume a small gross entirely', () => {
		// Below the SSS and PhilHealth floors, so contributions alone exceed
		// gross and taxable income clamps to exactly zero — the lowest tax
		// bracket (over: 0) must still match this, not throw.
		const breakdown = computeNetPay(1_00);
		expect(breakdown.taxableIncome).toBe(0);
		expect(breakdown.incomeTax).toBe(0);
	});

	it('floors SSS and PhilHealth at their minimum salary credit for low pay', () => {
		const breakdown = computeNetPay(3_000_00);
		expect(breakdown.contributions[0]).toEqual({ label: 'SSS', amount: 250_00 });
		expect(breakdown.contributions[1]).toEqual({ label: 'PhilHealth', amount: 250_00 });
		expect(breakdown.contributions[2]).toEqual({ label: 'Pag-IBIG', amount: 60_00 });
	});

	it('caps SSS and PhilHealth at their maximum salary credit for high pay', () => {
		const breakdown = computeNetPay(200_000_00);
		expect(breakdown.contributions[0]).toEqual({ label: 'SSS', amount: 1_750_00 });
		expect(breakdown.contributions[1]).toEqual({ label: 'PhilHealth', amount: 2_500_00 });
		expect(breakdown.contributions[2]).toEqual({ label: 'Pag-IBIG', amount: 200_00 });
	});

	it('applies the top tax bracket past ₱666,667 of taxable income', () => {
		const breakdown = computeNetPay(700_000_00);
		expect(breakdown.totalContributions).toBe(4_450_00);
		expect(breakdown.taxableIncome).toBe(695_550_00);
		expect(breakdown.incomeTax).toBe(210_942_38);
		expect(breakdown.netPay).toBe(484_607_62);
	});
});
