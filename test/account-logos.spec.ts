import { beforeEach, describe, expect, it } from 'vitest';
import { accountTypeId, authedClient, json, makeAccount, type Call } from './helpers';

let call: Call;
let typeId: string;

beforeEach(async () => {
	call = await authedClient();
	typeId = await accountTypeId(call);
});

const create = (body: Record<string, unknown>) =>
	call('/accounts', { method: 'POST', body: JSON.stringify({ name: 'With logo', typeId, ...body }) });

const logoOf = async (id: string) => (await json<{ account: { logoUrl: string | null } }>(await call(`/accounts/${id}`))).account.logoUrl;

describe('account logos', () => {
	it('defaults to none', async () => {
		expect(await logoOf(await makeAccount(call))).toBeNull();
	});

	it('stores an https URL', async () => {
		const response = await create({ logoUrl: 'https://example.com/logo.png' });
		expect(response.status).toBe(201);

		const { account } = await json<{ account: { logoUrl: string } }>(response);
		expect(account.logoUrl).toBe('https://example.com/logo.png');
	});

	it('accepts plain http too', async () => {
		const response = await create({ logoUrl: 'http://example.com/logo.png' });
		expect(response.status).toBe(201);
	});

	it('trims surrounding whitespace', async () => {
		const { account } = await json<{ account: { logoUrl: string } }>(await create({ logoUrl: '  https://example.com/a.png  ' }));
		expect(account.logoUrl).toBe('https://example.com/a.png');
	});

	it('treats an empty string as no logo', async () => {
		const { account } = await json<{ account: { logoUrl: string | null } }>(await create({ logoUrl: '   ' }));
		expect(account.logoUrl).toBeNull();
	});

	it('can be set, changed and cleared', async () => {
		const id = await makeAccount(call);

		await call(`/accounts/${id}`, { method: 'PATCH', body: JSON.stringify({ logoUrl: 'https://example.com/one.png' }) });
		expect(await logoOf(id)).toBe('https://example.com/one.png');

		await call(`/accounts/${id}`, { method: 'PATCH', body: JSON.stringify({ logoUrl: 'https://example.com/two.png' }) });
		expect(await logoOf(id)).toBe('https://example.com/two.png');

		// Emptying the field is how the form clears it.
		await call(`/accounts/${id}`, { method: 'PATCH', body: JSON.stringify({ logoUrl: '' }) });
		expect(await logoOf(id)).toBeNull();

		await call(`/accounts/${id}`, { method: 'PATCH', body: JSON.stringify({ logoUrl: 'https://example.com/three.png' }) });
		await call(`/accounts/${id}`, { method: 'PATCH', body: JSON.stringify({ logoUrl: null }) });
		expect(await logoOf(id)).toBeNull();
	});

	it('is left alone by a patch that does not mention it', async () => {
		const id = await makeAccount(call, { logoUrl: 'https://example.com/keep.png' });

		await call(`/accounts/${id}`, { method: 'PATCH', body: JSON.stringify({ name: 'Renamed' }) });
		expect(await logoOf(id)).toBe('https://example.com/keep.png');
	});

	it('refuses schemes that are not http(s)', async () => {
		// The value lands in an `<img src>`, so the scheme is restricted rather
		// than letting arbitrary payloads through.
		for (const url of [
			'javascript:alert(1)',
			'data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=',
			'file:///etc/passwd',
			'ftp://example.com/logo.png',
			'//example.com/logo.png',
			'not a url',
			'example.com/logo.png',
		]) {
			expect((await create({ logoUrl: url })).status, url).toBe(400);
		}
	});

	it('refuses a URL longer than the column should hold', async () => {
		expect((await create({ logoUrl: `https://example.com/${'a'.repeat(2100)}.png` })).status).toBe(400);
	});

	it('appears on the account listing, not just the detail', async () => {
		await makeAccount(call, { name: 'Listed', logoUrl: 'https://example.com/listed.png' });

		const { accounts } = await json<{ accounts: { name: string; logoUrl: string | null }[] }>(await call('/accounts'));
		expect(accounts.find((account) => account.name === 'Listed')?.logoUrl).toBe('https://example.com/listed.png');
	});
});
