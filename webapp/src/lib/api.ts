import type {
	Account,
	AccountType,
	Budget,
	Category,
	IncomePlan,
	Payee,
	RoundUpRule,
	Settings,
	Summary,
	Transaction,
	TransactionFilters,
	TransactionPage,
} from '@/types';

/** An error carrying the API's status code and any per-field validation detail. */
export class ApiError extends Error {
	readonly status: number;
	readonly details?: Record<string, string[]>;

	constructor(status: number, message: string, details?: Record<string, string[]>) {
		super(message);
		this.name = 'ApiError';
		this.status = status;
		this.details = details;
	}

	/** True when the session is missing or expired and the user must sign in again. */
	get isUnauthorized(): boolean {
		return this.status === 401;
	}
}

/**
 * The API asks for a token per request rather than holding one, so Auth0's SDK
 * can refresh it when it is close to expiring. The provider is installed by the
 * auth store, which keeps `api` free of any import back into Pinia.
 */
type TokenProvider = () => Promise<string | null>;

let tokenProvider: TokenProvider | null = null;

export function setTokenProvider(provider: TokenProvider): void {
	tokenProvider = provider;
}

/** Called when any request is rejected as unauthorised, so the app can sign out. */
let onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(handler: () => void): void {
	onUnauthorized = handler;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
	const token = tokenProvider ? await tokenProvider() : null;

	const response = await fetch(`/api${path}`, {
		...init,
		headers: {
			'content-type': 'application/json',
			...(token ? { authorization: `Bearer ${token}` } : {}),
			...(init.headers as Record<string, string> | undefined),
		},
	});

	if (response.status === 204) return undefined as T;

	const body = (await response.json().catch(() => null)) as (T & { error?: string; details?: Record<string, string[]> }) | null;

	if (!response.ok) {
		if (response.status === 401) onUnauthorized?.();
		throw new ApiError(response.status, body?.error ?? `Request failed (${response.status}).`, body?.details);
	}

	return body as T;
}

const body = (value: unknown) => JSON.stringify(value);

function queryString(filters: Record<string, string | number | undefined>): string {
	const params = new URLSearchParams();
	for (const [key, value] of Object.entries(filters)) {
		if (value !== undefined && value !== '') params.set(key, String(value));
	}
	const query = params.toString();
	return query ? `?${query}` : '';
}

export const api = {
	me: () => request<{ subject: string; issuer: string | null; expiresAt: number | null; permissions: string[] }>('/auth/me'),

	listPayees: (search?: string, limit = 20) => request<{ payees: Payee[] }>(`/payees${queryString({ search, limit })}`),
	forgetPayee: (id: string) => request<void>(`/payees/${id}`, { method: 'DELETE' }),

	settings: () => request<{ settings: Settings }>('/settings'),
	updateSettings: (input: Partial<Settings>) => request<{ settings: Settings }>('/settings', { method: 'PATCH', body: body(input) }),

	getRoundUpRule: () => request<{ roundUpRule: RoundUpRule }>('/round-up'),
	updateRoundUpRule: (input: Partial<RoundUpRule>) =>
		request<{ roundUpRule: RoundUpRule }>('/round-up', { method: 'PATCH', body: body(input) }),

	listAccountTypes: (includeArchived = false) =>
		request<{ accountTypes: AccountType[] }>(`/account-types${queryString({ includeArchived: String(includeArchived) })}`),
	createAccountType: (input: Partial<AccountType>) =>
		request<{ accountType: AccountType }>('/account-types', { method: 'POST', body: body(input) }),
	updateAccountType: (id: string, input: Partial<AccountType>) =>
		request<{ accountType: AccountType }>(`/account-types/${id}`, { method: 'PATCH', body: body(input) }),
	deleteAccountType: (id: string) => request<void>(`/account-types/${id}`, { method: 'DELETE' }),

	listAccounts: (includeArchived = false) =>
		request<{ accounts: Account[] }>(`/accounts${queryString({ includeArchived: String(includeArchived) })}`),
	createAccount: (input: Partial<Account>) => request<{ account: Account }>('/accounts', { method: 'POST', body: body(input) }),
	updateAccount: (id: string, input: Partial<Account>) =>
		request<{ account: Account }>(`/accounts/${id}`, { method: 'PATCH', body: body(input) }),
	deleteAccount: (id: string, includeTransactions = false) =>
		request<void>(`/accounts/${id}${queryString({ includeTransactions: String(includeTransactions) })}`, { method: 'DELETE' }),
	adjustAccount: (id: string, input: { balance: number; occurredOn: string; payee?: string; notes?: string }) =>
		request<{ transaction: Transaction }>(`/accounts/${id}/adjust`, { method: 'POST', body: body(input) }),

	listCategories: (includeArchived = false) =>
		request<{ categories: Category[] }>(`/categories${queryString({ includeArchived: String(includeArchived) })}`),
	createCategory: (input: Partial<Category>) => request<{ category: Category }>('/categories', { method: 'POST', body: body(input) }),
	updateCategory: (id: string, input: Partial<Category>) =>
		request<{ category: Category }>(`/categories/${id}`, { method: 'PATCH', body: body(input) }),
	deleteCategory: (id: string) => request<void>(`/categories/${id}`, { method: 'DELETE' }),

	listTransactions: (filters: TransactionFilters = {}) =>
		request<TransactionPage>(`/transactions${queryString(filters as Record<string, string | number | undefined>)}`),
	createTransaction: (input: Record<string, unknown>) =>
		request<{ transaction: Transaction; roundUp: Transaction | null }>('/transactions', { method: 'POST', body: body(input) }),
	updateTransaction: (id: string, input: Record<string, unknown>) =>
		request<{ transaction: Transaction }>(`/transactions/${id}`, { method: 'PATCH', body: body(input) }),
	deleteTransaction: (id: string) => request<void>(`/transactions/${id}`, { method: 'DELETE' }),
	createTransfer: (input: Record<string, unknown>) =>
		request<{ transferId: string; transactions: Transaction[] }>('/transactions/transfer', { method: 'POST', body: body(input) }),
	updateTransfer: (transferId: string, input: Record<string, unknown>) =>
		request<{ transferId: string; transactions: Transaction[] }>(`/transactions/transfer/${transferId}`, {
			method: 'PATCH',
			body: body(input),
		}),

	listBudgets: (month: string) => request<{ budgets: Budget[] }>(`/budgets${queryString({ month })}`),
	setBudget: (input: { categoryId: string; month: string; amount: number } | { categoryId: string; month: string; percent: number }) =>
		request<{ budget: Budget }>('/budgets', { method: 'PUT', body: body(input) }),
	deleteBudget: (id: string) => request<void>(`/budgets/${id}`, { method: 'DELETE' }),

	getIncomePlan: (month: string) => request<{ incomePlan: IncomePlan }>(`/income-plan${queryString({ month })}`),
	setIncomePlan: (input: { month: string; amount: number; mode?: 'gross' | 'fixed'; grossAmount?: number }) =>
		request<{ incomePlan: IncomePlan }>('/income-plan', { method: 'PUT', body: body(input) }),

	summary: (month: string) => request<Summary>(`/summary${queryString({ month })}`),
};
