import { env } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';
import { DEFAULT_CATEGORY_COUNT, DEMO_ACCOUNTS, DEMO_BUDGETS, DEMO_TRANSACTIONS } from '../server/defaults';
import { ensureUser } from '../server/users';
import { currentMonth } from '../server/dates';

/**
 * The rest of the suite runs with `SEED_DEMO_DATA` off so specs can assert on
 * rows they created themselves. Provisioning is therefore exercised here, by
 * calling it directly with the flag set either way.
 */
function envWith(overrides: Record<string, string>): Env {
	// `env` from `cloudflare:test` does not survive a spread — its bindings are
	// not own enumerable properties — so the copy is assembled by hand.
	return {
		DB: env.DB,
		CACHE: env.CACHE,
		AUTH0_DOMAIN: env.AUTH0_DOMAIN,
		AUTH0_AUDIENCE: env.AUTH0_AUDIENCE,
		...overrides,
	} as unknown as Env;
}

const seeding = envWith({ SEED_DEMO_DATA: 'true' });
const categoriesOnly = envWith({ SEED_DEMO_DATA: 'false' });

async function countsFor(subject: string) {
	const [categories, accounts, transactions, budgets] = await env.DB.batch<{ n: number }>([
		env.DB.prepare('SELECT COUNT(*) AS n FROM categories WHERE user_id = ?').bind(subject),
		env.DB.prepare('SELECT COUNT(*) AS n FROM accounts WHERE user_id = ?').bind(subject),
		env.DB.prepare('SELECT COUNT(*) AS n FROM transactions WHERE user_id = ?').bind(subject),
		env.DB.prepare('SELECT COUNT(*) AS n FROM budgets WHERE user_id = ?').bind(subject),
	]);

	return {
		categories: categories.results[0].n,
		accounts: accounts.results[0].n,
		transactions: transactions.results[0].n,
		budgets: budgets.results[0].n,
	};
}

describe('first sign-in', () => {
	it('provisions categories and the sample data in one go', async () => {
		await ensureUser(seeding, 'auth0|newcomer');

		expect(await countsFor('auth0|newcomer')).toEqual({
			categories: DEFAULT_CATEGORY_COUNT,
			accounts: DEMO_ACCOUNTS.length,
			transactions: DEMO_TRANSACTIONS.length,
			budgets: DEMO_BUDGETS.length,
		});
	});

	it('dates the sample transactions into the month the user joined', async () => {
		await ensureUser(seeding, 'auth0|dated');

		const { results } = await env.DB.prepare('SELECT occurred_on FROM transactions WHERE user_id = ?')
			.bind('auth0|dated')
			.all<{ occurred_on: string }>();

		expect(results).toHaveLength(DEMO_TRANSACTIONS.length);
		for (const row of results) {
			expect(row.occurred_on.slice(0, 7)).toBe(currentMonth());
		}
	});

	it('points the sample rows at the same user’s own accounts and categories', async () => {
		await ensureUser(seeding, 'auth0|linked');

		// A mismatched id here would mean a transaction referencing a row that is
		// not the owner's — exactly what the ownership model forbids.
		const orphans = await env.DB.prepare(
			`SELECT COUNT(*) AS n FROM transactions t
			 WHERE t.user_id = ?
			   AND (NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.account_id AND a.user_id = t.user_id)
			     OR NOT EXISTS (SELECT 1 FROM categories c WHERE c.id = t.category_id AND c.user_id = t.user_id))`,
		)
			.bind('auth0|linked')
			.first<{ n: number }>();

		expect(orphans?.n).toBe(0);

		const budgetOrphans = await env.DB.prepare(
			`SELECT COUNT(*) AS n FROM budgets b
			 WHERE b.user_id = ?
			   AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.id = b.category_id AND c.user_id = b.user_id)`,
		)
			.bind('auth0|linked')
			.first<{ n: number }>();

		expect(budgetOrphans?.n).toBe(0);
	});

	it('produces a dashboard with figures on it', async () => {
		await ensureUser(seeding, 'auth0|figures');

		const worth = await env.DB.prepare(
			`SELECT SUM(a.starting_balance) + COALESCE((SELECT SUM(amount) FROM transactions WHERE user_id = ?), 0) AS total
			 FROM accounts a WHERE a.user_id = ?`,
		)
			.bind('auth0|figures', 'auth0|figures')
			.first<{ total: number }>();

		const expected =
			DEMO_ACCOUNTS.reduce((sum, account) => sum + account.startingBalance, 0) +
			DEMO_TRANSACTIONS.reduce((sum, transaction) => sum + transaction.amount, 0);

		expect(worth?.total).toBe(expected);
		expect(worth?.total).toBeGreaterThan(0);
	});

	it('runs once, however many requests arrive', async () => {
		await ensureUser(seeding, 'auth0|repeat');
		const first = await countsFor('auth0|repeat');

		await ensureUser(seeding, 'auth0|repeat');
		await ensureUser(seeding, 'auth0|repeat');

		expect(await countsFor('auth0|repeat')).toEqual(first);
	});

	it('survives concurrent first requests without double-provisioning', async () => {
		await Promise.all([ensureUser(seeding, 'auth0|racer'), ensureUser(seeding, 'auth0|racer'), ensureUser(seeding, 'auth0|racer')]);

		expect(await countsFor('auth0|racer')).toEqual({
			categories: DEFAULT_CATEGORY_COUNT,
			accounts: DEMO_ACCOUNTS.length,
			transactions: DEMO_TRANSACTIONS.length,
			budgets: DEMO_BUDGETS.length,
		});
	});

	it('gives each user their own copy', async () => {
		await ensureUser(seeding, 'auth0|one');
		await ensureUser(seeding, 'auth0|two');

		const shared = await env.DB.prepare(
			`SELECT COUNT(*) AS n FROM accounts a
			 JOIN accounts b ON a.id = b.id AND a.user_id <> b.user_id`,
		).first<{ n: number }>();

		expect(shared?.n).toBe(0);
		expect(await countsFor('auth0|one')).toEqual(await countsFor('auth0|two'));
	});
});

describe('with SEED_DEMO_DATA off', () => {
	it('provisions the categories and nothing else', async () => {
		await ensureUser(categoriesOnly, 'auth0|minimal');

		expect(await countsFor('auth0|minimal')).toEqual({
			categories: DEFAULT_CATEGORY_COUNT,
			accounts: 0,
			transactions: 0,
			budgets: 0,
		});
	});
});
