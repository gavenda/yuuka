import { describe, expect, it } from 'vitest';
import { dailyAccrued, mergeTransferRows } from './transactionRows';
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
