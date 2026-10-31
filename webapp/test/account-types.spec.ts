import { beforeEach, describe, expect, it } from 'vitest';
import { DEFAULT_ACCOUNT_TYPES } from '../server/defaults';
import { accountTypeId, authedClient, json, makeAccount, otherClient, type Call } from './helpers';

let call: Call;

beforeEach(async () => {
	call = await authedClient();
});

const create = (body: Record<string, unknown>) => call('/account-types', { method: 'POST', body: JSON.stringify(body) });

describe('the starting vocabulary', () => {
	it('gives every new user the default set', async () => {
		const { accountTypes } = await json<{ accountTypes: { name: string }[] }>(await call('/account-types'));

		expect(accountTypes.map((type) => type.name)).toEqual(DEFAULT_ACCOUNT_TYPES.map((type) => type.name));
	});

	it('reports how many accounts use each type', async () => {
		const typeId = await accountTypeId(call, 'Checking');
		await makeAccount(call, { name: 'One', typeId });
		await makeAccount(call, { name: 'Two', typeId });

		const { accountTypes } = await json<{ accountTypes: { id: string; accountCount: number }[] }>(await call('/account-types'));
		const checking = accountTypes.find((type) => type.id === typeId)!;
		const unused = accountTypes.find((type) => type.id !== typeId)!;

		expect(checking.accountCount).toBe(2);
		expect(unused.accountCount).toBe(0);
	});
});

describe('customising', () => {
	it('adds a type of the user’s own', async () => {
		const response = await create({ name: 'Crypto Wallet', sortOrder: 9 });

		expect(response.status).toBe(201);
		const { accountType } = await json<{ accountType: Record<string, unknown> }>(response);
		expect(accountType).toMatchObject({ name: 'Crypto Wallet', sortOrder: 9, archived: false, accountCount: 0 });
	});

	it('uses a custom type for an account like any other', async () => {
		const { accountType } = await json<{ accountType: { id: string } }>(await create({ name: 'Crypto Wallet' }));
		const id = await makeAccount(call, { name: 'Ledger', typeId: accountType.id });

		const { account } = await json<{ account: { typeName: string } }>(await call(`/accounts/${id}`));
		expect(account.typeName).toBe('Crypto Wallet');
	});

	it('renames a type, and every account follows', async () => {
		const typeId = await accountTypeId(call, 'Checking');
		const id = await makeAccount(call, { name: 'Everyday', typeId });

		// Accounts reference the type by id, so a rename needs no backfill.
		expect((await call(`/account-types/${typeId}`, { method: 'PATCH', body: JSON.stringify({ name: 'Current' }) })).status).toBe(200);

		const { account } = await json<{ account: { typeName: string } }>(await call(`/accounts/${id}`));
		expect(account.typeName).toBe('Current');
	});

	it('refuses a duplicate name', async () => {
		await create({ name: 'Crypto Wallet' });
		expect((await create({ name: 'Crypto Wallet' })).status).toBe(409);
	});

	it('refuses renaming onto an existing name', async () => {
		const savings = await accountTypeId(call, 'Savings');
		const response = await call(`/account-types/${savings}`, { method: 'PATCH', body: JSON.stringify({ name: 'Checking' }) });
		expect(response.status).toBe(409);
	});

	it('rejects a blank name', async () => {
		expect((await create({ name: '   ' })).status).toBe(400);
	});

	it('deletes a type nothing uses', async () => {
		const { accountType } = await json<{ accountType: { id: string } }>(await create({ name: 'Temporary' }));

		expect((await call(`/account-types/${accountType.id}`, { method: 'DELETE' })).status).toBe(204);
		expect((await call(`/account-types/${accountType.id}`, { method: 'DELETE' })).status).toBe(404);
	});

	it('refuses to delete a type still in use, and says how many', async () => {
		const typeId = await accountTypeId(call, 'Checking');
		await makeAccount(call, { name: 'Everyday', typeId });

		const response = await call(`/account-types/${typeId}`, { method: 'DELETE' });
		expect(response.status).toBe(409);

		const { error } = await json<{ error: string }>(response);
		expect(error).toContain('1 account');
	});

	it('archives a type instead, hiding it from the picker but keeping accounts', async () => {
		const typeId = await accountTypeId(call, 'Checking');
		const id = await makeAccount(call, { name: 'Everyday', typeId });

		await call(`/account-types/${typeId}`, { method: 'PATCH', body: JSON.stringify({ archived: true }) });

		const visible = await json<{ accountTypes: { id: string }[] }>(await call('/account-types'));
		const all = await json<{ accountTypes: { id: string }[] }>(await call('/account-types?includeArchived=true'));

		expect(visible.accountTypes.some((type) => type.id === typeId)).toBe(false);
		expect(all.accountTypes.some((type) => type.id === typeId)).toBe(true);

		// The account itself is untouched and still reads its type.
		const { account } = await json<{ account: { typeName: string } }>(await call(`/accounts/${id}`));
		expect(account.typeName).toBe('Checking');
	});

	it('moves an account to a different type', async () => {
		const id = await makeAccount(call, { name: 'Everyday', typeId: await accountTypeId(call, 'Checking') });
		const savings = await accountTypeId(call, 'Savings');

		const response = await call(`/accounts/${id}`, { method: 'PATCH', body: JSON.stringify({ typeId: savings }) });
		expect(response.status).toBe(200);

		const { account } = await json<{ account: { typeName: string } }>(response);
		expect(account.typeName).toBe('Savings');
	});

	it('rejects moving an account to a type that does not exist', async () => {
		const id = await makeAccount(call);
		expect((await call(`/accounts/${id}`, { method: 'PATCH', body: JSON.stringify({ typeId: 'atp_nope' }) })).status).toBe(400);
	});

	it('404s for an unknown type', async () => {
		expect((await call('/account-types/atp_nope', { method: 'PATCH', body: JSON.stringify({ name: 'x' }) })).status).toBe(404);
		expect((await call('/account-types/atp_nope', { method: 'DELETE' })).status).toBe(404);
	});
});

describe('types are private to their owner', () => {
	let theirs: Call;

	beforeEach(async () => {
		theirs = await otherClient();
	});

	it('are not listed for another user', async () => {
		await create({ name: 'Crypto Wallet' });

		const { accountTypes } = await json<{ accountTypes: { name: string }[] }>(await theirs('/account-types'));
		expect(accountTypes.map((type) => type.name)).toEqual(DEFAULT_ACCOUNT_TYPES.map((type) => type.name));
	});

	it('cannot be renamed or deleted by another user', async () => {
		const typeId = await accountTypeId(call, 'Checking');

		expect((await theirs(`/account-types/${typeId}`, { method: 'PATCH', body: JSON.stringify({ name: 'Stolen' }) })).status).toBe(404);
		expect((await theirs(`/account-types/${typeId}`, { method: 'DELETE' })).status).toBe(404);
	});

	it('cannot be borrowed for another user’s account', async () => {
		const typeId = await accountTypeId(call, 'Checking');

		const response = await theirs('/accounts', { method: 'POST', body: JSON.stringify({ name: 'Sneaky', typeId }) });
		expect(response.status).toBe(400);
	});

	it('two users may each have a type of the same name', async () => {
		expect((await create({ name: 'Crypto Wallet' })).status).toBe(201);
		expect((await theirs('/account-types', { method: 'POST', body: JSON.stringify({ name: 'Crypto Wallet' }) })).status).toBe(201);
	});
});

describe('ordering', () => {
	const order = (ids: string[]) => call('/account-types/order', { method: 'PUT', body: JSON.stringify({ ids }) });
	const listed = async () =>
		(await json<{ accountTypes: { id: string; name: string; sortOrder: number }[] }>(await call('/account-types'))).accountTypes;

	it('puts the types in the order given, and answers with the list', async () => {
		const before = await listed();
		const reversed = [...before].reverse().map((type) => type.id);

		const response = await order(reversed);
		expect(response.status).toBe(200);
		const { accountTypes } = await json<{ accountTypes: { id: string; sortOrder: number }[] }>(response);
		expect(accountTypes.map((type) => type.id)).toEqual(reversed);
		expect(accountTypes.map((type) => type.sortOrder)).toEqual(reversed.map((_, index) => index));

		expect((await listed()).map((type) => type.id)).toEqual(reversed);
	});

	it('leaves a type already in its place untouched', async () => {
		const before = await listed();
		const swapped = [before[1].id, before[0].id, ...before.slice(2).map((type) => type.id)];
		const { accountTypes } = await json<{ accountTypes: { id: string; updatedAt: string }[] }>(await order(swapped));

		const untouched = before[2];
		expect(accountTypes.find((type) => type.id === untouched.id)!.updatedAt).toBe(
			(before as unknown as { id: string; updatedAt: string }[]).find((type) => type.id === untouched.id)!.updatedAt,
		);
	});

	it('refuses a list that names a type twice, or none', async () => {
		const [first] = await listed();
		expect((await order([first.id, first.id])).status).toBe(400);
		expect((await order([])).status).toBe(400);
	});
});
