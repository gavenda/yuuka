import type { Transaction } from '@/types';

/** A transfer is two linked rows; every list shows them as the single movement they represent. */
export interface TransferRow {
	kind: 'transfer';
	id: string;
	payee: string;
	fromAccountName: string | null;
	toAccountName: string | null;
	toAccountId: string;
	categoryName: string | null;
	categoryColor: string | null;
	notes: string;
	amount: number;
	leg: Transaction;
}

export type TransactionRow = { kind: 'transaction'; transaction: Transaction } | TransferRow;

/** Pairs up transfer legs within a set of transactions, leaving ordinary transactions untouched. */
export function mergeTransferRows(group: Transaction[]): TransactionRow[] {
	const rows: TransactionRow[] = [];
	const paired = new Set<string>();

	for (const transaction of group) {
		if (paired.has(transaction.id)) continue;

		if (transaction.transferId) {
			const other = group.find((candidate) => candidate.transferId === transaction.transferId && candidate.id !== transaction.id);
			if (other) {
				paired.add(transaction.id);
				paired.add(other.id);
				const outflow = transaction.amount < 0 ? transaction : other;
				const inflow = outflow === transaction ? other : transaction;
				rows.push({
					kind: 'transfer',
					id: transaction.transferId,
					payee: transaction.payee,
					fromAccountName: outflow.accountName,
					toAccountName: inflow.accountName,
					toAccountId: inflow.accountId,
					categoryName: transaction.categoryName,
					categoryColor: transaction.categoryColor,
					notes: transaction.notes,
					amount: Math.abs(transaction.amount),
					leg: outflow,
				});
				continue;
			}
		}

		rows.push({ kind: 'transaction', transaction });
	}

	return rows;
}
