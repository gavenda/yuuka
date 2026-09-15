import { beforeEach, describe, expect, it } from 'vitest';
import { DEFAULT_CURRENCY } from '../functions/api/_lib/defaults';
import { authedClient, json, makeAccount, otherClient, type Call } from './helpers';

let call: Call;

beforeEach(async () => {
	call = await authedClient();
});

const patch = (body: Record<string, unknown>) => call('/settings', { method: 'PATCH', body: JSON.stringify(body) });

describe('display currency', () => {
	it('starts as the default', async () => {
		const { settings } = await json<{ settings: { displayCurrency: string } }>(await call('/settings'));
		expect(settings.displayCurrency).toBe(DEFAULT_CURRENCY);
		expect(DEFAULT_CURRENCY).toBe('PHP');
	});

	it('can be changed', async () => {
		const response = await patch({ displayCurrency: 'JPY' });
		expect(response.status).toBe(200);

		const { settings } = await json<{ settings: { displayCurrency: string } }>(response);
		expect(settings.displayCurrency).toBe('JPY');

		// And it sticks.
		const reread = await json<{ settings: { displayCurrency: string } }>(await call('/settings'));
		expect(reread.settings.displayCurrency).toBe('JPY');
	});

	it('is upper-cased, so the code is stored canonically', async () => {
		const { settings } = await json<{ settings: { displayCurrency: string } }>(await patch({ displayCurrency: 'eur' }));
		expect(settings.displayCurrency).toBe('EUR');
	});

	it('rejects anything that is not three letters', async () => {
		// Intl throws on a malformed code, and this figure appears on every
		// screen — so the API refuses rather than storing a page-breaking value.
		for (const value of ['', 'US', 'USDD', '12', 'US1', '   ', 'a b']) {
			expect((await patch({ displayCurrency: value })).status, JSON.stringify(value)).toBe(400);
		}
	});

	it('rejects an empty patch', async () => {
		expect((await call('/settings', { method: 'PATCH', body: '{}' })).status).toBe(400);
	});

	it('needs a token', async () => {
		const { request } = await import('./helpers');
		expect((await request('/settings')).status).toBe(401);
	});
});

describe('new accounts', () => {
	it('default to the default currency', async () => {
		const id = await makeAccount(call, { name: 'No currency given' });
		const { account } = await json<{ account: { currency: string } }>(await call(`/accounts/${id}`));

		expect(account.currency).toBe(DEFAULT_CURRENCY);
	});

	it('keep their own currency, independent of the display preference', async () => {
		const id = await makeAccount(call, { name: 'Dollar account', currency: 'usd' });
		await patch({ displayCurrency: 'JPY' });

		const { account } = await json<{ account: { currency: string } }>(await call(`/accounts/${id}`));
		// An account records what it holds; the display setting is what totals are shown in.
		expect(account.currency).toBe('USD');
	});
});

describe('settings are private', () => {
	it('one user changing theirs does not touch another’s', async () => {
		const theirs = await otherClient();
		await patch({ displayCurrency: 'JPY' });

		const mine = await json<{ settings: { displayCurrency: string } }>(await call('/settings'));
		const other = await json<{ settings: { displayCurrency: string } }>(await theirs('/settings'));

		expect(mine.settings.displayCurrency).toBe('JPY');
		expect(other.settings.displayCurrency).toBe(DEFAULT_CURRENCY);
	});
});
