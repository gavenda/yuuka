/**
 * The answer a write gets before the server has given one.
 *
 * Writing locally first means every screen has to be told something
 * immediately, in the shape the API would have returned, so nothing downstream
 * has to know whether an answer came from here or from the server. These
 * builders are that shape: the row the user typed, plus the fields the API
 * would have filled in — the account's name, the category's colour, a balance
 * that has moved — worked out from the ledger already in the browser.
 *
 * Every figure here is a good guess, never the truth. Once the queue drains,
 * the slices the change touched are refetched and whatever these worked out is
 * replaced by what the server has. What they buy is the moment in between, and
 * the rules that keep that moment honest are:
 *
 * - **Guess the same way the server does.** A round-up computed differently
 *   here than in `routes/transactions.ts` would show one figure and save
 *   another. The arithmetic is deliberately the same, and `roundUpFor` is the
 *   web twin of `computeRoundUp`.
 * - **Never guess what only the server knows.** A running balance depends on
 *   every row in the account in date order, and a summary on the whole month.
 *   Those are left at their best local value and corrected on refresh rather
 *   than invented.
 */

import type { Account, AccountType, Category, RoundUpRule, Settings, Subscription, Tag, Transaction, TransactionTag } from '@/types';

const NOW = () => new Date().toISOString();

/** Fields every row carries, for a row that has only just come into existence. */
function stamps() {
	const now = NOW();
	return { createdAt: now, updatedAt: now };
}

/**
 * What the builders need to know about the ledger.
 *
 * It is handed in rather than imported, because the stores that hold it all
 * import the API, and the API is what asks for a provisional row — importing
 * back would be a cycle. `main.ts` registers the real one once Pinia is up.
 */
export interface LedgerView {
	accounts: Account[];
	accountTypes: AccountType[];
	categories: Category[];
	tags: Tag[];
	roundUpRule: RoundUpRule | null;
	settings: Settings | null;
	displayCurrency: string;
	/** A transaction already on screen, for an edit that has to show before it is sent. */
	transaction: (id: string) => Transaction | null;
	/** A subscription already on screen, for the same reason. */
	subscription: (id: string) => Subscription | null;
}

/** What is known before anything has loaded, and in a spec that does not care. */
const EMPTY: LedgerView = {
	accounts: [],
	accountTypes: [],
	categories: [],
	tags: [],
	roundUpRule: null,
	settings: null,
	displayCurrency: 'PHP',
	transaction: () => null,
	subscription: () => null,
};

let provider: (() => LedgerView) | null = null;

export function setLedgerView(view: () => LedgerView): void {
	provider = view;
}

/** The ledger as it stands, or an empty one if nothing has registered yet. */
export function ledgerView(): LedgerView {
	try {
		return provider?.() ?? EMPTY;
	} catch {
		// A view asked for before Pinia is active, which is a load-order accident
		// rather than something a write should fail on.
		return EMPTY;
	}
}

const accountOf = (ledger: LedgerView, id: string) => ledger.accounts.find((account) => account.id === id) ?? null;
const categoryOf = (ledger: LedgerView, id: string | null) =>
	id === null ? null : (ledger.categories.find((category) => category.id === id) ?? null);

/** The chips a transaction wears, in the name order the API returns them in. */
function chipsFor(ledger: LedgerView, tagIds: string[] | undefined): TransactionTag[] {
	if (!tagIds?.length) return [];

	return ledger.tags
		.filter((tag) => tagIds.includes(tag.id))
		.map((tag) => ({ id: tag.id, name: tag.name, color: tag.color }))
		.sort((left, right) => left.name.localeCompare(right.name));
}

export interface TransactionDraft {
	id: string;
	accountId: string;
	categoryId: string | null;
	amount: number;
	occurredOn: string;
	payee: string;
	notes: string;
	tagIds?: string[];
	transferId?: string | null;
}

/**
 * A transaction as it will look once it is saved.
 *
 * `runningBalance` is the account's balance with this row included, which is
 * right for the newest transaction on the account and approximate for one
 * back-dated into the middle of its history. The list is refetched when the
 * queue drains, so the approximation lasts only until then.
 */
export function provisionalTransaction(ledger: LedgerView, draft: TransactionDraft): Transaction {
	const account = accountOf(ledger, draft.accountId);
	const category = categoryOf(ledger, draft.categoryId);

	return {
		id: draft.id,
		accountId: draft.accountId,
		accountName: account?.name ?? null,
		categoryId: draft.categoryId,
		categoryName: category?.name ?? null,
		categoryColor: category?.color ?? null,
		amount: draft.amount,
		occurredOn: draft.occurredOn,
		payee: draft.payee,
		notes: draft.notes,
		transferId: draft.transferId ?? null,
		automated: false,
		tags: chipsFor(ledger, draft.tagIds),
		runningBalance: (account?.balance ?? 0) + draft.amount,
		...stamps(),
	};
}

/**
 * What "Save the Change" would move, given an expense.
 *
 * The web twin of `computeRoundUp` in `server/routes/transactions.ts`: the gap
 * between the amount spent and the next multiple up. An amount already on a
 * multiple rounds up by nothing, which is why zero means "no round-up" rather
 * than "a round-up of zero".
 */
export function roundUpFor(amount: number, roundTo: number): number {
	if (amount >= 0 || roundTo <= 0) return 0;

	const spent = Math.abs(amount);
	const remainder = spent % roundTo;
	return remainder === 0 ? 0 : roundTo - remainder;
}

/** The three rows a round-up posts, or null when this purchase does not trigger one. */
export function provisionalRoundUp(
	ledger: LedgerView,
	draft: TransactionDraft,
	ids: { transferId: string; fromId: string; toId: string },
): { transferId: string; source: Transaction; destination: Transaction } | null {
	const rule = ledger.roundUpRule;
	if (!rule?.enabled || !rule.destinationAccountId) return null;

	const source = accountOf(ledger, draft.accountId);
	// An account that has not opted in, or the destination buying something
	// itself, simply does not round up — the same two conditions the API checks.
	if (!source?.roundUpSource || draft.accountId === rule.destinationAccountId) return null;

	const amount = roundUpFor(draft.amount, rule.roundTo);
	if (amount <= 0) return null;

	const shared = {
		categoryId: rule.categoryId,
		occurredOn: draft.occurredOn,
		// Synthetic, and never taught to the payee history — the same way a
		// transfer's composed "From → To" name is not.
		payee: 'Save the Change',
		notes: '',
		transferId: ids.transferId,
	};

	return {
		transferId: ids.transferId,
		source: provisionalTransaction(ledger, { ...shared, id: ids.fromId, accountId: draft.accountId, amount: -amount }),
		destination: provisionalTransaction(ledger, { ...shared, id: ids.toId, accountId: rule.destinationAccountId, amount }),
	};
}

/** The two legs of a transfer. Both wear the same tags, so it reads the same from either account. */
export function provisionalTransfer(
	ledger: LedgerView,
	input: {
		ids: { transferId: string; fromId: string; toId: string };
		fromAccountId: string;
		toAccountId: string;
		categoryId: string | null;
		amount: number;
		occurredOn: string;
		payee: string;
		notes: string;
		tagIds?: string[];
	},
): Transaction[] {
	// Blank names itself, exactly as the API composes it.
	const from = accountOf(ledger, input.fromAccountId);
	const to = accountOf(ledger, input.toAccountId);
	const payee = input.payee || `${from?.name ?? '—'} → ${to?.name ?? '—'}`;

	const shared = {
		categoryId: input.categoryId,
		occurredOn: input.occurredOn,
		payee,
		notes: input.notes,
		tagIds: input.tagIds,
		transferId: input.ids.transferId,
	};

	return [
		provisionalTransaction(ledger, { ...shared, id: input.ids.fromId, accountId: input.fromAccountId, amount: -input.amount }),
		provisionalTransaction(ledger, { ...shared, id: input.ids.toId, accountId: input.toAccountId, amount: input.amount }),
	];
}

/**
 * The transaction a balance adjustment posts.
 *
 * The API is told the balance the account should read and works the difference
 * out itself, against the balance at the instant it writes. Here the only
 * balance available is the one on screen, so the amount is computed against
 * that — and if a transaction lands in between, the server's figure is the one
 * that survives the refresh.
 */
export function provisionalAdjustment(
	ledger: LedgerView,
	accountId: string,
	input: { id: string; balance: number; occurredOn: string; payee: string; notes: string },
): Transaction {
	const account = accountOf(ledger, accountId);
	const difference = input.balance - (account?.balance ?? 0);

	return provisionalTransaction(ledger, {
		id: input.id,
		accountId,
		categoryId: null,
		amount: difference,
		occurredOn: input.occurredOn,
		payee: input.payee || 'Balance adjustment',
		notes: input.notes,
	});
}

export function provisionalAccount(ledger: LedgerView, input: Partial<Account> & { id: string; name: string; typeId: string }): Account {
	const type = ledger.accountTypes.find((entry) => entry.id === input.typeId) ?? null;

	return {
		id: input.id,
		name: input.name,
		typeId: input.typeId,
		typeName: type?.name ?? null,
		currency: input.currency ?? ledger.displayCurrency,
		logoUrl: input.logoUrl ?? null,
		logoInvertDark: input.logoInvertDark ?? false,
		roundUpSource: input.roundUpSource ?? false,
		startingBalance: input.startingBalance ?? 0,
		// Nothing has posted to it yet, so its balance is what it opened with.
		balance: input.startingBalance ?? 0,
		archived: false,
		...stamps(),
	};
}

export function provisionalAccountType(input: { id: string; name: string; sortOrder?: number }): AccountType {
	return {
		id: input.id,
		name: input.name,
		sortOrder: input.sortOrder ?? 0,
		archived: false,
		accountCount: 0,
		...stamps(),
	};
}

/**
 * A category as it will be stored. A child inherits its parent's kind, which is
 * a quiet correction on the server rather than an error, so it is applied here
 * too — otherwise a subcategory would show one kind and save another.
 */
export function provisionalCategory(
	ledger: LedgerView,
	input: { id: string; name: string; kind: Category['kind']; color?: string; sortOrder?: number; parentId?: string | null },
): Category {
	const parent = categoryOf(ledger, input.parentId ?? null);

	return {
		id: input.id,
		name: input.name,
		kind: parent?.kind ?? input.kind,
		color: input.color ?? '#64748b',
		sortOrder: input.sortOrder ?? 0,
		archived: false,
		parentId: input.parentId ?? null,
		...stamps(),
	};
}

export function provisionalTag(input: { id: string; name: string; color?: string }): Tag {
	return {
		id: input.id,
		name: input.name,
		color: input.color ?? '#64748b',
		transactionCount: 0,
		...stamps(),
	};
}

/**
 * A subscription as it will be scheduled.
 *
 * The start date's day of the month is the anchor for every later run, and the
 * first run is the start date itself — the API refuses a date already past, so
 * there is never a backlog to work out here.
 */
export function provisionalSubscription(
	ledger: LedgerView,
	input: { id: string; accountId: string; categoryId: string | null; amount: number; payee: string; notes: string; startOn: string },
): Subscription {
	const account = accountOf(ledger, input.accountId);
	const category = categoryOf(ledger, input.categoryId);

	return {
		id: input.id,
		accountId: input.accountId,
		accountName: account?.name ?? null,
		categoryId: input.categoryId,
		categoryName: category?.name ?? null,
		categoryColor: category?.color ?? null,
		amount: input.amount,
		payee: input.payee,
		notes: input.notes,
		startOn: input.startOn,
		dayOfMonth: Number(input.startOn.slice(8, 10)),
		nextRunOn: input.startOn,
		lastRunOn: null,
		enabled: true,
		...stamps(),
	};
}

/** Applies a patch to a row that is already on screen, so an edit shows at once. */
export function patched<T extends object>(current: T, changes: Partial<T>): T {
	return { ...current, ...changes, updatedAt: NOW() } as T;
}
