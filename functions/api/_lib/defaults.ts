/**
 * What a brand-new account starts with, provisioned on first sign-in.
 *
 * Category colours are the validated eight-slot categorical palette, assigned
 * in fixed slot order so adjacent categories stay distinguishable — including
 * under colour-vision deficiency.
 */

export interface DefaultCategory {
	name: string;
	kind: 'income' | 'expense';
	color: string;
	sortOrder: number;
	/** Where the category may be used. Transfer categories budget cash movements. */
	appliesTo?: 'standard' | 'transfer';
	/** Subcategories, one level deep. They inherit their parent's kind and scope. */
	children?: string[];
}

export const DEFAULT_CATEGORIES: DefaultCategory[] = [
	{ name: 'Rent', kind: 'expense', color: '#2a78d6', sortOrder: 0 },
	{
		name: 'Groceries',
		kind: 'expense',
		color: '#eb6834',
		sortOrder: 1,
		children: ['Supermarket', 'Market'],
	},
	{ name: 'Utilities', kind: 'expense', color: '#1baf7a', sortOrder: 2, children: ['Electricity', 'Water', 'Internet'] },
	{ name: 'Transport', kind: 'expense', color: '#eda100', sortOrder: 3, children: ['Fuel', 'Fares'] },
	{ name: 'Dining Out', kind: 'expense', color: '#e87ba4', sortOrder: 4 },
	{ name: 'Health', kind: 'expense', color: '#008300', sortOrder: 5 },
	{ name: 'Entertainment', kind: 'expense', color: '#4a3aa7', sortOrder: 6 },
	{ name: 'Miscellaneous', kind: 'expense', color: '#e34948', sortOrder: 7 },
	{ name: 'Salary', kind: 'income', color: '#2a78d6', sortOrder: 0 },
	{ name: 'Interest', kind: 'income', color: '#eb6834', sortOrder: 1 },

	/**
	 * Money moved between the user's own accounts rather than spent. An
	 * investment contribution is a transfer, so budgeting it needs a category
	 * that transfers can carry.
	 *
	 * `kind` is 'expense' because the budgeted side of a movement is the outflow;
	 * it is not spending, and the income/expense totals exclude it either way.
	 */
	{
		name: 'Cashflow',
		kind: 'expense',
		color: '#4a3aa7',
		sortOrder: 0,
		appliesTo: 'transfer',
		children: ['Investments', 'Savings', 'Debt Repayment'],
	},
];

/**
 * The account types every new user starts with.
 *
 * These are only a starting vocabulary — the user owns the list and may rename,
 * extend or retire any of it. Nothing in the app behaves differently per type;
 * it is a label for grouping and reading, which is why it can be user-defined
 * without changing any calculation.
 */
export interface DefaultAccountType {
	name: string;
	sortOrder: number;
}

/** Used for new accounts and as a new user's display currency. */
export const DEFAULT_CURRENCY = 'PHP';

/** Every default category including subcategories — what provisioning creates. */
export const DEFAULT_CATEGORY_COUNT =
	DEFAULT_CATEGORIES.length + DEFAULT_CATEGORIES.reduce((total, category) => total + (category.children?.length ?? 0), 0);

export const DEFAULT_ACCOUNT_TYPES: DefaultAccountType[] = [
	{ name: 'Checking', sortOrder: 0 },
	{ name: 'Savings', sortOrder: 1 },
	{ name: 'Cash', sortOrder: 2 },
	{ name: 'Credit Card', sortOrder: 3 },
	{ name: 'Investment', sortOrder: 4 },
	{ name: 'Loan', sortOrder: 5 },
];

/**
 * The rest is sample material — balances and transactions that are not the
 * user's. It exists so a first sign-in lands on a dashboard with something to
 * look at instead of a set of zeroes, and it is skipped entirely when
 * `SEED_DEMO_DATA` is `"false"`.
 */
export interface DefaultAccount {
	slug: string;
	name: string;
	/** Matches a `DEFAULT_ACCOUNT_TYPES` name. */
	typeName: string;
	currency: string;
	startingBalance: number;
}

export const DEMO_ACCOUNTS: DefaultAccount[] = [
	{ slug: 'checking', name: 'Checking', typeName: 'Checking', currency: DEFAULT_CURRENCY, startingBalance: 250_000 },
	{ slug: 'savings', name: 'Savings', typeName: 'Savings', currency: DEFAULT_CURRENCY, startingBalance: 1_000_000 },
];

export interface DefaultTransaction {
	accountSlug: string;
	categoryName: string;
	amount: number;
	/** Day of the signing-up month. Kept to 1–28 so every month can hold it. */
	dayOfMonth: number;
	payee: string;
}

export const DEMO_TRANSACTIONS: DefaultTransaction[] = [
	{ accountSlug: 'checking', categoryName: 'Salary', amount: 450_000, dayOfMonth: 1, payee: 'Employer' },
	{ accountSlug: 'checking', categoryName: 'Rent', amount: -150_000, dayOfMonth: 2, payee: 'Landlord' },
	{ accountSlug: 'checking', categoryName: 'Groceries', amount: -8_450, dayOfMonth: 3, payee: 'Corner Market' },
	{ accountSlug: 'checking', categoryName: 'Dining Out', amount: -2_500, dayOfMonth: 11, payee: 'Cafe' },
];

export interface DefaultBudget {
	categoryName: string;
	amount: number;
}

export const DEMO_BUDGETS: DefaultBudget[] = [
	{ categoryName: 'Rent', amount: 150_000 },
	{ categoryName: 'Groceries', amount: 60_000 },
];
