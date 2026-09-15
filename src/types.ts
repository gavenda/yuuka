/** Shapes returned by the yuuka API. Amounts are integer minor units (cents). */

export type CategoryKind = 'income' | 'expense';
/** Where a category may be used: on spending/income, or on transfers. */
export type CategoryScope = 'standard' | 'transfer';

/** Per-user preferences. */
export interface Settings {
	/** The currency every aggregate figure is displayed in. */
	displayCurrency: string;
	createdAt: string;
	updatedAt: string;
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
	createdAt: string;
	updatedAt: string;
}

export interface Budget {
	id: string;
	categoryId: string;
	month: string;
	amount: number;
	createdAt: string;
	updatedAt: string;
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
