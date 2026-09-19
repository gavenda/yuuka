/** Shapes returned by the yuuka API. Amounts are integer minor units (cents). */

export type CategoryKind = 'income' | 'expense';
/** Where a category may be used: on spending/income, or on transfers. */
export type CategoryScope = 'standard' | 'transfer';

export type BudgetMode = 'fixed' | 'monthly';

/** Per-user preferences. */
export interface Settings {
	/** The currency every aggregate figure is displayed in. */
	displayCurrency: string;
	/** Whether a category's planned amount applies to every month, or is set separately per month. Fixed is the default. */
	budgetMode: BudgetMode;
	/** Which account a new transaction opens on. Null falls back to the first active account. */
	defaultAccountId: string | null;
	createdAt: string;
	updatedAt: string;
}

/** "Save the Change": rounds an opted-in account's expenses up and moves the difference into a chosen account. */
export interface RoundUpRule {
	enabled: boolean;
	/** Minor-unit multiple to round up to: 1000 (₱10) or 10000 (₱100). */
	roundTo: 1000 | 10000;
	destinationAccountId: string | null;
	/** Must be a transfer-scope category — a round-up posts as an ordinary transfer. Null stays uncategorised. */
	categoryId: string | null;
	createdAt: string | null;
	updatedAt: string | null;
}

/** A user-defined account type. Everyone starts with a default set. */
export interface AccountType {
	id: string;
	name: string;
	sortOrder: number;
	archived: boolean;
	/** How many accounts currently use it — a type in use cannot be deleted. */
	accountCount: number;
	createdAt: string;
	updatedAt: string;
}

export interface Account {
	id: string;
	name: string;
	typeId: string;
	typeName: string | null;
	currency: string;
	/** Optional http(s) image URL shown beside the account. */
	logoUrl: string | null;
	/** Applies an `invert()` filter to the logo in dark mode, for a dark mark that would otherwise disappear. */
	logoInvertDark: boolean;
	/** Whether this account's own expenses round up under "Save the Change". */
	roundUpSource: boolean;
	startingBalance: number;
	balance: number;
	archived: boolean;
	createdAt: string;
	updatedAt: string;
}

export interface Category {
	id: string;
	name: string;
	kind: CategoryKind;
	color: string;
	sortOrder: number;
	archived: boolean;
	/** Null for a top-level category. Nesting is one level deep. */
	parentId: string | null;
	appliesTo: CategoryScope;
	createdAt: string;
	updatedAt: string;
}

/** A user-defined label. Unlike a category it changes no figure — it is only there to read by. */
export interface Tag {
	id: string;
	name: string;
	color: string;
	createdAt: string;
	updatedAt: string;
}

/** The part of a tag a transaction carries: enough to draw its chip. */
export type TransactionTag = Pick<Tag, 'id' | 'name' | 'color'>;

export interface Transaction {
	id: string;
	accountId: string;
	accountName: string | null;
	categoryId: string | null;
	categoryName: string | null;
	categoryColor: string | null;
	amount: number;
	occurredOn: string;
	payee: string;
	notes: string;
	transferId: string | null;
	/** Posted by a subscription at 00:00 UTC, so its time of day is not the user's to change. */
	automated: boolean;
	/** The tags it wears, in name order. Both legs of a transfer wear the same ones. */
	tags: TransactionTag[];
	/** This account's own balance immediately after the transaction posted. */
	runningBalance: number;
	createdAt: string;
	updatedAt: string;
}

/** A monthly charge (or credit) the Worker posts as an ordinary transaction at 00:00 UTC. */
export interface Subscription {
	id: string;
	accountId: string;
	accountName: string | null;
	categoryId: string | null;
	categoryName: string | null;
	categoryColor: string | null;
	/** Signed minor units: negative is an outflow. */
	amount: number;
	payee: string;
	notes: string;
	/** The date the schedule was started from; its day of the month is the anchor. */
	startOn: string;
	dayOfMonth: number;
	/** The next date it posts. A short month clamps the day, so this is not always `dayOfMonth`. */
	nextRunOn: string;
	lastRunOn: string | null;
	/** Paused subscriptions post nothing. */
	enabled: boolean;
	createdAt: string;
	updatedAt: string;
}

export interface Budget {
	id: string;
	categoryId: string;
	/** Null when this budget is fixed — it applies to every month rather than one in particular. */
	month: string | null;
	amount: number;
	/** Whole or fractional percent of the month's planned income (e.g. 12.5), or null for a fixed amount. */
	percent: number | null;
	createdAt: string;
	updatedAt: string;
}

/** The total income planned for a month, set independently of what actually came in. */
export interface IncomePlan {
	/** Null when this plan is fixed — it applies to every month rather than one in particular. */
	month: string | null;
	amount: number;
	/** Whether `amount` was typed directly or is the take-home net of `grossAmount`. */
	mode: 'gross' | 'fixed';
	grossAmount: number | null;
	createdAt: string | null;
	updatedAt: string | null;
}

export interface SubcategoryBreakdown {
	categoryId: string;
	name: string;
	color: string;
	/** Already included in the parent's `actual`. */
	actual: number;
}

export interface CategoryBreakdown {
	categoryId: string;
	name: string;
	kind: CategoryKind;
	color: string;
	appliesTo: CategoryScope;
	planned: number;
	/** Set when `planned` is a share of the month's planned income rather than a fixed amount. */
	plannedPercent: number | null;
	/** Includes everything filed under this category's children. */
	actual: number;
	remaining: number;
	children: SubcategoryBreakdown[];
}

export interface Summary {
	month: string;
	income: number;
	expenses: number;
	net: number;
	/** The planned total for the month, set separately from actual income — what a percent-based budget is a share of. */
	plannedIncome: number;
	/** How `plannedIncome` was set, so the Budget page can reopen its editor in the same mode. */
	plannedIncomeMode: 'gross' | 'fixed';
	/** The gross figure `plannedIncome` was derived from, when `plannedIncomeMode` is 'gross'. */
	plannedIncomeGrossAmount: number | null;
	netWorth: number;
	/** Money moved into transfer-categorised destinations, e.g. investments. */
	cashflow: number;
	totalBudgeted: number;
	accounts: Account[];
	categories: CategoryBreakdown[];
	dailySpend: { date: string; amount: number }[];
	generatedAt: string;
	cached: boolean;
}

export interface TransactionPage {
	transactions: Transaction[];
	total: number;
	limit: number;
	offset: number;
}

export interface TransactionFilters {
	month?: string;
	from?: string;
	to?: string;
	accountId?: string;
	categoryId?: string;
	search?: string;
	limit?: number;
	offset?: number;
}

/** A remembered payee and what it was last filed under. */
export interface Payee {
	id: string;
	payee: string;
	kind: 'expense' | 'income' | 'transfer';
	accountId: string | null;
	accountName: string | null;
	toAccountId: string | null;
	toAccountName: string | null;
	categoryId: string | null;
	categoryName: string | null;
	categoryColor: string | null;
	notes: string;
	usedCount: number;
	lastUsedAt: string;
}
