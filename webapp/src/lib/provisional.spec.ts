import { describe, expect, it } from 'vitest';
import {
	patched,
	provisionalAccount,
	provisionalAdjustment,
	provisionalCategory,
	provisionalRoundUp,
	provisionalTransaction,
	provisionalTransfer,
	roundUpFor,
	type LedgerView,
} from './provisional';
import { parseSlices, slicesForPath } from './slices';
import type { Account, Category, RoundUpRule, Tag } from '@/types';

/**
 * What the screen is told before the server has said anything.
 *
 * The figures here are guesses, corrected on the next refresh — but they are
 * the figures the user reads for the seconds in between, so the ones that have
 * to match what the server would have said are the ones worth pinning down:
 * the round-up, the balance an adjustment posts, and a subcategory's kind.
 */

const account = (overrides: Partial<Account> = {}): Account => ({
	id: 'acc_1',
	name: 'Checking',
	typeId: 'atp_1',
	typeName: 'Checking',
	currency: 'PHP',
	logoUrl: null,
	logoInvertDark: false,
	roundUpSource: false,
	startingBalance: 0,
	balance: 100_00,
	archived: false,
	createdAt: '2026-01-01T00:00:00.000Z',
	updatedAt: '2026-01-01T00:00:00.000Z',
	...overrides,
});

const category = (overrides: Partial<Category> = {}): Category => ({
	id: 'cat_1',
	name: 'Groceries',
	kind: 'expense',
	color: '#64748b',
	sortOrder: 0,
	archived: false,
	parentId: null,
	createdAt: '2026-01-01T00:00:00.000Z',
	updatedAt: '2026-01-01T00:00:00.000Z',
	...overrides,
});

const rule = (overrides: Partial<RoundUpRule> = {}): RoundUpRule => ({
	enabled: true,
	roundTo: 1000,
	destinationAccountId: 'acc_savings',
	categoryId: 'cat_cashflow',
	createdAt: null,
	updatedAt: null,
	...overrides,
});

function view(overrides: Partial<LedgerView> = {}): LedgerView {
	return {
		accounts: [account({ id: 'acc_1', roundUpSource: true }), account({ id: 'acc_savings', name: 'Savings', balance: 0 })],
		accountTypes: [{ id: 'atp_1', name: 'Checking', sortOrder: 0, archived: false, accountCount: 1, createdAt: '', updatedAt: '' }],
		categories: [category(), category({ id: 'cat_cashflow', name: 'Cashflow', kind: 'transfer' })],
		tags: [],
		roundUpRule: rule(),
		settings: null,
		displayCurrency: 'PHP',
		transaction: () => null,
		subscription: () => null,
		...overrides,
	};
}

const draft = {
	id: 'txn_1',
	accountId: 'acc_1',
	categoryId: 'cat_1',
	amount: -1250,
	occurredOn: '2026-03-04',
	payee: 'Bakery',
	notes: '',
};

const ids = { transferId: 'tfr_1', fromId: 'txn_a', toId: 'txn_b' };

describe('roundUpFor', () => {
	it('is the gap to the next multiple up', () => {
		expect(roundUpFor(-1250, 1000)).toBe(750);
		expect(roundUpFor(-12_345, 10_000)).toBe(7655);
	});

	it('is nothing when the amount already sits on a multiple', () => {
		expect(roundUpFor(-2000, 1000)).toBe(0);
	});

	it('is nothing for income — only a purchase rounds up', () => {
		expect(roundUpFor(4500, 1000)).toBe(0);
	});
});

describe('a provisional transaction', () => {
	it('fills in the labels the API would have joined', () => {
		const result = provisionalTransaction(view(), draft);

		expect(result.accountName).toBe('Checking');
		expect(result.categoryName).toBe('Groceries');
		expect(result.categoryColor).toBe('#64748b');
	});

	it('moves the running balance by its own amount', () => {
		expect(provisionalTransaction(view(), draft).runningBalance).toBe(100_00 - 1250);
	});

	it('is never automated — a schedule posts those, and a schedule is the server', () => {
		expect(provisionalTransaction(view(), draft).automated).toBe(false);
	});

	it('carries the chips for the tags it names, in name order', () => {
		const tags: Tag[] = [
			{ id: 'tag_z', name: 'Zurich', color: '#111111', transactionCount: 0, createdAt: '', updatedAt: '' },
			{ id: 'tag_a', name: 'Amsterdam', color: '#222222', transactionCount: 0, createdAt: '', updatedAt: '' },
		];
		const result = provisionalTransaction(view({ tags }), { ...draft, tagIds: ['tag_z', 'tag_a'] });

		expect(result.tags.map((tag) => tag.name)).toEqual(['Amsterdam', 'Zurich']);
	});
});

describe('a provisional round-up', () => {
	it('posts both legs of the transfer under the ids it was given', () => {
		const result = provisionalRoundUp(view(), draft, ids);

		expect(result?.source.id).toBe('txn_a');
		expect(result?.destination.id).toBe('txn_b');
		expect(result?.source.amount).toBe(-750);
		expect(result?.destination.amount).toBe(750);
		expect(result?.destination.accountId).toBe('acc_savings');
	});

	it('gives both legs the rule’s category — a round-up is a transfer', () => {
		const result = provisionalRoundUp(view(), draft, ids);
		expect(result?.source.categoryId).toBe('cat_cashflow');
		expect(result?.destination.categoryId).toBe('cat_cashflow');
	});

	it('does not trigger on an account that has not opted in', () => {
		const ledger = view({ accounts: [account({ id: 'acc_1', roundUpSource: false }), account({ id: 'acc_savings' })] });
		expect(provisionalRoundUp(ledger, draft, ids)).toBeNull();
	});

	it('does not trigger on a purchase made from the destination itself', () => {
		expect(provisionalRoundUp(view(), { ...draft, accountId: 'acc_savings' }, ids)).toBeNull();
	});

	it('does not trigger when the rule is off', () => {
		expect(provisionalRoundUp(view({ roundUpRule: rule({ enabled: false }) }), draft, ids)).toBeNull();
	});

	it('does not trigger on income', () => {
		expect(provisionalRoundUp(view(), { ...draft, amount: 4500 }, ids)).toBeNull();
	});
});

describe('a provisional transfer', () => {
	it('writes one negative and one positive leg sharing a transfer id', () => {
		const [out, into] = provisionalTransfer(view(), {
			ids,
			fromAccountId: 'acc_1',
			toAccountId: 'acc_savings',
			categoryId: 'cat_cashflow',
			amount: 5000,
			occurredOn: '2026-03-04',
			payee: '',
			notes: '',
		});

		expect(out.amount).toBe(-5000);
		expect(into.amount).toBe(5000);
		expect(out.transferId).toBe('tfr_1');
		expect(into.transferId).toBe('tfr_1');
	});

	it('names itself when the payee is blank, the way the API composes it', () => {
		const [out] = provisionalTransfer(view(), {
			ids,
			fromAccountId: 'acc_1',
			toAccountId: 'acc_savings',
			categoryId: null,
			amount: 5000,
			occurredOn: '2026-03-04',
			payee: '',
			notes: '',
		});

		expect(out.payee).toBe('Checking → Savings');
	});
});

describe('a provisional adjustment', () => {
	it('posts the difference to the balance asked for, not the balance itself', () => {
		const result = provisionalAdjustment(view(), 'acc_1', {
			id: 'txn_adj',
			balance: 150_00,
			occurredOn: '2026-03-04',
			payee: '',
			notes: '',
		});

		expect(result.amount).toBe(50_00);
		// Nothing distinguishes it from a typed row; that is the point.
		expect(result.categoryId).toBeNull();
		expect(result.automated).toBe(false);
	});

	it('names itself when nothing was typed', () => {
		const result = provisionalAdjustment(view(), 'acc_1', { id: 'txn_adj', balance: 1, occurredOn: '2026-03-04', payee: '', notes: '' });
		expect(result.payee).toBe('Balance adjustment');
	});
});

describe('a provisional category', () => {
	it('inherits its parent’s kind, so it cannot show one kind and save another', () => {
		const result = provisionalCategory(view(), { id: 'cat_new', name: 'Investments', kind: 'expense', parentId: 'cat_cashflow' });
		expect(result.kind).toBe('transfer');
	});

	it('keeps its own kind at the top level', () => {
		const result = provisionalCategory(view(), { id: 'cat_new', name: 'Fuel', kind: 'expense', parentId: null });
		expect(result.kind).toBe('expense');
	});
});

describe('a provisional account', () => {
	it('opens at the balance it was started with — nothing has posted to it yet', () => {
		const result = provisionalAccount(view(), { id: 'acc_new', name: 'Wallet', typeId: 'atp_1', startingBalance: 2500 });
		expect(result.balance).toBe(2500);
		expect(result.typeName).toBe('Checking');
	});
});

describe('patched', () => {
	it('applies the change and moves the timestamp', () => {
		const before = category({ updatedAt: '2020-01-01T00:00:00.000Z' });
		const after = patched(before, { name: 'Food' });

		expect(after.name).toBe('Food');
		expect(after.updatedAt).not.toBe(before.updatedAt);
	});
});

describe('slices', () => {
	it('knows a transaction moves balances and the month’s summary, not just the list', () => {
		expect(slicesForPath('/api/transactions')).toEqual(['transactions', 'accounts', 'summary', 'payees']);
	});

	it('treats an adjustment as the transaction it is', () => {
		expect(slicesForPath('/api/accounts/acc_1/adjust')).toContain('transactions');
	});

	it('says nothing for a path that changes nothing shared', () => {
		expect(slicesForPath('/api/auth/me')).toEqual([]);
	});

	it('ignores slice names it does not recognise, so an old client cannot be confused by a new one', () => {
		expect(parseSlices('accounts,wobble,summary')).toEqual(['accounts', 'summary']);
		expect(parseSlices(undefined)).toEqual([]);
	});
});
