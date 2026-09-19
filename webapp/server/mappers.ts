import { FIXED_BUDGET_MONTH, type BudgetMode } from './budget-mode';
import type { CATEGORY_KINDS } from './schemas';

export type CategoryKind = (typeof CATEGORY_KINDS)[number];

export interface SettingsRow {
	display_currency: string;
	budget_mode: BudgetMode;
	default_account_id: string | null;
	created_at: string;
	updated_at: string;
}

export interface AccountTypeRow {
	id: string;
	name: string;
	sort_order: number;
	archived: number;
	created_at: string;
	updated_at: string;
	account_count?: number;
}

export interface AccountRow {
	id: string;
	name: string;
	type_id: string;
	type_name?: string;
	currency: string;
	logo_url: string | null;
	logo_invert_dark: number;
	round_up_source: number;
	starting_balance: number;
	archived: number;
	created_at: string;
	updated_at: string;
	balance?: number;
}

export interface RoundUpRuleRow {
	enabled: number;
	round_to: number;
	destination_account_id: string | null;
	category_id: string | null;
	created_at: string | null;
	updated_at: string | null;
}

export type CategoryScope = 'standard' | 'transfer';

export interface CategoryRow {
	id: string;
	name: string;
	kind: CategoryKind;
	color: string;
	sort_order: number;
	archived: number;
	parent_id: string | null;
	applies_to: CategoryScope;
	created_at: string;
	updated_at: string;
}

export interface TagRow {
	id: string;
	name: string;
	color: string;
	/** How many transactions wear it, from a subquery; a transfer counts once, not once per leg. */
	transaction_count?: number;
	created_at: string;
	updated_at: string;
}

/** The slice of a tag a transaction carries — enough to draw its chip. */
export interface TransactionTag {
	id: string;
	name: string;
	color: string;
}

export interface TransactionRow {
	id: string;
	account_id: string;
	category_id: string | null;
	amount: number;
	occurred_on: string;
	payee: string;
	notes: string;
	transfer_id: string | null;
	/** 1 when a subscription's cron posted this row rather than the user. */
	automated: number;
	created_at: string;
	updated_at: string;
	account_name?: string;
	category_name?: string | null;
	category_color?: string | null;
	/** The account's own balance immediately after this transaction posted. */
	running_balance: number;
	/** JSON array of the tags it wears, from a subquery; absent where a row is read without them. */
	tags_json?: string | null;
}

export interface BudgetRow {
	id: string;
	category_id: string;
	month: string;
	amount: number;
	/** Basis points (1 = 0.01%) of the month's planned income. Takes precedence over `amount` when set. */
	percent_bp: number | null;
	created_at: string;
	updated_at: string;
}

export interface IncomePlanRow {
	id: string;
	month: string;
	amount: number;
	/** Whether `amount` was typed directly or is the take-home net of `gross_amount`. */
	mode: 'gross' | 'fixed';
	gross_amount: number | null;
	created_at: string;
	updated_at: string;
}

export const toSettings = (row: SettingsRow) => ({
	displayCurrency: row.display_currency,
	budgetMode: row.budget_mode,
	defaultAccountId: row.default_account_id,
	createdAt: row.created_at,
	updatedAt: row.updated_at,
});

export const toAccountType = (row: AccountTypeRow) => ({
	id: row.id,
	name: row.name,
	sortOrder: row.sort_order,
	archived: row.archived === 1,
	accountCount: row.account_count ?? 0,
	createdAt: row.created_at,
	updatedAt: row.updated_at,
});

export const toAccount = (row: AccountRow) => ({
	id: row.id,
	name: row.name,
	typeId: row.type_id,
	typeName: row.type_name ?? null,
	currency: row.currency,
	logoUrl: row.logo_url,
	logoInvertDark: row.logo_invert_dark === 1,
	roundUpSource: row.round_up_source === 1,
	startingBalance: row.starting_balance,
	balance: row.balance ?? row.starting_balance,
	archived: row.archived === 1,
	createdAt: row.created_at,
	updatedAt: row.updated_at,
});

export const toRoundUpRule = (row: RoundUpRuleRow) => ({
	enabled: row.enabled === 1,
	roundTo: row.round_to,
	destinationAccountId: row.destination_account_id,
	categoryId: row.category_id,
	createdAt: row.created_at,
	updatedAt: row.updated_at,
});

export const toCategory = (row: CategoryRow) => ({
	id: row.id,
	name: row.name,
	kind: row.kind,
	color: row.color,
	sortOrder: row.sort_order,
	archived: row.archived === 1,
	parentId: row.parent_id,
	appliesTo: row.applies_to,
	createdAt: row.created_at,
	updatedAt: row.updated_at,
});

export const toTag = (row: TagRow) => ({
	id: row.id,
	name: row.name,
	color: row.color,
	transactionCount: row.transaction_count ?? 0,
	createdAt: row.created_at,
	updatedAt: row.updated_at,
});

/** Chips read in a steady order, so a transaction's tags do not shuffle between loads. */
function parseTags(json: string | null | undefined): TransactionTag[] {
	if (!json) return [];
	return (JSON.parse(json) as TransactionTag[]).sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }));
}

export const toTransaction = (row: TransactionRow) => ({
	id: row.id,
	accountId: row.account_id,
	accountName: row.account_name ?? null,
	categoryId: row.category_id,
	categoryName: row.category_name ?? null,
	categoryColor: row.category_color ?? null,
	amount: row.amount,
	occurredOn: row.occurred_on,
	payee: row.payee,
	notes: row.notes,
	transferId: row.transfer_id,
	/** Posted by a subscription at 00:00 UTC — its time of day is not the user's to change. */
	automated: row.automated === 1,
	tags: parseTags(row.tags_json),
	runningBalance: row.running_balance,
	createdAt: row.created_at,
	updatedAt: row.updated_at,
});

export const toBudget = (row: BudgetRow) => ({
	id: row.id,
	categoryId: row.category_id,
	/** Null when this budget is fixed — it applies to every month rather than the one it happens to be stored under. */
	month: row.month === FIXED_BUDGET_MONTH ? null : row.month,
	amount: row.amount,
	/** Whole or fractional percent (e.g. 12.5), null when this budget is a fixed amount. */
	percent: row.percent_bp === null ? null : row.percent_bp / 100,
	createdAt: row.created_at,
	updatedAt: row.updated_at,
});

export const toIncomePlan = (row: IncomePlanRow) => ({
	/** Null when this plan is fixed — it applies to every month rather than the one it happens to be stored under. */
	month: row.month === FIXED_BUDGET_MONTH ? null : row.month,
	amount: row.amount,
	mode: row.mode,
	grossAmount: row.gross_amount,
	createdAt: row.created_at,
	updatedAt: row.updated_at,
});

export interface PayeeRow {
	id: string;
	payee: string;
	kind: 'expense' | 'income' | 'transfer';
	account_id: string | null;
	to_account_id: string | null;
	category_id: string | null;
	notes: string;
	used_count: number;
	last_used_at: string;
	account_name: string | null;
	to_account_name: string | null;
	category_name: string | null;
	category_color: string | null;
}

export const toPayee = (row: PayeeRow) => ({
	id: row.id,
	payee: row.payee,
	kind: row.kind,
	accountId: row.account_id,
	accountName: row.account_name,
	toAccountId: row.to_account_id,
	toAccountName: row.to_account_name,
	categoryId: row.category_id,
	categoryName: row.category_name,
	categoryColor: row.category_color,
	notes: row.notes,
	usedCount: row.used_count,
	lastUsedAt: row.last_used_at,
});

export interface SubscriptionRow {
	id: string;
	account_id: string;
	category_id: string | null;
	amount: number;
	payee: string;
	notes: string;
	start_on: string;
	day_of_month: number;
	next_run_on: string;
	last_run_on: string | null;
	enabled: number;
	created_at: string;
	updated_at: string;
	account_name?: string;
	category_name?: string | null;
	category_color?: string | null;
}

export const toSubscription = (row: SubscriptionRow) => ({
	id: row.id,
	accountId: row.account_id,
	accountName: row.account_name ?? null,
	categoryId: row.category_id,
	categoryName: row.category_name ?? null,
	categoryColor: row.category_color ?? null,
	amount: row.amount,
	payee: row.payee,
	notes: row.notes,
	startOn: row.start_on,
	dayOfMonth: row.day_of_month,
	nextRunOn: row.next_run_on,
	lastRunOn: row.last_run_on,
	enabled: row.enabled === 1,
	createdAt: row.created_at,
	updatedAt: row.updated_at,
});
