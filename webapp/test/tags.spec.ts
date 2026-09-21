import { beforeEach, describe, expect, it } from 'vitest';
import { authedClient, json, makeAccount, otherClient, type Call } from './helpers';

let call: Call;
let accountId: string;

beforeEach(async () => {
	call = await authedClient();
	accountId = await makeAccount(call, { name: 'Checking', startingBalance: 0 });
});

interface Chip {
	id: string;
	name: string;
	color: string;
}

async function makeTag(as: Call, name: string, extra: Record<string, unknown> = {}): Promise<string> {
	const response = await as('/tags', { method: 'POST', body: JSON.stringify({ name, ...extra }) });
	if (response.status !== 201) throw new Error(`Could not create tag ${name}: ${response.status}`);
	return (await json<{ tag: { id: string } }>(response)).tag.id;
}

const post = (body: Record<string, unknown>, as: Call = call) => as('/transactions', { method: 'POST', body: JSON.stringify(body) });
const patch = (id: string, body: Record<string, unknown>) => call(`/transactions/${id}`, { method: 'PATCH', body: JSON.stringify(body) });

async function listing(query = ''): Promise<{ id: string; tags: Chip[] }[]> {
	return (await json<{ transactions: { id: string; tags: Chip[] }[] }>(await call(`/transactions${query}`))).transactions;
}

describe('tags', () => {
	it('starts empty — there is no default set', async () => {
		expect((await json<{ tags: unknown[] }>(await call('/tags'))).tags).toEqual([]);
	});

	it('creates a tag with a default colour', async () => {
		const response = await call('/tags', { method: 'POST', body: JSON.stringify({ name: 'Trip' }) });

		expect(response.status).toBe(201);
		expect((await json<{ tag: Record<string, unknown> }>(response)).tag).toMatchObject({ name: 'Trip', color: '#64748b' });
	});

	it('rejects a blank name and a bad colour', async () => {
		expect((await call('/tags', { method: 'POST', body: JSON.stringify({ name: '  ' }) })).status).toBe(400);
		expect((await call('/tags', { method: 'POST', body: JSON.stringify({ name: 'Trip', color: 'red' }) })).status).toBe(400);
	});

	it('refuses a duplicate name whatever its case', async () => {
		await makeTag(call, 'Trip');

		expect((await call('/tags', { method: 'POST', body: JSON.stringify({ name: 'trip' }) })).status).toBe(409);
	});

	it('lists alphabetically, ignoring case', async () => {
		await makeTag(call, 'work');
		await makeTag(call, 'Bills');
		await makeTag(call, 'Trip');

		const { tags } = await json<{ tags: { name: string }[] }>(await call('/tags'));
		expect(tags.map((tag) => tag.name)).toEqual(['Bills', 'Trip', 'work']);
	});

	it('renames and recolours, and refuses to rename onto another tag', async () => {
		const trip = await makeTag(call, 'Trip');
		await makeTag(call, 'Work');

		const renamed = await call(`/tags/${trip}`, { method: 'PATCH', body: JSON.stringify({ name: 'Holiday', color: '#eb6834' }) });
		expect(renamed.status).toBe(200);
		expect((await json<{ tag: Record<string, unknown> }>(renamed)).tag).toMatchObject({ name: 'Holiday', color: '#eb6834' });

		expect((await call(`/tags/${trip}`, { method: 'PATCH', body: JSON.stringify({ name: 'work' }) })).status).toBe(409);
		expect((await call(`/tags/${trip}`, { method: 'PATCH', body: JSON.stringify({}) })).status).toBe(400);
	});

	it('answers 404 for a tag that does not exist', async () => {
		expect((await call('/tags/tag_nope', { method: 'PATCH', body: JSON.stringify({ name: 'X' }) })).status).toBe(404);
		expect((await call('/tags/tag_nope', { method: 'DELETE' })).status).toBe(404);
	});
});

describe('tags on a transaction', () => {
	it('are worn from creation, in name order', async () => {
		const work = await makeTag(call, 'work', { color: '#2a78d6' });
		const trip = await makeTag(call, 'Trip');

		const response = await post({ accountId, amount: -1000, occurredOn: '2026-09-03', tagIds: [work, trip] });
		expect(response.status).toBe(201);

		const { transaction } = await json<{ transaction: { tags: Chip[] } }>(response);
		expect(transaction.tags).toEqual([
			{ id: trip, name: 'Trip', color: '#64748b' },
			{ id: work, name: 'work', color: '#2a78d6' },
		]);
	});

	it('default to none', async () => {
		const { transaction } = await json<{ transaction: { tags: Chip[] } }>(
			await post({ accountId, amount: -1000, occurredOn: '2026-09-03' }),
		);
		expect(transaction.tags).toEqual([]);
	});

	it('collapse a repeated tag', async () => {
		const trip = await makeTag(call, 'Trip');

		const { transaction } = await json<{ transaction: { tags: Chip[] } }>(
			await post({ accountId, amount: -1000, occurredOn: '2026-09-03', tagIds: [trip, trip] }),
		);
		expect(transaction.tags).toHaveLength(1);
	});

	it('take at most ten', async () => {
		const ids = await Promise.all(Array.from({ length: 11 }, (_, index) => makeTag(call, `tag ${index}`)));

		expect((await post({ accountId, amount: -1000, occurredOn: '2026-09-03', tagIds: ids })).status).toBe(400);
		expect((await post({ accountId, amount: -1000, occurredOn: '2026-09-03', tagIds: ids.slice(0, 10) })).status).toBe(201);
	});

	it('come back on the listing', async () => {
		const trip = await makeTag(call, 'Trip');
		await post({ accountId, amount: -1000, occurredOn: '2026-09-03', tagIds: [trip] });
		await post({ accountId, amount: -2000, occurredOn: '2026-09-04' });

		const rows = await listing('?month=2026-09');
		expect(rows.map((row) => row.tags.map((tag) => tag.name))).toEqual([[], ['Trip']]);
	});

	it('are replaced by an edit that names tags, and kept by one that does not', async () => {
		const trip = await makeTag(call, 'Trip');
		const work = await makeTag(call, 'Work');
		const { transaction } = await json<{ transaction: { id: string } }>(
			await post({ accountId, amount: -1000, occurredOn: '2026-09-03', tagIds: [trip] }),
		);

		const kept = await json<{ transaction: { tags: Chip[] } }>(await patch(transaction.id, { notes: 'edited' }));
		expect(kept.transaction.tags.map((tag) => tag.name)).toEqual(['Trip']);

		const replaced = await json<{ transaction: { tags: Chip[] } }>(await patch(transaction.id, { tagIds: [work] }));
		expect(replaced.transaction.tags.map((tag) => tag.name)).toEqual(['Work']);

		const cleared = await json<{ transaction: { tags: Chip[] } }>(await patch(transaction.id, { tagIds: [] }));
		expect(cleared.transaction.tags).toEqual([]);
	});

	it('can be the only thing an edit changes', async () => {
		const trip = await makeTag(call, 'Trip');
		const { transaction } = await json<{ transaction: { id: string; amount: number } }>(
			await post({ accountId, amount: -1000, occurredOn: '2026-09-03' }),
		);

		const response = await patch(transaction.id, { tagIds: [trip] });
		expect(response.status).toBe(200);
		expect((await json<{ transaction: { amount: number } }>(response)).transaction.amount).toBe(-1000);
	});

	it('are refused when unknown, and nothing is saved', async () => {
		expect((await post({ accountId, amount: -1000, occurredOn: '2026-09-03', tagIds: ['tag_nope'] })).status).toBe(400);
		expect(await listing()).toEqual([]);
	});

	it("are refused when they are someone else's", async () => {
		const theirs = await otherClient();
		const stranger = await makeTag(theirs, 'Theirs');

		expect((await post({ accountId, amount: -1000, occurredOn: '2026-09-03', tagIds: [stranger] })).status).toBe(400);

		const { transaction } = await json<{ transaction: { id: string } }>(await post({ accountId, amount: -1000, occurredOn: '2026-09-03' }));
		expect((await patch(transaction.id, { tagIds: [stranger] })).status).toBe(400);
	});

	it('leave every figure alone', async () => {
		const trip = await makeTag(call, 'Trip');
		await post({ accountId, amount: -1000, occurredOn: '2026-09-03', tagIds: [trip] });

		const { accounts } = await json<{ accounts: { balance: number }[] }>(await call('/accounts'));
		expect(accounts[0].balance).toBe(-1000);
	});

	it('can be searched by name', async () => {
		const trip = await makeTag(call, 'Holiday');
		await post({ accountId, amount: -1000, occurredOn: '2026-09-03', payee: 'Ferry', tagIds: [trip] });
		await post({ accountId, amount: -2000, occurredOn: '2026-09-04', payee: 'Bakery' });

		const rows = await listing('?search=holi');
		expect(rows).toHaveLength(1);
		expect(rows[0].tags[0].name).toBe('Holiday');
	});

	it('can be filtered on, matching a transaction that wears any of them', async () => {
		const trip = await makeTag(call, 'Holiday');
		const work = await makeTag(call, 'Work');
		const unused = await makeTag(call, 'Unused');
		await post({ accountId, amount: -1000, occurredOn: '2026-09-03', payee: 'Ferry', tagIds: [trip] });
		await post({ accountId, amount: -2000, occurredOn: '2026-09-04', payee: 'Pens', tagIds: [work] });
		await post({ accountId, amount: -3000, occurredOn: '2026-09-05', payee: 'Bakery' });

		expect(await listing(`?tagId=${trip}`)).toHaveLength(1);
		expect(await listing(`?tagId=${trip},${work}`)).toHaveLength(2);
		expect(await listing(`?tagId=${unused}`)).toHaveLength(0);
	});
});

describe('transaction count', () => {
	const countOf = async (id: string) =>
		(await json<{ tags: { id: string; transactionCount: number }[] }>(await call('/tags'))).tags.find((tag) => tag.id === id)!
			.transactionCount;

	it('is zero for a tag nothing wears', async () => {
		const trip = await makeTag(call, 'Trip');

		expect(
			(await json<{ tag: { transactionCount: number } }>(await call('/tags', { method: 'POST', body: JSON.stringify({ name: 'Fresh' }) })))
				.tag.transactionCount,
		).toBe(0);
		expect(await countOf(trip)).toBe(0);
	});

	it('counts each transaction wearing the tag', async () => {
		const trip = await makeTag(call, 'Trip');
		const work = await makeTag(call, 'Work');
		await post({ accountId, amount: -1000, occurredOn: '2026-09-03', tagIds: [trip, work] });
		await post({ accountId, amount: -2000, occurredOn: '2026-09-04', tagIds: [trip] });
		await post({ accountId, amount: -3000, occurredOn: '2026-09-05' });

		expect(await countOf(trip)).toBe(2);
		expect(await countOf(work)).toBe(1);
	});

	it('counts a transfer once, not once per leg', async () => {
		const trip = await makeTag(call, 'Trip');
		const savingsId = await makeAccount(call, { name: 'Savings' });
		await call('/transactions/transfer', {
			method: 'POST',
			body: JSON.stringify({ fromAccountId: accountId, toAccountId: savingsId, amount: 5000, occurredOn: '2026-09-03', tagIds: [trip] }),
		});
		await post({ accountId, amount: -1000, occurredOn: '2026-09-04', tagIds: [trip] });

		expect(await countOf(trip)).toBe(2);
	});

	it('follows an edit and a delete', async () => {
		const trip = await makeTag(call, 'Trip');
		const { transaction } = await json<{ transaction: { id: string } }>(
			await post({ accountId, amount: -1000, occurredOn: '2026-09-03', tagIds: [trip] }),
		);
		expect(await countOf(trip)).toBe(1);

		await patch(transaction.id, { tagIds: [] });
		expect(await countOf(trip)).toBe(0);

		await patch(transaction.id, { tagIds: [trip] });
		await call(`/transactions/${transaction.id}`, { method: 'DELETE' });
		expect(await countOf(trip)).toBe(0);
	});

	it('only counts your own transactions', async () => {
		const trip = await makeTag(call, 'Trip');
		await post({ accountId, amount: -1000, occurredOn: '2026-09-03', tagIds: [trip] });
		const theirs = await otherClient();
		await makeTag(theirs, 'Trip');

		const { tags } = await json<{ tags: { transactionCount: number }[] }>(await theirs('/tags'));
		expect(tags.map((tag) => tag.transactionCount)).toEqual([0]);
	});
});

describe('tags on a transfer', () => {
	let savingsId: string;

	beforeEach(async () => {
		savingsId = await makeAccount(call, { name: 'Savings' });
	});

	const transfer = (body: Record<string, unknown> = {}) =>
		call('/transactions/transfer', {
			method: 'POST',
			body: JSON.stringify({ fromAccountId: accountId, toAccountId: savingsId, amount: 5000, occurredOn: '2026-09-03', ...body }),
		});

	it('go on both legs', async () => {
		const trip = await makeTag(call, 'Trip');

		const { transactions } = await json<{ transactions: { tags: Chip[] }[] }>(await transfer({ tagIds: [trip] }));
		expect(transactions.map((leg) => leg.tags.map((tag) => tag.name))).toEqual([['Trip'], ['Trip']]);
	});

	it('are replaced on both legs by an edit that names tags, and kept by one that does not', async () => {
		const trip = await makeTag(call, 'Trip');
		const work = await makeTag(call, 'Work');
		const { transferId } = await json<{ transferId: string }>(await transfer({ tagIds: [trip] }));

		const edit = (body: Record<string, unknown>) =>
			call(`/transactions/transfer/${transferId}`, {
				method: 'PATCH',
				body: JSON.stringify({ fromAccountId: accountId, toAccountId: savingsId, amount: 5000, occurredOn: '2026-09-03', ...body }),
			});

		const kept = await json<{ transactions: { tags: Chip[] }[] }>(await edit({ notes: 'edited' }));
		expect(kept.transactions.map((leg) => leg.tags.map((tag) => tag.name))).toEqual([['Trip'], ['Trip']]);

		const replaced = await json<{ transactions: { tags: Chip[] }[] }>(await edit({ tagIds: [work] }));
		expect(replaced.transactions.map((leg) => leg.tags.map((tag) => tag.name))).toEqual([['Work'], ['Work']]);
	});

	it('are not written when the transfer itself is refused', async () => {
		const trip = await makeTag(call, 'Trip');

		expect((await transfer({ tagIds: [trip], toAccountId: 'acc_nope' })).status).toBe(400);
		expect(await listing()).toEqual([]);
	});
});

describe('deleting', () => {
	it('a tag takes the label off but keeps the transaction', async () => {
		const trip = await makeTag(call, 'Trip');
		await post({ accountId, amount: -1000, occurredOn: '2026-09-03', tagIds: [trip] });

		expect((await call(`/tags/${trip}`, { method: 'DELETE' })).status).toBe(204);

		const rows = await listing();
		expect(rows).toHaveLength(1);
		expect(rows[0].tags).toEqual([]);
	});

	it('a transaction leaves its tag in place', async () => {
		const trip = await makeTag(call, 'Trip');
		const { transaction } = await json<{ transaction: { id: string } }>(
			await post({ accountId, amount: -1000, occurredOn: '2026-09-03', tagIds: [trip] }),
		);

		await call(`/transactions/${transaction.id}`, { method: 'DELETE' });

		expect((await json<{ tags: unknown[] }>(await call('/tags'))).tags).toHaveLength(1);
	});
});

describe('tags are private', () => {
	it('are not listed, renamed or deleted by another user', async () => {
		const trip = await makeTag(call, 'Trip');
		const theirs = await otherClient();

		expect((await json<{ tags: unknown[] }>(await theirs('/tags'))).tags).toEqual([]);
		expect((await theirs(`/tags/${trip}`, { method: 'PATCH', body: JSON.stringify({ name: 'Mine' }) })).status).toBe(404);
		expect((await theirs(`/tags/${trip}`, { method: 'DELETE' })).status).toBe(404);
	});

	it('let two users hold the same name', async () => {
		const theirs = await otherClient();
		await makeTag(call, 'Trip');

		expect((await theirs('/tags', { method: 'POST', body: JSON.stringify({ name: 'Trip' }) })).status).toBe(201);
	});
});
