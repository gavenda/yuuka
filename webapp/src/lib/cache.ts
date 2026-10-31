/**
 * The local copy of the ledger, row by row: the DAOs over `cacheDb.ts`, as `LedgerDao`,
 * `TransactionDao` and the rest are over Room.
 *
 * It plays the part Room plays in the Android app: screens paint from it at once, and the API is
 * still the source of truth. Two shapes of write live here, as they do there:
 *
 * - **A replace is a sync.** The API answered, and what it said is the whole truth for that slice:
 *   the rows it returned go in, and the rows it no longer returns within the slice go out.
 * - **A save is one row.** The row the user just made — the queue's provisional answer, or the
 *   API's own — is written before the server has seen it, as Room's `upsert` and `deleteById` are,
 *   with the same cascades: a deleted transfer takes both legs, a deleted category leaves its
 *   transactions uncategorised, a deleted tag only comes off.
 *
 * Reads hand back fresh objects, in the order the API sorts each list in, so nothing a screen does
 * to what it was given can reach the copy.
 */

import type {
	Account,
	AccountType,
	Budget,
	Category,
	IncomePlan,
	Payee,
	RoundUpRule,
	Settings,
	Subscription,
	Summary,
	Tag,
	Transaction,
	TransactionFilters,
	Trend,
} from '@/types';
import { read, write, type DatabaseView, type Op, type StoredRow, type TableName } from './cacheDb';

export { adoptCacheFor, clearCache, warmCache } from './cacheDb';

/** The one settings row and the one round-up rule, pinned at id 0 as Room pins them. */
const SINGLE_ROW = 0;

const put = (table: TableName, row: object): Op => ({ op: 'put', table, row: { ...row } });
const remove = (table: TableName, key: IDBValidKey): Op => ({ op: 'delete', table, key });
const replace = (table: TableName, rows: readonly object[]): Op[] => [{ op: 'clear', table }, ...rows.map((row) => put(table, row))];

/** A stored row as the entity it was, without what the copy keeps beside it. */
function entity<T>(row: StoredRow, ...internal: string[]): T {
	const copy: Record<string, unknown> = { ...row };
	for (const field of ['owner', ...internal]) delete copy[field];
	return copy as T;
}

/** `COLLATE NOCASE`, as the API's `ORDER BY`s use it. */
function nocase(a: string, b: string): number {
	const [x, y] = [a.toLowerCase(), b.toLowerCase()];
	return x < y ? -1 : x > y ? 1 : 0;
}

const compare = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);

// The API's own orders, so a list painted from the copy reads as the one it answered with.
const byAccount = (a: Account, b: Account) => Number(a.archived) - Number(b.archived) || nocase(a.name, b.name);
const byAccountType = (a: AccountType, b: AccountType) =>
	Number(a.archived) - Number(b.archived) || a.sortOrder - b.sortOrder || nocase(a.name, b.name);
const byTag = (a: Tag, b: Tag) => nocase(a.name, b.name);
const bySubscription = (a: Subscription, b: Subscription) =>
	Number(b.enabled) - Number(a.enabled) || compare(a.nextRunOn, b.nextRunOn) || nocase(a.payee, b.payee);
const byPayee = (a: Payee, b: Payee) => b.usedCount - a.usedCount || compare(b.lastUsedAt, a.lastUsedAt);
/** `routes/transactions.ts`'s `NEWEST_FIRST`: a dated row with no time sorts after the timed ones of its day. */
function newestFirst(a: object, b: object): number {
	const [x, y] = [a as { occurredOn?: unknown; createdAt?: unknown }, b as { occurredOn?: unknown; createdAt?: unknown }];
	return compare(String(y.occurredOn), String(x.occurredOn)) || compare(String(y.createdAt), String(x.createdAt));
}

/** Each family in its parent's place, the parent first and then its children — `routes/categories.ts`'s order. */
function sortCategories(categories: Category[]): Category[] {
	const byId = new Map(categories.map((category) => [category.id, category]));
	const family = (category: Category) => (category.parentId ? byId.get(category.parentId) : undefined) ?? category;

	return categories.sort(
		(a, b) =>
			compare(a.kind, b.kind) ||
			family(a).sortOrder - family(b).sortOrder ||
			nocase(family(a).name, family(b).name) ||
			Number(a.parentId !== null) - Number(b.parentId !== null) ||
			a.sortOrder - b.sortOrder ||
			nocase(a.name, b.name),
	);
}

// ---------------------------------------------------------------------------------------------
// The ledger: accounts, their types, categories, tags, settings and the round-up rule.

export interface LedgerCopy {
	accounts: Account[];
	accountTypes: AccountType[];
	categories: Category[];
	tags: Tag[];
	settings: Settings;
	roundUpRule: RoundUpRule;
}

/** The whole ledger, or null when it has never been saved whole — settings and the rule come with every full load. */
export function readLedger(): Promise<LedgerCopy | null> {
	return read((db) => {
		const settings = db('settings').get(SINGLE_ROW);
		const roundUpRule = db('round_up_rule').get(SINGLE_ROW);
		if (!settings || !roundUpRule) return null;

		return {
			accounts: db('accounts')
				.all()
				.map((row) => entity<Account>(row))
				.sort(byAccount),
			accountTypes: db('account_types')
				.all()
				.map((row) => entity<AccountType>(row))
				.sort(byAccountType),
			categories: sortCategories(
				db('categories')
					.all()
					.map((row) => entity<Category>(row)),
			),
			tags: db('tags')
				.all()
				.map((row) => entity<Tag>(row))
				.sort(byTag),
			settings: entity<Settings>(settings, 'id'),
			roundUpRule: entity<RoundUpRule>(roundUpRule, 'id'),
		};
	});
}

/** Replaces the whole ledger with what a full load of it answered. */
export function replaceLedger(ledger: LedgerCopy): Promise<void> {
	return write([
		...replace('accounts', ledger.accounts),
		...replace('account_types', ledger.accountTypes),
		...replace('categories', ledger.categories),
		...replace('tags', ledger.tags),
		put('settings', { ...ledger.settings, id: SINGLE_ROW }),
		put('round_up_rule', { ...ledger.roundUpRule, id: SINGLE_ROW }),
	]);
}

export const replaceAccounts = (accounts: Account[], accountTypes: AccountType[]) =>
	write([...replace('accounts', accounts), ...replace('account_types', accountTypes)]);
export const replaceCategories = (categories: Category[]) => write(replace('categories', categories));
export const replaceTags = (tags: Tag[]) => write(replace('tags', tags));

export const saveSettings = (settings: Settings) => write([put('settings', { ...settings, id: SINGLE_ROW })]);
export const saveRoundUpRule = (rule: RoundUpRule) => write([put('round_up_rule', { ...rule, id: SINGLE_ROW })]);
export const saveAccountType = (type: AccountType) => write([put('account_types', type)]);
export const saveAccountTypes = (types: AccountType[]) => write(types.map((type) => put('account_types', type)));
export const saveAccount = (account: Account) => write([put('accounts', account)]);
export const saveCategory = (category: Category) => write([put('categories', category)]);
export const saveTag = (tag: Tag) => write([put('tags', tag)]);

export const deleteAccountType = (id: string) => write([remove('account_types', id)]);

/** Only the label comes off: the transactions that wore it stay. */
export const deleteTag = (id: string) =>
	write([remove('tags', id), { op: 'deleteWhere', table: 'transaction_tags', field: 'tagId', value: id }]);

/** Deleting a category never destroys history: its transactions and subscriptions fall back to uncategorised; only its budgets go. */
export function deleteCategory(id: string): Promise<void> {
	const uncategorised = { categoryId: null, categoryName: null, categoryColor: null };
	return write([
		remove('categories', id),
		{ op: 'patchWhere', table: 'transactions', field: 'categoryId', value: id, patch: uncategorised },
		{ op: 'patchWhere', table: 'subscriptions', field: 'categoryId', value: id, patch: uncategorised },
		{ op: 'deleteWhere', table: 'budgets', field: 'categoryId', value: id },
	]);
}

/** An account takes its transactions with it — and, through a transfer, the other leg — and its subscriptions. */
export function deleteAccount(id: string): Promise<void> {
	return write((db) => [
		...transactionRemovals(db, db('transactions').where('accountId', id)),
		{ op: 'deleteWhere', table: 'transactions', field: 'accountId', value: id },
		{ op: 'deleteWhere', table: 'subscriptions', field: 'accountId', value: id },
		remove('accounts', id),
	]);
}

// ---------------------------------------------------------------------------------------------
// Transactions, and the tags they wear.

/** A transaction's row and exactly the tags it now wears; what it wore before is dropped, so a removed tag does not linger. */
function transactionWrites(transaction: Transaction): Op[] {
	const { tags, ...row } = transaction;
	return [
		{ op: 'deleteWhere', table: 'transaction_tags', field: 'transactionId', value: transaction.id },
		// The month a screen filters by, kept beside the date so it can be looked up.
		put('transactions', { ...row, month: transaction.occurredOn.slice(0, 7) }),
		// The chip as it was, should the tag itself not be in the copy.
		...tags.map((tag) => put('transaction_tags', { transactionId: transaction.id, tagId: tag.id, name: tag.name, color: tag.color })),
	];
}

/** Rows and their tag links, and the other leg of any transfer among them. */
function transactionRemovals(db: DatabaseView, rows: StoredRow[]): Op[] {
	const legs = new Map<string, StoredRow>();
	for (const row of rows) {
		legs.set(String(row.id), row);
		if (row.transferId) for (const leg of db('transactions').where('transferId', row.transferId as string)) legs.set(String(leg.id), leg);
	}

	return [...legs.keys()].flatMap((id) => [
		{ op: 'deleteWhere', table: 'transaction_tags', field: 'transactionId', value: id } as Op,
		remove('transactions', id),
	]);
}

function toTransaction(db: DatabaseView, row: StoredRow): Transaction {
	const tags = db('transaction_tags')
		.where('transactionId', row.id as string)
		.map((link) => {
			const tag = db('tags').get(link.tagId as string);
			return { id: link.tagId as string, name: (tag?.name ?? link.name) as string, color: (tag?.color ?? link.color) as string };
		})
		.sort((a, b) => nocase(a.name, b.name));

	return { ...entity<Omit<Transaction, 'tags'>>(row, 'month'), tags };
}

const idList = (value: string | undefined) =>
	(value ?? '')
		.split(',')
		.map((id) => id.trim())
		.filter(Boolean);

/**
 * The rows a filter set matches, newest first, worked out the way `GET /transactions` does: month,
 * date range, accounts, categories (`none` for uncategorised), tags, and a search over the payee,
 * the notes and the names of the tags worn. Candidates come from the most selective lookup there is.
 */
function matching(db: DatabaseView, filters: TransactionFilters): StoredRow[] {
	const accounts = new Set(idList(filters.accountId));
	const categories = idList(filters.categoryId);
	const uncategorised = categories.includes('none');
	const categoryIds = new Set(categories.filter((id) => id !== 'none'));
	const tagIds = new Set(idList(filters.tagId));
	const search = filters.search?.trim().toLowerCase();

	const candidates = filters.month
		? db('transactions').where('month', filters.month)
		: accounts.size
			? [...accounts].flatMap((id) => db('transactions').where('accountId', id))
			: db('transactions').all();

	return candidates
		.filter((row) => {
			const date = (row.occurredOn as string).slice(0, 10);
			if (filters.month && row.month !== filters.month) return false;
			if (filters.from && date < filters.from) return false;
			if (filters.to && date > filters.to) return false;
			if (accounts.size && !accounts.has(row.accountId as string)) return false;
			if (categories.length && !(row.categoryId == null ? uncategorised : categoryIds.has(row.categoryId as string))) return false;

			const links = db('transaction_tags').where('transactionId', row.id as string);
			if (tagIds.size && !links.some((link) => tagIds.has(link.tagId as string))) return false;
			if (!search) return true;

			const tagNames = links.map((link) => String(db('tags').get(link.tagId as string)?.name ?? link.name));
			return [row.payee as string, row.notes as string, ...tagNames].some((text) => text.toLowerCase().includes(search));
		})
		.sort(newestFirst);
}

/** A first page this browser has seen for a filter set: which rows it held, and the API's total. */
interface PageRow {
	key: string;
	ids: string[];
	total: number;
	cachedAt: number;
}

const pageKey = (filters: TransactionFilters) =>
	snapshotKey('transactions', { ...filters, limit: undefined, offset: undefined } as Record<string, string | number | undefined>);

/**
 * The first page of a filter set seen before, from the rows in the copy — at least [minimum] of
 * them, or as many as the API last gave. Null for a filter set never seen: the copy cannot say what
 * the API would have answered for it.
 */
export function readTransactionPage(
	filters: TransactionFilters,
	minimum: number,
): Promise<{ transactions: Transaction[]; total: number } | null> {
	return read((db) => {
		const page = db('transaction_pages').get(pageKey(filters)) as (StoredRow & PageRow) | undefined;
		if (!page) return null;

		const rows = matching(db, filters).slice(0, Math.max(minimum, page.ids.length));
		return { transactions: rows.map((row) => toTransaction(db, row)), total: Math.max(page.total, rows.length) };
	});
}

/**
 * Replaces the slice a first page covers with what the API answered for it, and keeps the newest
 * [keep] filter sets. Every row the filter set matches down to the page's last row, and every row
 * its last answer held, that is not in this answer goes: deleted elsewhere, or no longer matching.
 * A filter set trimmed off takes the rows only it held.
 */
export function writeTransactionPage(filters: TransactionFilters, transactions: Transaction[], total: number, keep: number): Promise<void> {
	const key = pageKey(filters);
	const cachedAt = Date.now();

	return write((db) => {
		const fresh = new Set(transactions.map((transaction) => transaction.id));
		const last = transactions.at(-1);
		const complete = transactions.length >= total;
		const covered = matching(db, filters).filter((row) => complete || (last !== undefined && newestFirst(row, last) <= 0));

		const pages = db('transaction_pages').all() as (StoredRow & PageRow)[];
		const previous = pages.find((page) => page.key === key)?.ids ?? [];
		const others = pages.filter((page) => page.key !== key).sort((a, b) => b.cachedAt - a.cachedAt);
		const kept = others.slice(0, Math.max(0, keep - 1));
		const trimmed = others.slice(kept.length);

		const held = new Set([...fresh, ...kept.flatMap((page) => page.ids)]);
		const gone = new Set([
			...covered.map((row) => String(row.id)).filter((id) => !fresh.has(id)),
			...[...previous, ...trimmed.flatMap((page) => page.ids)].filter((id) => !held.has(id)),
		]);

		return [
			...[...gone].flatMap((id) => [
				{ op: 'deleteWhere', table: 'transaction_tags', field: 'transactionId', value: id } as Op,
				remove('transactions', id),
			]),
			...transactions.flatMap(transactionWrites),
			put('transaction_pages', { key, ids: [...fresh], total, cachedAt } satisfies PageRow),
			...trimmed.map((page) => remove('transaction_pages', page.key)),
		];
	});
}

/** Forgets every filter set but [except]'s, with the rows only they held: a write may have moved them, and only the API knows. */
export function dropTransactionPages(except?: TransactionFilters): Promise<void> {
	const keep = except ? pageKey(except) : undefined;

	return write((db) => {
		const pages = db('transaction_pages').all() as (StoredRow & PageRow)[];
		const held = new Set(pages.find((page) => page.key === keep)?.ids ?? []);
		const dropped = pages.filter((page) => page.key !== keep);
		const gone = new Set(dropped.flatMap((page) => page.ids).filter((id) => !held.has(id)));

		return [
			...[...gone].flatMap((id) => [
				{ op: 'deleteWhere', table: 'transaction_tags', field: 'transactionId', value: id } as Op,
				remove('transactions', id),
			]),
			...dropped.map((page) => remove('transaction_pages', page.key)),
		];
	});
}

/**
 * The accounts [before] and [after] touch, each moved by what changed on it — an account's balance is
 * its starting balance plus its rows — so a balance and net worth read right before the server has
 * answered. Its balances replace these once the change has landed.
 */
function balanceShifts(db: DatabaseView, before: StoredRow[], after: Transaction[]): Op[] {
	const shifts = new Map<string, number>();
	for (const row of before) shifts.set(String(row.accountId), (shifts.get(String(row.accountId)) ?? 0) - Number(row.amount));
	for (const row of after) shifts.set(row.accountId, (shifts.get(row.accountId) ?? 0) + row.amount);

	return [...shifts].flatMap(([accountId, shift]) => {
		const account = db('accounts').get(accountId);
		if (!account || shift === 0) return [];
		const current = entity<Account>(account);
		return [put('accounts', { ...current, balance: current.balance + shift })];
	});
}

/** Saves rows the user just made or changed, with the tags each now wears, and moves the balances they touch. */
export const saveTransactions = (transactions: Transaction[]) =>
	write((db) => {
		const before = transactions.map((transaction) => db('transactions').get(transaction.id)).filter((row) => row !== undefined);
		return [...balanceShifts(db, before, transactions), ...transactions.flatMap(transactionWrites)];
	});

/** Deletes a transaction — both legs, when it is one side of a transfer — and moves the balances back. */
export function deleteTransaction(id: string): Promise<void> {
	return write((db) => {
		const row = db('transactions').get(id);
		if (!row) return [remove('transactions', id)];
		const legs = row.transferId ? db('transactions').where('transferId', row.transferId as string) : [row];
		return [...balanceShifts(db, legs, []), ...transactionRemovals(db, [row])];
	});
}

// ---------------------------------------------------------------------------------------------
// Summaries, budgets and income plans: what is held per month.

/** A month's summary — the aggregate as the API worked it out, kept whole for its month. */
export function readSummary(month: string): Promise<Summary | null> {
	return read((db) => {
		const row = db('summaries').get(month);
		return row ? structuredClone(row.summary as Summary) : null;
	});
}

/** Saves a month's summary, keeping the newest [keep] months. */
export function writeSummary(summary: Summary, keep: number): Promise<void> {
	return write([
		put('summaries', { month: summary.month, summary: plain(summary), cachedAt: Date.now() }),
		{ op: 'trim', table: 'summaries', field: 'cachedAt', keep },
	]);
}

/** Forgets every month's summary but [except]'s. */
/**
 * The dashboard's trend up to `month`. It lives in the summaries store under a key no month can have, since it
 * is a derived aggregate like them and goes whenever they go — so it is refetched rather than left on stale figures.
 */
export function readTrend(month: string): Promise<Trend | null> {
	return read((db) => {
		const row = db('summaries').get(`trend:${month}`);
		return row ? structuredClone(row.summary as Trend) : null;
	});
}

export function writeTrend(trend: Trend, keep: number): Promise<void> {
	return write([
		put('summaries', { month: `trend:${trend.month}`, summary: plain(trend), cachedAt: Date.now() }),
		{ op: 'trim', table: 'summaries', field: 'cachedAt', keep },
	]);
}

export const dropSummaries = (except?: string) => write([{ op: 'clear', table: 'summaries', except: except ? [except] : [] }]);

/** A category carries one plan per month asked for, so setting it again replaces whatever was there. */
export function saveBudget(budget: Budget, queriedMonth: string): Promise<void> {
	return write((db) => [
		...db('budgets')
			.where('queriedMonth', queriedMonth)
			.filter((row) => row.categoryId === budget.categoryId)
			.map((row) => remove('budgets', row.id as string)),
		put('budgets', { ...budget, queriedMonth }),
	]);
}

export const deleteBudget = (id: string) => write([remove('budgets', id)]);
export const saveIncomePlan = (plan: IncomePlan, queriedMonth: string) => write([put('income_plans', { ...plan, queriedMonth })]);

/** What is held per month and per page, for a sync to throw away once the whole lists have been replaced. */
export type MonthlySlice = 'transactions' | 'budgets' | 'incomePlans' | 'summaries';

const MONTHLY_TABLES: Record<MonthlySlice, TableName[]> = {
	transactions: ['transactions', 'transaction_tags', 'transaction_pages'],
	budgets: ['budgets'],
	incomePlans: ['income_plans'],
	summaries: ['summaries'],
};

export const discardMonthly = (slices: MonthlySlice[]) =>
	write(slices.flatMap((slice) => MONTHLY_TABLES[slice]).map((table): Op => ({ op: 'clear', table })));

// ---------------------------------------------------------------------------------------------
// Subscriptions and payees: whole lists.

export function readSubscriptions(): Promise<Subscription[] | null> {
	return read((db) => {
		const rows = db('subscriptions').all();
		return rows.length ? rows.map((row) => entity<Subscription>(row)).sort(bySubscription) : null;
	});
}

export const replaceSubscriptions = (subscriptions: Subscription[]) => write(replace('subscriptions', subscriptions));
export const saveSubscription = (subscription: Subscription) => write([put('subscriptions', subscription)]);
export const deleteSubscription = (id: string) => write([remove('subscriptions', id)]);

export function readPayees(): Promise<Payee[] | null> {
	return read((db) => {
		const rows = db('payees').all();
		return rows.length ? rows.map((row) => entity<Payee>(row)).sort(byPayee) : null;
	});
}

export const replacePayees = (payees: Payee[]) => write(replace('payees', payees));
export const deletePayee = (id: string) => write([remove('payees', id)]);

// ---------------------------------------------------------------------------------------------

/** A value IndexedDB can clone: a reactive proxy is not one, so it goes through JSON instead. */
function plain<T>(value: T): T {
	try {
		return structuredClone(value);
	} catch {
		return JSON.parse(JSON.stringify(value)) as T;
	}
}

/** A stable key variant for a set of query parameters: the same filters always give the same string. */
export function snapshotKey(family: string, params: Record<string, string | number | undefined>): string {
	const parts = Object.entries(params)
		.filter(([, value]) => value !== undefined && value !== '')
		.sort(([a], [b]) => a.localeCompare(b))
		.map(([name, value]) => `${name}=${value}`);

	return `${family}:${parts.join('&')}`;
}
