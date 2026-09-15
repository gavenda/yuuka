import { applyD1Migrations, env, type D1Migration } from 'cloudflare:test';
import { beforeEach } from 'vitest';

const migrations = (env as unknown as { TEST_MIGRATIONS: D1Migration[] }).TEST_MIGRATIONS;

await applyD1Migrations(env.DB, migrations);

/** Removes every key in a namespace, paging through the listing. */
async function clearNamespace(namespace: KVNamespace): Promise<void> {
	let cursor: string | undefined;
	do {
		const page = await namespace.list({ cursor });
		await Promise.all(page.keys.map((key) => namespace.delete(key.name)));
		cursor = page.list_complete ? undefined : page.cursor;
	} while (cursor);
}

// This plugin shares one Miniflare instance across the tests in a file, so each
// test starts by emptying the database and the cache itself. Deletion order
// respects the foreign keys D1 enforces.
beforeEach(async () => {
	await env.DB.batch([
		env.DB.prepare('DELETE FROM transactions'),
		env.DB.prepare('DELETE FROM budgets'),
		env.DB.prepare('DELETE FROM categories'),
		env.DB.prepare('DELETE FROM accounts'),
		env.DB.prepare('DELETE FROM users'),
	]);

	await clearNamespace(env.CACHE);
});
