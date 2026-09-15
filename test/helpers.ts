import { SELF } from 'cloudflare:test';
import { mintToken, OTHER_SUBJECT, publishJwks, type TokenOverrides } from './tokens';

const BASE = 'https://yuuka.test/api';

export function request(path: string, init: RequestInit = {}): Promise<Response> {
	return SELF.fetch(`${BASE}${path}`, init);
}

/**
 * Publishes the test JWKS and returns a caller that presents a freshly minted
 * access token on every request — the same thing the browser does with the
 * token Auth0 gave it.
 */
export async function authedClient(overrides: TokenOverrides = {}) {
	await publishJwks();
	const token = await mintToken(overrides);

	return async function call(path: string, init: RequestInit = {}): Promise<Response> {
		return request(path, {
			...init,
			headers: {
				'content-type': 'application/json',
				authorization: `Bearer ${token}`,
				...(init.headers as Record<string, string> | undefined),
			},
		});
	};
}

export type Call = Awaited<ReturnType<typeof authedClient>>;

/** A caller authenticated as a different Auth0 user. */
export function otherClient(): Promise<Call> {
	return authedClient({ sub: OTHER_SUBJECT });
}

export async function json<T>(response: Response): Promise<T> {
	return (await response.json()) as T;
}

/** The id of one of the user's account types, by name; defaults to the first. */
export async function accountTypeId(call: Call, name?: string): Promise<string> {
	const { accountTypes } = await json<{ accountTypes: { id: string; name: string }[] }>(await call('/account-types'));
	const match = name ? accountTypes.find((type) => type.name === name) : accountTypes[0];
	if (!match) throw new Error(`No account type ${name ?? '(first)'} — found ${accountTypes.map((t) => t.name).join(', ')}`);
	return match.id;
}

/** Creates an account and returns its id, picking a type unless one is given. */
export async function makeAccount(call: Call, overrides: Record<string, unknown> = {}): Promise<string> {
	const typeId = overrides.typeId ?? (await accountTypeId(call));
	const response = await call('/accounts', {
		method: 'POST',
		body: JSON.stringify({ name: 'Checking', startingBalance: 0, ...overrides, typeId }),
	});
	const body = await json<{ account: { id: string } }>(response);
	if (!body.account) throw new Error(`Account creation failed (${response.status}): ${JSON.stringify(body)}`);
	return body.account.id;
}

/**
 * Returns the id of a category with this name, creating it if the user does not
 * already have one. Signing in provisions a default set, so a spec asking for
 * "Groceries" should get that one rather than a duplicate-name conflict.
 */
export async function makeCategory(call: Call, overrides: Record<string, unknown> = {}): Promise<string> {
	const wanted = { name: 'Groceries', kind: 'expense', ...overrides };

	const response = await call('/categories', { method: 'POST', body: JSON.stringify(wanted) });
	if (response.status === 201) {
		return (await json<{ category: { id: string } }>(response)).category.id;
	}

	const { categories } = await json<{ categories: { id: string; name: string; kind: string }[] }>(
		await call('/categories?includeArchived=true'),
	);
	const existing = categories.find((entry) => entry.name === wanted.name && entry.kind === wanted.kind);
	if (!existing) throw new Error(`Could not create or find category ${JSON.stringify(wanted)} (status ${response.status})`);

	return existing.id;
}

/** How many categories a freshly provisioned user starts with. */
export async function categoryCount(call: Call, includeArchived = false): Promise<number> {
	const { categories } = await json<{ categories: unknown[] }>(await call(`/categories?includeArchived=${includeArchived}`));
	return categories.length;
}
