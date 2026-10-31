import type {
	Account,
	AccountType,
	Budget,
	Category,
	IncomePlan,
	Payee,
	PricePoint,
	RoundUpRule,
	Security,
	Trade,
	TradeKind,
	Settings,
	Subscription,
	Summary,
	Tag,
	TransactionFilters,
	Trend,
	TransactionPage,
} from '@/types';

import * as local from './cache';
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
import { tradeCash } from './investments';

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

/**
 * Hands back [answer] once what it saved is in the local copy as well. A save is written there before
 * the server has seen it, as Android writes the provisional row into Room, so the next page load
 * paints it even offline; the server's rows replace it when the slices it touched are refetched. The
 * copy is best-effort, so this never fails the save. It is waited for — it is the browser's own disk,
 * never the network — so a screen can show the save from the copy the moment it resolves.
 */
async function saved<T>(answer: Promise<T>, keep: (value: T) => Promise<void>): Promise<T> {
	const value = await answer;
	await keep(value).catch(() => undefined);
	return value;
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
	forgetPayee: (id: string) =>
		saved(queued<void>({ method: 'DELETE', path: `/api/payees/${id}`, optimistic: undefined }), () => local.deletePayee(id)),

	settings: () => request<{ settings: Settings }>('/settings'),
	updateSettings: (input: Partial<Settings>) => {
		const current = settingsNow();
		return saved(
			queued({
				method: 'PATCH',
				path: '/api/settings',
				body: input,
				entity: 'settings',
				optimistic: { settings: patched(current, input) },
			}),
			(answer) => local.saveSettings(answer.settings),
		);
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
		return saved(
			queued({
				method: 'PATCH',
				path: '/api/round-up',
				body: input,
				entity: 'roundUpRule',
				optimistic: { roundUpRule: patched(current, input) },
			}),
			(answer) => local.saveRoundUpRule(answer.roundUpRule),
		);
	},

	listAccountTypes: (includeArchived = false) =>
		request<{ accountTypes: AccountType[] }>(`/account-types${queryString({ includeArchived: String(includeArchived) })}`),
	createAccountType: (input: Partial<AccountType>) => {
		const payload = { ...input, id: newId('accountType') };
		return saved(
			queued({
				method: 'POST',
				path: '/api/account-types',
				body: payload,
				optimistic: { accountType: provisionalAccountType({ id: payload.id, name: payload.name ?? '', sortOrder: payload.sortOrder }) },
			}),
			(answer) => local.saveAccountType(answer.accountType),
		);
	},
	updateAccountType: (id: string, input: Partial<AccountType>) => {
		const current = ledgerView().accountTypes.find((type) => type.id === id);
		return saved(
			queued({
				method: 'PATCH',
				path: `/api/account-types/${id}`,
				body: input,
				entity: 'accountType',
				id,
				optimistic: { accountType: patched(current ?? provisionalAccountType({ id, name: input.name ?? '' }), input) },
			}),
			(answer) => local.saveAccountType(answer.accountType),
		);
	},
	/** The types in the order they were dragged into, first to last: each takes its place as its sort order. */
	reorderAccountTypes: (ids: string[]) => {
		const place = new Map(ids.map((id, index) => [id, index]));
		const reordered = ledgerView().accountTypes.map((type) => (place.has(type.id) ? { ...type, sortOrder: place.get(type.id)! } : type));
		return saved(
			queued({
				method: 'PUT',
				path: '/api/account-types/order',
				body: { ids },
				optimistic: { accountTypes: reordered },
			}),
			(answer) => local.saveAccountTypes(answer.accountTypes),
		);
	},
	deleteAccountType: (id: string) =>
		saved(queued<void>({ method: 'DELETE', path: `/api/account-types/${id}`, entity: 'accountType', id, optimistic: undefined }), () =>
			local.deleteAccountType(id),
		),

	listAccounts: (includeArchived = false) =>
		request<{ accounts: Account[] }>(`/accounts${queryString({ includeArchived: String(includeArchived) })}`),
	createAccount: (input: Partial<Account>) => {
		const payload = { ...input, id: newId('account') };
		return saved(
			queued({
				method: 'POST',
				path: '/api/accounts',
				body: payload,
				optimistic: {
					account: provisionalAccount(ledgerView(), { ...payload, name: payload.name ?? '', typeId: payload.typeId ?? '' }),
				},
			}),
			(answer) => local.saveAccount(answer.account),
		);
	},
	updateAccount: (id: string, input: Partial<Account>) => {
		const ledger = ledgerView();
		const current = ledger.accounts.find((account) => account.id === id);
		// Changing the type changes the label beside the account, so the name is
		// resolved here rather than left showing the old one until the refresh.
		const typeName = input.typeId ? (ledger.accountTypes.find((type) => type.id === input.typeId)?.name ?? null) : undefined;
		return saved(
			queued({
				method: 'PATCH',
				path: `/api/accounts/${id}`,
				body: input,
				entity: 'account',
				id,
				optimistic: {
					account: patched(current ?? provisionalAccount(ledger, { id, name: input.name ?? '', typeId: input.typeId ?? '' }), {
						...input,
						...(typeName === undefined ? {} : { typeName }),
						// The balance is the starting balance plus everything posted to it, so moving the one
						// moves the other by the same amount.
						...(current && input.startingBalance !== undefined
							? { balance: current.balance - current.startingBalance + input.startingBalance }
							: {}),
					}),
				},
			}),
			(answer) => local.saveAccount(answer.account),
		);
	},
	/**
	 * Deleting an account takes its transactions with it, which is why the API
	 * answers 409 until the caller repeats the request with the flag. That
	 * confirmation is a question about data this client already holds, so it is
	 * still asked here — only the request that follows it is queued.
	 */
	deleteAccount: (id: string, includeTransactions = false) =>
		saved(
			includeTransactions
				? queued<void>({
						method: 'DELETE',
						path: `/api/accounts/${id}${queryString({ includeTransactions: 'true' })}`,
						entity: 'account',
						id,
						optimistic: undefined,
					})
				: request<void>(`/accounts/${id}`, { method: 'DELETE' }),
			() => local.deleteAccount(id),
		),
	adjustAccount: (id: string, input: { balance: number; occurredOn: string; payee?: string; notes?: string }) => {
		const payload = { ...input, id: newId('transaction') };
		return saved(
			queued({
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
			}),
			(answer) => local.saveTransactions([answer.transaction]),
		);
	},

	listCategories: (includeArchived = false) =>
		request<{ categories: Category[] }>(`/categories${queryString({ includeArchived: String(includeArchived) })}`),
	createCategory: (input: Partial<Category>) => {
		const payload = { ...input, id: newId('category') };
		return saved(
			queued({
				method: 'POST',
				path: '/api/categories',
				body: payload,
				optimistic: {
					category: provisionalCategory(ledgerView(), { ...payload, name: payload.name ?? '', kind: payload.kind ?? 'expense' }),
				},
			}),
			(answer) => local.saveCategory(answer.category),
		);
	},
	updateCategory: (id: string, input: Partial<Category>) => {
		const ledger = ledgerView();
		const current = ledger.categories.find((category) => category.id === id);
		return saved(
			queued({
				method: 'PATCH',
				path: `/api/categories/${id}`,
				body: input,
				entity: 'category',
				id,
				optimistic: {
					category: patched(current ?? provisionalCategory(ledger, { id, name: input.name ?? '', kind: input.kind ?? 'expense' }), input),
				},
			}),
			(answer) => local.saveCategory(answer.category),
		);
	},
	deleteCategory: (id: string) =>
		saved(queued<void>({ method: 'DELETE', path: `/api/categories/${id}`, entity: 'category', id, optimistic: undefined }), () =>
			local.deleteCategory(id),
		),

	listTags: () => request<{ tags: Tag[] }>('/tags'),
	createTag: (input: Partial<Tag>) => {
		const payload = { ...input, id: newId('tag') };
		return saved(
			queued({
				method: 'POST',
				path: '/api/tags',
				body: payload,
				optimistic: { tag: provisionalTag({ id: payload.id, name: payload.name ?? '', color: payload.color }) },
			}),
			(answer) => local.saveTag(answer.tag),
		);
	},
	updateTag: (id: string, input: Partial<Tag>) => {
		const current = ledgerView().tags.find((tag) => tag.id === id);
		return saved(
			queued({
				method: 'PATCH',
				path: `/api/tags/${id}`,
				body: input,
				entity: 'tag',
				id,
				optimistic: { tag: patched(current ?? provisionalTag({ id, name: input.name ?? '' }), input) },
			}),
			(answer) => local.saveTag(answer.tag),
		);
	},
	deleteTag: (id: string) =>
		saved(queued<void>({ method: 'DELETE', path: `/api/tags/${id}`, entity: 'tag', id, optimistic: undefined }), () => local.deleteTag(id)),

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

		return saved(
			queued({
				method: 'POST',
				path: '/api/transactions',
				body: { ...input, id: draft.id, roundUpIds },
				optimistic: {
					transaction: provisionalTransaction(ledger, draft),
					// The destination leg is what the interface surfaces as feedback.
					roundUp: roundUp?.destination ?? null,
				},
			}),
			// Both legs of the round-up: the source leg was drawn here, and the server adopts its name.
			(answer) => local.saveTransactions([answer.transaction, ...(answer.roundUp && roundUp ? [roundUp.source, answer.roundUp] : [])]),
		);
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

		return saved(
			queued({
				method: 'PATCH',
				path: `/api/transactions/${id}`,
				body: input,
				entity: 'transaction',
				id,
				optimistic: { transaction: provisionalTransaction(ledger, merged) },
			}),
			(answer) => local.saveTransactions([answer.transaction]),
		);
	},
	deleteTransaction: (id: string) =>
		saved(queued<void>({ method: 'DELETE', path: `/api/transactions/${id}`, entity: 'transaction', id, optimistic: undefined }), () =>
			local.deleteTransaction(id),
		),
	createTransfer: (input: Record<string, unknown>) => {
		const ids = { transferId: newId('transfer'), fromId: newId('transaction'), toId: newId('transaction') };

		return saved(
			queued({
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
			}),
			(answer) => local.saveTransactions(answer.transactions),
		);
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

		return saved(
			queued({
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
			}),
			(answer) => local.saveTransactions(answer.transactions),
		);
	},

	listSubscriptions: () => request<{ subscriptions: Subscription[] }>('/subscriptions'),
	createSubscription: (input: Record<string, unknown>) => {
		const id = newId('subscription');
		return saved(
			queued({
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
			}),
			(answer) => local.saveSubscription(answer.subscription),
		);
	},
	updateSubscription: (id: string, input: Record<string, unknown>) => {
		const ledger = ledgerView();
		const current = ledger.subscription(id);
		return saved(
			queued({
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
			}),
			(answer) => local.saveSubscription(answer.subscription),
		);
	},
	deleteSubscription: (id: string) =>
		saved(queued<void>({ method: 'DELETE', path: `/api/subscriptions/${id}`, entity: 'subscription', id, optimistic: undefined }), () =>
			local.deleteSubscription(id),
		),

	listBudgets: (month: string) => request<{ budgets: Budget[] }>(`/budgets${queryString({ month })}`),
	/**
	 * An upsert: a category carries one plan, and setting it again replaces it.
	 * The id is only used when there is no plan yet, so a queued create and a
	 * queued change are the same call.
	 */
	setBudget: (input: { categoryId: string; month: string; amount: number } | { categoryId: string; month: string; percent: number }) => {
		const id = newId('budget');
		const isPercent = 'percent' in input;
		return saved(
			queued({
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
			}),
			(answer) => local.saveBudget(answer.budget, input.month),
		);
	},
	deleteBudget: (id: string) =>
		saved(queued<void>({ method: 'DELETE', path: `/api/budgets/${id}`, entity: 'budget', id, optimistic: undefined }), () =>
			local.deleteBudget(id),
		),

	getIncomePlan: (month: string) => request<{ incomePlan: IncomePlan }>(`/income-plan${queryString({ month })}`),
	setIncomePlan: (input: { month: string; amount: number; mode?: 'gross' | 'fixed'; grossAmount?: number }) =>
		saved(
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
			(answer) => local.saveIncomePlan(answer.incomePlan, input.month),
		),

	listSecurities: () => request<{ securities: Security[] }>('/securities'),
	prices: (symbol: string, from?: string) =>
		request<{ prices: PricePoint[] }>(`/securities/${encodeURIComponent(symbol)}/prices${queryString({ from })}`),
	listTrades: () => request<{ trades: Trade[] }>('/trades'),
	/**
	 * A trade and its cash leg, both named here so the server adopts them: the leg is an ordinary
	 * transaction in the broker account, the stock as its payee, and moves its balance at once.
	 */
	createTrade: (input: {
		accountId: string;
		symbol: string;
		name: string | null;
		kind: TradeKind;
		occurredOn: string;
		shares: number;
		price: number;
		fees: number;
	}) => {
		const ledger = ledgerView();
		const id = newId('trade');
		const transactionId = newId('transaction');
		const amount = tradeCash(input.kind, input.shares, input.price, input.fees);
		const now = new Date().toISOString();
		const verb = `${input.kind[0]!.toUpperCase()}${input.kind.slice(1)}`;
		const leg = provisionalTransaction(ledger, {
			id: transactionId,
			accountId: input.accountId,
			categoryId: null,
			amount,
			occurredOn: input.occurredOn,
			payee: input.symbol,
			notes: `${verb} ${input.shares.toLocaleString('en-US')} ${input.symbol}`,
		});
		const { name, ...body } = input;
		const trade: Trade = {
			...body,
			id,
			name,
			accountName: leg.accountName,
			amount,
			transactionId,
			createdAt: now,
			updatedAt: now,
		};
		return saved(queued({ method: 'POST', path: '/api/trades', body: { ...body, id, transactionId }, optimistic: { trade } }), (answer) =>
			local.saveTrade(answer.trade, leg),
		);
	},
	deleteTrade: (id: string) =>
		saved(queued<void>({ method: 'DELETE', path: `/api/trades/${id}`, optimistic: undefined }), () => local.deleteTrade(id)),

	summary: (month: string) => request<Summary>(`/summary${queryString({ month })}`),

	trend: (month: string, months: number) => request<Trend>(`/trend${queryString({ month, months })}`),
};
