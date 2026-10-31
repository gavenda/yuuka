import type { Account, AccountType, Category, Payee, RoundUpRule, Settings, Subscription, Summary, Tag, Transaction } from '@/types';

/**
 * Whole rows, in the API's shapes, for specs that put them through the local copy: its tables are
 * typed, and sorting a list the way the API does reads fields a bare `{ id }` does not have.
 */

const stamps = { createdAt: '2026-09-01T00:00:00.000Z', updatedAt: '2026-09-01T00:00:00.000Z' };

export const account = (id: string, extra: Partial<Account> = {}): Account => ({
	id,
	name: id,
	typeId: 'type',
	typeName: 'Checking',
	currency: 'PHP',
	logoUrl: null,
	logoInvertDark: false,
	bank: null,
	roundUpSource: false,
	startingBalance: 0,
	balance: 0,
	archived: false,
	...stamps,
	...extra,
});

export const accountType = (id: string, extra: Partial<AccountType> = {}): AccountType => ({
	id,
	name: id,
	sortOrder: 0,
	archived: false,
	accountCount: 0,
	...stamps,
	...extra,
});

export const category = (id: string, extra: Partial<Category> = {}): Category => ({
	id,
	name: id,
	kind: 'expense',
	color: '#4477aa',
	sortOrder: 0,
	archived: false,
	parentId: null,
	...stamps,
	...extra,
});

export const tag = (id: string, extra: Partial<Tag> = {}): Tag => ({
	id,
	name: id,
	color: '#228833',
	transactionCount: 0,
	total: 0,
	...stamps,
	...extra,
});

export const settings = (extra: Partial<Settings> = {}): Settings => ({
	displayCurrency: 'PHP',
	budgetMode: 'fixed',
	defaultAccountId: null,
	...stamps,
	...extra,
});

export const roundUpRule = (extra: Partial<RoundUpRule> = {}): RoundUpRule => ({
	enabled: false,
	roundTo: 1000,
	destinationAccountId: null,
	categoryId: null,
	createdAt: null,
	updatedAt: null,
	...extra,
});

export const transaction = (id: string, extra: Partial<Transaction> = {}): Transaction => ({
	id,
	accountId: 'a',
	accountName: 'a',
	categoryId: null,
	categoryName: null,
	categoryColor: null,
	amount: -100,
	occurredOn: '2026-09-10',
	payee: id,
	notes: '',
	transferId: null,
	automated: false,
	tags: [],
	runningBalance: 0,
	...stamps,
	...extra,
});

export const subscription = (id: string, extra: Partial<Subscription> = {}): Subscription => ({
	id,
	accountId: 'a',
	accountName: 'a',
	categoryId: null,
	categoryName: null,
	categoryColor: null,
	amount: -100,
	payee: id,
	notes: '',
	startOn: '2026-09-01',
	dayOfMonth: 1,
	nextRunOn: '2026-10-01',
	lastRunOn: null,
	enabled: true,
	...stamps,
	...extra,
});

export const payee = (id: string, extra: Partial<Payee> = {}): Payee => ({
	id,
	payee: id,
	kind: 'expense',
	accountId: null,
	accountName: null,
	toAccountId: null,
	toAccountName: null,
	categoryId: null,
	categoryName: null,
	categoryColor: null,
	notes: '',
	usedCount: 1,
	lastUsedAt: '2026-09-01T00:00:00.000Z',
	...extra,
});

/** A summary for [month], with a [marker] to tell one copy of it from another. */
export const summary = (month: string, marker = 'x'): Summary & { marker: string } => ({
	month,
	income: 0,
	expenses: 0,
	net: 0,
	plannedIncome: 0,
	plannedIncomeMode: 'fixed',
	plannedIncomeGrossAmount: null,
	netWorth: 0,
	cashflow: 0,
	totalBudgeted: 0,
	accounts: [],
	categories: [],
	dailySpend: [],
	topPayees: [],
	roundUps: 0,
	generatedAt: stamps.createdAt,
	cached: false,
	marker,
});

/** A whole ledger, its one account named [id]. */
export const ledger = (id: string) => ({
	accounts: [account(id)],
	accountTypes: [accountType('type')],
	categories: [] as Category[],
	tags: [] as Tag[],
	settings: settings({ displayCurrency: 'USD' }),
	roundUpRule: roundUpRule(),
});

export const ids = (rows: readonly { id: string }[]) => rows.map((row) => row.id);
