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

/** Net change to your accounts' balances for a day. A transfer moves money between your own accounts, so it doesn't count. */
export function dailyAccrued(rows: TransactionRow[]): number {
	return rows.reduce((sum, row) => (row.kind === 'transaction' ? sum + row.transaction.amount : sum), 0);
}

/** What a transaction card shows, the same for a plain transaction and a merged transfer. Mirrors the Android row. */
export interface RowCard {
	key: string;
	title: string;
	/** The account, or "From → To" for a transfer. */
	subtitle: string;
	/** Posted by a subscription rather than entered by a person. */
	automated: boolean;
	occurredOn: string;
	amount: number;
	tone: 'signed' | 'transfer';
	balance: number;
	balanceAccountId: string;
	category: { name: string; color: string | null } | null;
	notes: string;
}

export function describeRow(row: TransactionRow): RowCard {
	if (row.kind === 'transfer') {
		const flow = `${row.fromAccountName ?? ''} → ${row.toAccountName ?? ''}`;
		const payee = row.payee.trim();

		return {
			key: row.id,
			// An unnamed transfer is stored under its own "From → To", which the subtitle already says.
			title: !payee || payee === flow ? 'Transfer' : payee,
			subtitle: flow,
			automated: false,
			occurredOn: row.leg.occurredOn,
			amount: row.amount,
			tone: 'transfer',
			balance: row.leg.runningBalance,
			balanceAccountId: row.leg.accountId,
			category: row.categoryName ? { name: row.categoryName, color: row.categoryColor } : null,
			notes: row.notes.trim(),
		};
	}

	const { transaction } = row;

	return {
		key: transaction.id,
		title: transaction.payee || transaction.categoryName || 'Uncategorized',
		subtitle: transaction.accountName ?? '',
		automated: transaction.automated,
		occurredOn: transaction.occurredOn,
		amount: transaction.amount,
		tone: 'signed',
		balance: transaction.runningBalance,
		balanceAccountId: transaction.accountId,
		category: transaction.categoryName ? { name: transaction.categoryName, color: transaction.categoryColor } : null,
		notes: transaction.notes.trim(),
	};
}
