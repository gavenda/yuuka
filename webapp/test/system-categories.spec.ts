import { env } from 'cloudflare:test';
import { beforeEach, describe, expect, it } from 'vitest';
import { authedClient, json, makeAccount, type Call } from './helpers';

let call: Call;
let broker: string;

interface Category {
	id: string;
	name: string;
	kind: string;
	parentId: string | null;
	system: string | null;
}

const TODAY = new Date().toISOString().slice(0, 10);

beforeEach(async () => {
	// An `as_of` far ahead keeps the routes from asking PHISIX for anything.
	await env.DB.prepare(
		`INSERT OR REPLACE INTO securities (symbol, name, price_e4, change_bp, as_of) VALUES ('BDO', 'BDO Unibank, Inc.', 1200000, 51, '2999-12-31')`,
	).run();
	call = await authedClient();
	broker = await makeAccount(call, { name: 'Dragonfi', startingBalance: 10_000_000 });
});

const categories = async () => (await json<{ categories: Category[] }>(await call('/categories?includeArchived=true'))).categories;
const system = async (key: string) => (await categories()).find((category) => category.system === key)!;
const patch = (id: string, body: Record<string, unknown>) => call(`/categories/${id}`, { method: 'PATCH', body: JSON.stringify(body) });

const trade = (kind: string, body: Record<string, unknown> = {}) =>
	call('/trades', {
		method: 'POST',
		body: JSON.stringify({
			accountId: broker,
			symbol: 'BDO',
			kind,
			occurredOn: TODAY,
			shares: 100,
			price: 1_180_000,
			fees: 3_000,
			...body,
		}),
	});

const legCategory = async (transactionId: string) =>
	(await env.DB.prepare('SELECT category_id FROM transactions WHERE id = ?').bind(transactionId).first<{ category_id: string | null }>())!
		.category_id;

describe('system categories', () => {
	it('are provisioned at the top level: an Investments for transfers, and one for income', async () => {
		const investments = await system('investments');
		const income = await system('investment_income');

		expect(investments).toMatchObject({ name: 'Investments', kind: 'transfer', parentId: null });
		expect(income).toMatchObject({ name: 'Investments', kind: 'income', parentId: null });
		expect((await categories()).filter((category) => category.system === null).length).toBeGreaterThan(0);
	});

	it('file a buy and a sell under Investments the transfer, a dividend under Investments the income', async () => {
		const investments = await system('investments');
		const income = await system('investment_income');
		const { trade: buy } = await json<{ trade: { transactionId: string } }>(await trade('buy'));
		const { trade: sell } = await json<{ trade: { transactionId: string } }>(await trade('sell', { shares: 50, price: 1_200_000 }));
		const { trade: dividend } = await json<{ trade: { transactionId: string } }>(await trade('dividend', { price: 15_000, fees: 1_500 }));

		expect(await legCategory(buy.transactionId)).toBe(investments.id);
		expect(await legCategory(sell.transactionId)).toBe(investments.id);
		expect(await legCategory(dividend.transactionId)).toBe(income.id);
	});

	it('move a trade’s cash to the other one when an edit changes its kind', async () => {
		const income = await system('investment_income');
		const { trade: buy } = await json<{ trade: { id: string; transactionId: string } }>(await trade('buy'));
		await trade('buy', { shares: 10 });

		const response = await call(`/trades/${buy.id}`, {
			method: 'PATCH',
			body: JSON.stringify({ accountId: broker, symbol: 'BDO', kind: 'dividend', occurredOn: TODAY, shares: 10, price: 15_000, fees: 150 }),
		});

		expect(response.status).toBe(200);
		expect(await legCategory(buy.transactionId)).toBe(income.id);
	});

	it('count a dividend under its income category in the summary', async () => {
		const income = await system('investment_income');
		await trade('dividend', { price: 15_000, fees: 1_500 });

		const { categories: breakdown } = await json<{ categories: { categoryId: string; actual: number }[] }>(
			await call(`/summary?month=${TODAY.slice(0, 7)}`),
		);

		expect(breakdown.find((entry) => entry.categoryId === income.id)!.actual).toBe(13_500);
	});

	it('cannot be renamed, recoloured, archived or deleted', async () => {
		for (const key of ['investments', 'investment_income']) {
			const category = await system(key);
			expect((await patch(category.id, { name: 'Stocks' })).status).toBe(409);
			expect((await patch(category.id, { color: '#2a78d6' })).status).toBe(409);
			expect((await patch(category.id, { archived: true })).status).toBe(409);
			expect((await call(`/categories/${category.id}`, { method: 'DELETE' })).status).toBe(409);
			expect(await system(key)).toMatchObject({ id: category.id, name: 'Investments' });
		}
	});

	it('take no subcategories', async () => {
		const investments = await system('investments');
		const response = await call('/categories', {
			method: 'POST',
			body: JSON.stringify({ name: 'Stocks', kind: 'transfer', color: '#2a78d6', parentId: investments.id }),
		});

		expect(response.status).toBe(409);
	});

	it('leave Cashflow an ordinary category', async () => {
		const cashflow = (await categories()).find((category) => category.name === 'Cashflow')!;

		expect((await patch(cashflow.id, { archived: true })).status).toBe(200);
		expect((await call(`/categories/${cashflow.id}`, { method: 'DELETE' })).status).toBe(204);
		expect(await system('investments')).toMatchObject({ name: 'Investments' });
	});

	it('leave the other categories as they were', async () => {
		const savings = (await categories()).find((category) => category.name === 'Savings')!;

		expect((await patch(savings.id, { name: 'Rainy day' })).status).toBe(200);
		expect((await call(`/categories/${savings.id}`, { method: 'DELETE' })).status).toBe(204);
	});

	it('answer another person’s id with a 404, not a 409', async () => {
		const response = await call('/categories/cat_nobodys', { method: 'DELETE' });
		expect(response.status).toBe(404);
	});
});
