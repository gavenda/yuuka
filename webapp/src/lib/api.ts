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
	TransactionFilters,
	TransactionPage,
} from '@/types';

import { request } from './http';
import { newId } from './ids';
import {
	ledgerView,
	patched,
	provisionalAccount,
	provisionalAccountType,
	provisionalAdjustment,
	provisionalCategory,
	provisionalRoundUp,
	provisionalSubscription,
	provisionalTag,
	provisionalTransaction,
	provisionalTransfer,
} from './provisional';
import { queued } from './queue';

// Re-exported so nothing that already imports these from `@/lib/api` has to move.
export { ApiError, isNetworkError, setTokenProvider, setUnauthorizedHandler } from './http';

/**
 * The settings as they stand, for an edit that has to show before it is sent.
 * Falling back to the defaults rather than failing keeps a write working on a
 * screen reached before the ledger finished loading.
 */
function settingsNow(): Settings {
	const ledger = ledgerView();
	const now = new Date().toISOString();
	return (
		ledger.settings ?? {
			displayCurrency: ledger.displayCurrency,
			budgetMode: 'fixed',
			defaultAccountId: null,
			createdAt: now,
			updatedAt: now,
		}
	);
}

function queryString(filters: Record<string, string | number | undefined>): string {
	const params = new URLSearchParams();
	for (const [key, value] of Object.entries(filters)) {
		if (value !== undefined && value !== '') params.set(key, String(value));
	}
	const query = params.toString();
	return query ? `?${query}` : '';
}

/**
 * The writes.
 *
 * Every one of these is local-first: it goes into the outbox and answers with
 * the row as it will be, rather than waiting for the server. The screen
 * therefore updates at the speed of the browser, online or not, and the queue
 * drains behind it — see `queue.ts` for what happens when it lands, and
 * `provisional.ts` for how the answer is built.
 *
 * A write names the row it creates (`newId`) so that the next write can
 * reference it before either has been sent, and names the row it changes
 * (`entity`, `id`) so the server can drop an edit that a newer one has already
 * overtaken.
 */
export const api = {
	me: () => request<{ subject: string; issuer: string | null; expiresAt: number | null; permissions: string[] }>('/auth/me'),

	listPayees: (search?: string, limit = 20) => request<{ payees: Payee[] }>(`/payees${queryString({ search, limit })}`),
	forgetPayee: (id: string) => queued<void>({ method: 'DELETE', path: `/api/payees/${id}`, optimistic: undefined }),

	settings: () => request<{ settings: Settings }>('/settings'),
	updateSettings: (input: Partial<Settings>) => {
		const current = settingsNow();
		return queued({
			method: 'PATCH',
			path: '/api/settings',
			body: input,
			entity: 'settings',
			optimistic: { settings: patched(current, input) },
		});
	},

	getRoundUpRule: () => request<{ roundUpRule: RoundUpRule }>('/round-up'),
	updateRoundUpRule: (input: Partial<RoundUpRule>) => {
		const current = ledgerView().roundUpRule ?? {
			enabled: false,
			roundTo: 1000 as const,
			destinationAccountId: null,
			categoryId: null,
			createdAt: null,
			updatedAt: null,
		};
		return queued({
			method: 'PATCH',
			path: '/api/round-up',
			body: input,
			entity: 'roundUpRule',
			optimistic: { roundUpRule: patched(current, input) },
		});
	},

	listAccountTypes: (includeArchived = false) =>
		request<{ accountTypes: AccountType[] }>(`/account-types${queryString({ includeArchived: String(includeArchived) })}`),
	createAccountType: (input: Partial<AccountType>) => {
		const payload = { ...input, id: newId('accountType') };
		return queued({
			method: 'POST',
			path: '/api/account-types',
			body: payload,
			optimistic: { accountType: provisionalAccountType({ id: payload.id, name: payload.name ?? '', sortOrder: payload.sortOrder }) },
		});
	},
	updateAccountType: (id: string, input: Partial<AccountType>) => {
		const current = ledgerView().accountTypes.find((type) => type.id === id);
		return queued({
			method: 'PATCH',
			path: `/api/account-types/${id}`,
			body: input,
			entity: 'accountType',
			id,
			optimistic: { accountType: patched(current ?? provisionalAccountType({ id, name: input.name ?? '' }), input) },
		});
	},
	deleteAccountType: (id: string) =>
		queued<void>({ method: 'DELETE', path: `/api/account-types/${id}`, entity: 'accountType', id, optimistic: undefined }),

	listAccounts: (includeArchived = false) =>
		request<{ accounts: Account[] }>(`/accounts${queryString({ includeArchived: String(includeArchived) })}`),
	createAccount: (input: Partial<Account>) => {
		const payload = { ...input, id: newId('account') };
		return queued({
			method: 'POST',
			path: '/api/accounts',
			body: payload,
			optimistic: {
				account: provisionalAccount(ledgerView(), { ...payload, name: payload.name ?? '', typeId: payload.typeId ?? '' }),
			},
		});
	},
	updateAccount: (id: string, input: Partial<Account>) => {
		const ledger = ledgerView();
		const current = ledger.accounts.find((account) => account.id === id);
		// Changing the type changes the label beside the account, so the name is
		// resolved here rather than left showing the old one until the refresh.
		const typeName = input.typeId ? (ledger.accountTypes.find((type) => type.id === input.typeId)?.name ?? null) : undefined;
		return queued({
			method: 'PATCH',
			path: `/api/accounts/${id}`,
			body: input,
			entity: 'account',
			id,
			optimistic: {
				account: patched(current ?? provisionalAccount(ledger, { id, name: input.name ?? '', typeId: input.typeId ?? '' }), {
					...input,
					...(typeName === undefined ? {} : { typeName }),
				}),
			},
		});
	},
	/**
	 * Deleting an account takes its transactions with it, which is why the API
	 * answers 409 until the caller repeats the request with the flag. That
	 * confirmation is a question about data this client already holds, so it is
	 * still asked here — only the request that follows it is queued.
	 */
	deleteAccount: (id: string, includeTransactions = false) =>
		includeTransactions
			? queued<void>({
					method: 'DELETE',
					path: `/api/accounts/${id}${queryString({ includeTransactions: 'true' })}`,
					entity: 'account',
					id,
					optimistic: undefined,
				})
			: request<void>(`/accounts/${id}`, { method: 'DELETE' }),
	adjustAccount: (id: string, input: { balance: number; occurredOn: string; payee?: string; notes?: string }) => {
		const payload = { ...input, id: newId('transaction') };
		return queued({
			method: 'POST',
			path: `/api/accounts/${id}/adjust`,
			body: payload,
			optimistic: {
				transaction: provisionalAdjustment(ledgerView(), id, {
					id: payload.id,
					balance: input.balance,
					occurredOn: input.occurredOn,
					payee: input.payee ?? '',
					notes: input.notes ?? '',
				}),
			},
		});
	},

	listCategories: (includeArchived = false) =>
		request<{ categories: Category[] }>(`/categories${queryString({ includeArchived: String(includeArchived) })}`),
	createCategory: (input: Partial<Category>) => {
		const payload = { ...input, id: newId('category') };
		return queued({
			method: 'POST',
			path: '/api/categories',
			body: payload,
			optimistic: {
				category: provisionalCategory(ledgerView(), { ...payload, name: payload.name ?? '', kind: payload.kind ?? 'expense' }),
			},
		});
	},
	updateCategory: (id: string, input: Partial<Category>) => {
		const ledger = ledgerView();
		const current = ledger.categories.find((category) => category.id === id);
		return queued({
			method: 'PATCH',
			path: `/api/categories/${id}`,
			body: input,
			entity: 'category',
			id,
			optimistic: {
				category: patched(current ?? provisionalCategory(ledger, { id, name: input.name ?? '', kind: input.kind ?? 'expense' }), input),
			},
		});
	},
	deleteCategory: (id: string) =>
		queued<void>({ method: 'DELETE', path: `/api/categories/${id}`, entity: 'category', id, optimistic: undefined }),

	listTags: () => request<{ tags: Tag[] }>('/tags'),
	createTag: (input: Partial<Tag>) => {
		const payload = { ...input, id: newId('tag') };
		return queued({
			method: 'POST',
			path: '/api/tags',
			body: payload,
			optimistic: { tag: provisionalTag({ id: payload.id, name: payload.name ?? '', color: payload.color }) },
		});
	},
	updateTag: (id: string, input: Partial<Tag>) => {
		const current = ledgerView().tags.find((tag) => tag.id === id);
		return queued({
			method: 'PATCH',
			path: `/api/tags/${id}`,
			body: input,
			entity: 'tag',
			id,
			optimistic: { tag: patched(current ?? provisionalTag({ id, name: input.name ?? '' }), input) },
		});
	},
	deleteTag: (id: string) => queued<void>({ method: 'DELETE', path: `/api/tags/${id}`, entity: 'tag', id, optimistic: undefined }),

	listTransactions: (filters: TransactionFilters = {}) =>
		request<TransactionPage>(`/transactions${queryString(filters as Record<string, string | number | undefined>)}`),
	/**
	 * The one call that can trigger "Save the Change". The client works the
	 * round-up out itself so the balance it shows is right at once, and names the
	 * three rows it drew, so the API's answer replaces them rather than arriving
	 * as a second copy.
	 */
	createTransaction: (input: Record<string, unknown>) => {
		const ledger = ledgerView();
		const draft = {
			id: newId('transaction'),
			accountId: String(input.accountId ?? ''),
			categoryId: (input.categoryId as string | null) ?? null,
			amount: Number(input.amount ?? 0),
			occurredOn: String(input.occurredOn ?? ''),
			payee: String(input.payee ?? ''),
			notes: String(input.notes ?? ''),
			tagIds: (input.tagIds as string[] | undefined) ?? [],
		};

		const roundUpIds = { transferId: newId('transfer'), fromId: newId('transaction'), toId: newId('transaction') };
		const roundUp = provisionalRoundUp(ledger, draft, roundUpIds);

		return queued({
			method: 'POST',
			path: '/api/transactions',
			body: { ...input, id: draft.id, roundUpIds },
			optimistic: {
				transaction: provisionalTransaction(ledger, draft),
				// The destination leg is what the interface surfaces as feedback.
				roundUp: roundUp?.destination ?? null,
			},
		});
	},
	updateTransaction: (id: string, input: Record<string, unknown>) => {
		const ledger = ledgerView();
		const current = ledger.transaction(id);
		const merged = {
			id,
			accountId: String(input.accountId ?? current?.accountId ?? ''),
			categoryId: (input.categoryId as string | null | undefined) ?? current?.categoryId ?? null,
			amount: Number(input.amount ?? current?.amount ?? 0),
			occurredOn: String(input.occurredOn ?? current?.occurredOn ?? ''),
			payee: String(input.payee ?? current?.payee ?? ''),
			notes: String(input.notes ?? current?.notes ?? ''),
			// Left out of a patch, the tags stay as they are.
			tagIds: (input.tagIds as string[] | undefined) ?? current?.tags.map((tag) => tag.id),
			transferId: current?.transferId ?? null,
		};

		return queued({
			method: 'PATCH',
			path: `/api/transactions/${id}`,
			body: input,
			entity: 'transaction',
			id,
			optimistic: { transaction: provisionalTransaction(ledger, merged) },
		});
	},
	deleteTransaction: (id: string) =>
		queued<void>({ method: 'DELETE', path: `/api/transactions/${id}`, entity: 'transaction', id, optimistic: undefined }),
	createTransfer: (input: Record<string, unknown>) => {
		const ids = { transferId: newId('transfer'), fromId: newId('transaction'), toId: newId('transaction') };

		return queued({
			method: 'POST',
			path: '/api/transactions/transfer',
			body: { ...input, ids },
			optimistic: {
				transferId: ids.transferId,
				transactions: provisionalTransfer(ledgerView(), {
					ids,
					fromAccountId: String(input.fromAccountId ?? ''),
					toAccountId: String(input.toAccountId ?? ''),
					categoryId: (input.categoryId as string | null) ?? null,
					amount: Number(input.amount ?? 0),
					occurredOn: String(input.occurredOn ?? ''),
					payee: String(input.payee ?? ''),
					notes: String(input.notes ?? ''),
					tagIds: input.tagIds as string[] | undefined,
				}),
			},
		});
	},
	updateTransfer: (transferId: string, input: Record<string, unknown>) => {
		const ledger = ledgerView();
		// An edit keeps the legs it already has; only what they say changes.
		const legs = [ledger.transaction(String(input.fromId ?? '')), ledger.transaction(String(input.toId ?? ''))];
		const ids = {
			transferId,
			fromId: legs[0]?.id ?? newId('transaction'),
			toId: legs[1]?.id ?? newId('transaction'),
		};

		return queued({
			method: 'PATCH',
			path: `/api/transactions/transfer/${transferId}`,
			body: input,
			entity: 'transfer',
			id: transferId,
			optimistic: {
				transferId,
				transactions: provisionalTransfer(ledger, {
					ids,
					fromAccountId: String(input.fromAccountId ?? ''),
					toAccountId: String(input.toAccountId ?? ''),
					categoryId: (input.categoryId as string | null) ?? null,
					amount: Number(input.amount ?? 0),
					occurredOn: String(input.occurredOn ?? ''),
					payee: String(input.payee ?? ''),
					notes: String(input.notes ?? ''),
					tagIds: input.tagIds as string[] | undefined,
				}),
			},
		});
	},

	listSubscriptions: () => request<{ subscriptions: Subscription[] }>('/subscriptions'),
	createSubscription: (input: Record<string, unknown>) => {
		const id = newId('subscription');
		return queued({
			method: 'POST',
			path: '/api/subscriptions',
			body: { ...input, id },
			optimistic: {
				subscription: provisionalSubscription(ledgerView(), {
					id,
					accountId: String(input.accountId ?? ''),
					categoryId: (input.categoryId as string | null) ?? null,
					amount: Number(input.amount ?? 0),
					payee: String(input.payee ?? ''),
					notes: String(input.notes ?? ''),
					startOn: String(input.startOn ?? ''),
				}),
			},
		});
	},
	updateSubscription: (id: string, input: Record<string, unknown>) => {
		const ledger = ledgerView();
		const current = ledger.subscription(id);
		return queued({
			method: 'PATCH',
			path: `/api/subscriptions/${id}`,
			body: input,
			entity: 'subscription',
			id,
			optimistic: {
				subscription: patched(
					current ??
						provisionalSubscription(ledger, {
							id,
							accountId: String(input.accountId ?? ''),
							categoryId: null,
							amount: 0,
							payee: '',
							notes: '',
							startOn: String(input.startOn ?? ''),
						}),
					input as Partial<Subscription>,
				),
			},
		});
	},
	deleteSubscription: (id: string) =>
		queued<void>({ method: 'DELETE', path: `/api/subscriptions/${id}`, entity: 'subscription', id, optimistic: undefined }),

	listBudgets: (month: string) => request<{ budgets: Budget[] }>(`/budgets${queryString({ month })}`),
	/**
	 * An upsert: a category carries one plan, and setting it again replaces it.
	 * The id is only used when there is no plan yet, so a queued create and a
	 * queued change are the same call.
	 */
	setBudget: (input: { categoryId: string; month: string; amount: number } | { categoryId: string; month: string; percent: number }) => {
		const id = newId('budget');
		const isPercent = 'percent' in input;
		return queued({
			method: 'PUT',
			path: '/api/budgets',
			body: { ...input, id },
			optimistic: {
				budget: {
					id,
					categoryId: input.categoryId,
					month: input.month,
					amount: isPercent ? 0 : input.amount,
					percent: isPercent ? input.percent : null,
					createdAt: new Date().toISOString(),
					updatedAt: new Date().toISOString(),
				} as Budget,
			},
		});
	},
	deleteBudget: (id: string) => queued<void>({ method: 'DELETE', path: `/api/budgets/${id}`, entity: 'budget', id, optimistic: undefined }),

	getIncomePlan: (month: string) => request<{ incomePlan: IncomePlan }>(`/income-plan${queryString({ month })}`),
	setIncomePlan: (input: { month: string; amount: number; mode?: 'gross' | 'fixed'; grossAmount?: number }) =>
		queued({
			method: 'PUT',
			path: '/api/income-plan',
			body: input,
			optimistic: {
				incomePlan: {
					month: input.month,
					amount: input.amount,
					mode: input.mode ?? 'fixed',
					grossAmount: input.grossAmount ?? null,
					createdAt: new Date().toISOString(),
					updatedAt: new Date().toISOString(),
				} as IncomePlan,
			},
		}),

	summary: (month: string) => request<Summary>(`/summary${queryString({ month })}`),
};
