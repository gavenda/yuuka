import { beforeEach, describe, expect, it } from 'vitest';
import { authedClient, json, request } from './helpers';
import { mintToken, publishJwks, TEST_ISSUER, TEST_SUBJECT, testKeys } from './tokens';

const PROTECTED = ['/accounts', '/categories', '/transactions', '/budgets', '/summary', '/auth/me'];

describe('health', () => {
	it('answers without authentication', async () => {
		const response = await request('/health');
		expect(response.status).toBe(200);
		expect(await json(response)).toEqual({ status: 'ok', service: 'yuuka' });
	});
});

describe('access tokens', () => {
	beforeEach(() => publishJwks());

	it('refuses protected routes with no token', async () => {
		for (const path of PROTECTED) {
			expect((await request(path)).status, path).toBe(401);
		}
	});

	it('refuses a non-bearer authorization header', async () => {
		expect((await request('/accounts', { headers: { authorization: 'Basic hunter2' } })).status).toBe(401);
	});

	it('refuses a token that is not a JWT at all', async () => {
		expect((await request('/accounts', { headers: { authorization: 'Bearer not-a-jwt' } })).status).toBe(401);
	});

	it('accepts a correctly signed token', async () => {
		const call = await authedClient();
		expect((await call('/accounts')).status).toBe(200);
	});

	it('reports the caller back from /auth/me', async () => {
		const call = await authedClient();
		const body = await json<{ subject: string; issuer: string; expiresAt: number }>(await call('/auth/me'));

		expect(body.subject).toBe(TEST_SUBJECT);
		expect(body.issuer).toBe(TEST_ISSUER);
		expect(body.expiresAt).toBeGreaterThan(Date.now());
	});

	it('refuses an expired token', async () => {
		const call = await authedClient({ expiresIn: -60 });
		expect((await call('/accounts')).status).toBe(401);
	});

	it('refuses a token issued for a different audience', async () => {
		const call = await authedClient({ aud: 'https://api.someone-else.test' });
		expect((await call('/accounts')).status).toBe(401);
	});

	it('refuses a token from a different issuer', async () => {
		const call = await authedClient({ iss: 'https://evil.example/' });
		expect((await call('/accounts')).status).toBe(401);
	});

	it('fails closed when the signing key is unknown and the JWKS cannot be refetched', async () => {
		const token = await mintToken();
		// Republish a JWKS whose only key has a different id, so the token's `kid`
		// no longer resolves. That looks like a key rotation, so the verifier tries
		// to refetch — and when the identity provider is unreachable it denies the
		// request rather than letting an unverifiable token through.
		const { publicJwk } = await testKeys();
		await publishJwks([{ ...publicJwk, kid: 'some-other-key' }]);

		const response = await request('/accounts', { headers: { authorization: `Bearer ${token}` } });
		expect(response.status).toBe(503);
	});

	it('refuses a tampered payload', async () => {
		const token = await mintToken();
		const [header, , signature] = token.split('.');
		const forged = btoa(JSON.stringify({ sub: 'auth0|intruder', iss: TEST_ISSUER, aud: 'https://api.yuuka.test', exp: 9_999_999_999 }))
			.replaceAll('+', '-')
			.replaceAll('/', '_')
			.replace(/=+$/, '');

		const response = await request('/accounts', { headers: { authorization: `Bearer ${header}.${forged}.${signature}` } });
		expect(response.status).toBe(401);
	});
});

describe('subject allowlist', () => {
	beforeEach(() => publishJwks());

	// The pool binds ALLOWED_SUBJECTS as empty, so any authenticated subject is
	// admitted; the restricted case is covered in allowlist.spec.ts.
	it('admits any tenant user when no allowlist is configured', async () => {
		const call = await authedClient({ sub: 'auth0|somebody-else' });
		expect((await call('/accounts')).status).toBe(200);
	});
});

describe('routing', () => {
	it('returns a JSON 404 for unknown paths', async () => {
		const response = await request('/nope');
		expect(response.status).toBe(404);
		expect(await json(response)).toEqual({ error: 'Not found.' });
	});
});
