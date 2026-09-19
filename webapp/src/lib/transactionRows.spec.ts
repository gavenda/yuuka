import { describe, expect, it } from 'vitest';
import { dailyAccrued, describeRow, mergeTransferRows } from './transactionRows';
import type { Transaction } from '@/types';

function tx(id: string, amount: number, transferId: string | null = null): Transaction {
	return {
		id,
		amount,
		transferId,
		accountId: `acc-${id}`,
		accountName: id,
		payee: '',
		notes: '',
		categoryName: null,
		categoryColor: null,
	} as unknown as Transaction;
}

describe('dailyAccrued', () => {
	it('sums ordinary transactions by sign', () => {
		expect(dailyAccrued(mergeTransferRows([tx('a', -4599), tx('b', 10000), tx('c', -401)]))).toBe(5000);
	});

	it('leaves transfers out, since they only move money between your own accounts', () => {
		const rows = mergeTransferRows([tx('a', -2500), tx('out', -10000, 't1'), tx('in', 10000, 't1')]);
		expect(dailyAccrued(rows)).toBe(-2500);
	});

	it('is zero for a day of only transfers', () => {
		expect(dailyAccrued(mergeTransferRows([tx('out', -10000, 't1'), tx('in', 10000, 't1')]))).toBe(0);
	});
});

describe('describeRow', () => {
	it('titles a plain transaction by payee, then category, then "Uncategorized"', () => {
		const [named] = mergeTransferRows([{ ...tx('a', -100), payee: 'Jollibee', categoryName: 'Food' } as Transaction]);
		const [categorised] = mergeTransferRows([{ ...tx('b', -100), categoryName: 'Food' } as Transaction]);
		const [bare] = mergeTransferRows([tx('c', -100)]);

		expect(describeRow(named).title).toBe('Jollibee');
		expect(describeRow(categorised).title).toBe('Food');
		expect(describeRow(bare).title).toBe('Uncategorized');
	});

	it('shows the account under a plain transaction and marks it signed', () => {
		const card = describeRow(mergeTransferRows([{ ...tx('a', -100), accountName: 'BPI' } as Transaction])[0]);

		expect(card.subtitle).toBe('BPI');
		expect(card.tone).toBe('signed');
	});

	it("shows a transfer as From → To with the amount unsigned, and keeps the outflow leg's balance", () => {
		const rows = mergeTransferRows([
			{ ...tx('out', -10000, 't1'), accountName: 'Checking', runningBalance: 5000 } as unknown as Transaction,
			{ ...tx('in', 10000, 't1'), accountName: 'Savings' } as Transaction,
		]);
		const card = describeRow(rows[0]);

		expect(card.subtitle).toBe('Checking → Savings');
		expect(card.amount).toBe(10000);
		expect(card.tone).toBe('transfer');
		expect(card.balance).toBe(5000);
	});

	it('calls an unnamed transfer "Transfer" rather than repeating its own From → To', () => {
		const unnamed = mergeTransferRows([
			{ ...tx('out', -100, 't1'), accountName: 'Checking', payee: 'Checking → Savings' } as Transaction,
			{ ...tx('in', 100, 't1'), accountName: 'Savings' } as Transaction,
		]);
		const named = mergeTransferRows([
			{ ...tx('out', -100, 't2'), accountName: 'Checking', payee: 'Rent pool' } as Transaction,
			{ ...tx('in', 100, 't2'), accountName: 'Savings' } as Transaction,
		]);

		expect(describeRow(unnamed[0]).title).toBe('Transfer');
		expect(describeRow(named[0]).title).toBe('Rent pool');
	});

	it('leaves a blank note out, so the card has no notes row for it', () => {
		const [row] = mergeTransferRows([{ ...tx('a', -100), notes: '   ' } as Transaction]);

		expect(describeRow(row).notes).toBe('');
	});
});
