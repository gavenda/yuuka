import { newId } from './ids';
import { NOW_SQL } from './sql';

export type PayeeKind = 'expense' | 'income' | 'transfer';

/**
 * The synthetic payee "Save the Change" round-ups are posted under. It is
 * written by the API rather than typed, so it is never remembered — offering it
 * back as a suggestion would fill a form with a name the user cannot have meant.
 */
export const ROUND_UP_PAYEE = 'Save the Change';

export interface PayeeMemory {
	payee: string;
	kind: PayeeKind;
	accountId: string | null;
	toAccountId?: string | null;
	categoryId: string | null;
	notes: string;
}

/**
 * Records what a payee was filed under, so typing it again can fill in the
 * rest. Only a named transaction is worth remembering — a blank payee has
 * nothing to look up later, and a name the API composed was not typed at all.
 *
 * The newest use wins: the stored details are overwritten rather than merged,
 * because the question being answered is "what did I do last time", and a
 * half-updated row would answer it wrongly.
 */
export async function rememberPayee(db: D1Database, userId: string, memory: PayeeMemory): Promise<void> {
	const payee = memory.payee.trim();
	if (!payee || payee === ROUND_UP_PAYEE) return;

	await db
		.prepare(
			`INSERT INTO payee_history (id, user_id, payee, kind, account_id, to_account_id, category_id, notes)
			 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
			 ON CONFLICT (user_id, payee COLLATE NOCASE)
			 DO UPDATE SET
			   payee = excluded.payee,
			   kind = excluded.kind,
			   account_id = excluded.account_id,
			   to_account_id = excluded.to_account_id,
			   category_id = excluded.category_id,
			   notes = excluded.notes,
			   used_count = used_count + 1,
			   last_used_at = ${NOW_SQL},
			   updated_at = ${NOW_SQL}`,
		)
		.bind(newId('pye'), userId, payee, memory.kind, memory.accountId, memory.toAccountId ?? null, memory.categoryId, memory.notes)
		.run();
}

/** The kind of transaction a row represents, for the history. */
export function payeeKind(amount: number, transferId: string | null): PayeeKind {
	if (transferId) return 'transfer';
	return amount >= 0 ? 'income' : 'expense';
}
