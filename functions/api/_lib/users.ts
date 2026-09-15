import { currentMonth } from './dates';
import { DEFAULT_ACCOUNT_TYPES, DEFAULT_CATEGORIES, DEMO_ACCOUNTS, DEMO_BUDGETS, DEMO_TRANSACTIONS } from './defaults';
import { stableId } from './ids';

/** Sample data is on unless explicitly switched off. */
function seedsDemoData(env: Env): boolean {
	return (env.SEED_DEMO_DATA ?? 'true').trim().toLowerCase() !== 'false';
}

/**
 * Provisions a signed-in subject the first time the API sees them: a `users`
 * row, the default account types and categories, and — unless `SEED_DEMO_DATA`
 * is `"false"` — a set of sample accounts, transactions and budgets so the
 * first dashboard has something on it.
 *
 * The account types are a starting vocabulary, not a fixed list: the user owns
 * them from here on and may rename, extend or retire any of them.
 *
 * Every id is derived from the subject rather than random, and every statement
 * is `INSERT OR IGNORE`, so the whole thing is idempotent. That matters because
 * the first page load fires several requests at once: they race here, and a
 * second run has to resolve to the same rows and do nothing rather than insert
 * duplicates or reference rows its own batch failed to write.
 */
export async function ensureUser(env: Env, subject: string): Promise<void> {
	const db = env.DB;

	// Steady state is this read alone; the writes below run once per user, ever.
	const existing = await db.prepare('SELECT 1 AS present FROM users WHERE id = ?').bind(subject).first<{ present: number }>();
	if (existing) return;

	const categoryIds = new Map(
		await Promise.all(
			DEFAULT_CATEGORIES.map(async (category) => [category.name, await stableId('cat', subject, category.kind, category.name)] as const),
		),
	);

	// Children are keyed by parent as well as name, so two parents may each have
	// an "Other" without colliding.
	const childIds = new Map(
		await Promise.all(
			DEFAULT_CATEGORIES.flatMap((parent) =>
				(parent.children ?? []).map(
					async (child) => [`${parent.name}/${child}`, await stableId('cat', subject, parent.kind, parent.name, child)] as const,
				),
			),
		),
	);

	const typeIds = new Map(
		await Promise.all(DEFAULT_ACCOUNT_TYPES.map(async (type) => [type.name, await stableId('atp', subject, type.name)] as const)),
	);

	const statements = [
		db.prepare('INSERT OR IGNORE INTO users (id) VALUES (?)').bind(subject),

		// Types come before accounts: the sample accounts reference them, and an
		// account cannot exist without one.
		...DEFAULT_ACCOUNT_TYPES.map((type) =>
			db
				.prepare('INSERT OR IGNORE INTO account_types (id, user_id, name, sort_order) VALUES (?, ?, ?, ?)')
				.bind(typeIds.get(type.name)!, subject, type.name, type.sortOrder),
		),

		...DEFAULT_CATEGORIES.map((category) =>
			db
				.prepare(
					`INSERT OR IGNORE INTO categories (id, user_id, name, kind, color, sort_order, applies_to)
					 VALUES (?, ?, ?, ?, ?, ?, ?)`,
				)
				.bind(
					categoryIds.get(category.name)!,
					subject,
					category.name,
					category.kind,
					category.color,
					category.sortOrder,
					category.appliesTo ?? 'standard',
				),
		),

		// Children come after their parents in the same batch, so the foreign key
		// is satisfied by the time they land.
		...DEFAULT_CATEGORIES.flatMap((parent) =>
			(parent.children ?? []).map((child, index) =>
				db
					.prepare(
						`INSERT OR IGNORE INTO categories (id, user_id, name, kind, color, sort_order, applies_to, parent_id)
						 VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
					)
					.bind(
						childIds.get(`${parent.name}/${child}`)!,
						subject,
						child,
						parent.kind,
						parent.color,
						index,
						parent.appliesTo ?? 'standard',
						categoryIds.get(parent.name)!,
					),
			),
		),
	];

	if (seedsDemoData(env)) {
		// Dated into the month the user joined, so the dashboard they land on is
		// the one with the sample activity in it.
		const month = currentMonth();

		const accountIds = new Map(
			await Promise.all(DEMO_ACCOUNTS.map(async (account) => [account.slug, await stableId('acc', subject, account.slug)] as const)),
		);

		const transactions = await Promise.all(
			DEMO_TRANSACTIONS.map(async (transaction) => ({
				...transaction,
				id: await stableId('txn', subject, transaction.accountSlug, transaction.payee, String(transaction.dayOfMonth)),
			})),
		);

		const budgets = await Promise.all(
			DEMO_BUDGETS.map(async (budget) => ({ ...budget, id: await stableId('bdg', subject, budget.categoryName, month) })),
		);

		statements.push(
			...DEMO_ACCOUNTS.map((account) =>
				db
					.prepare(
						`INSERT OR IGNORE INTO accounts (id, user_id, name, type_id, currency, starting_balance)
						 VALUES (?, ?, ?, ?, ?, ?)`,
					)
					.bind(
						accountIds.get(account.slug)!,
						subject,
						account.name,
						typeIds.get(account.typeName)!,
						account.currency,
						account.startingBalance,
					),
			),

			...transactions.map((transaction) =>
				db
					.prepare(
						`INSERT OR IGNORE INTO transactions (id, user_id, account_id, category_id, amount, occurred_on, payee)
						 VALUES (?, ?, ?, ?, ?, ?, ?)`,
					)
					.bind(
						transaction.id,
						subject,
						accountIds.get(transaction.accountSlug)!,
						categoryIds.get(transaction.categoryName)!,
						transaction.amount,
						`${month}-${String(transaction.dayOfMonth).padStart(2, '0')}`,
						transaction.payee,
					),
			),

			...budgets.map((budget) =>
				db
					.prepare('INSERT OR IGNORE INTO budgets (id, user_id, category_id, month, amount) VALUES (?, ?, ?, ?, ?)')
					.bind(budget.id, subject, categoryIds.get(budget.categoryName)!, month, budget.amount),
			),
		);
	}

	await db.batch(statements);
}
