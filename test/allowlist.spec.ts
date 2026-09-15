import { env } from 'cloudflare:test';
import { beforeEach, describe, expect, it } from 'vitest';
import { verifyAccessToken } from '../functions/api/_lib/auth0';
import { HttpError } from '../functions/api/_lib/errors';
import { mintToken, publishJwks, TEST_SUBJECT } from './tokens';

/**
 * The allowlist is read from the environment on every request, so these cases
 * call the verifier directly with an adjusted env rather than needing a second
 * test worker with different bindings.
 */
function envWith(overrides: Record<string, string>): Env {
	// `env` from `cloudflare:test` does not survive a spread — its bindings are
	// not own enumerable properties — so the copy is assembled by hand.
	return {
		DB: env.DB,
		CACHE: env.CACHE,
		AUTH0_DOMAIN: env.AUTH0_DOMAIN,
		AUTH0_AUDIENCE: env.AUTH0_AUDIENCE,
		ALLOWED_SUBJECTS: env.ALLOWED_SUBJECTS,
		...overrides,
		// `wrangler types` narrows each var to the literal in wrangler.jsonc, so the
		// overrides need a widening cast to stand in for them here.
	} as unknown as Env;
}

async function statusOf(promise: Promise<unknown>): Promise<number> {
	try {
		await promise;
		return 200;
	} catch (error) {
		return error instanceof HttpError ? error.status : 500;
	}
}

describe('subject allowlist', () => {
	beforeEach(() => publishJwks());

	it('admits a listed subject', async () => {
		const claims = await verifyAccessToken(envWith({ ALLOWED_SUBJECTS: TEST_SUBJECT }), await mintToken());
		expect(claims.sub).toBe(TEST_SUBJECT);
	});

	it('admits a listed subject among several, ignoring spacing', async () => {
		const claims = await verifyAccessToken(
			envWith({ ALLOWED_SUBJECTS: ` auth0|someone , ${TEST_SUBJECT} ,auth0|other ` }),
			await mintToken(),
		);
		expect(claims.sub).toBe(TEST_SUBJECT);
	});

	it('rejects a valid token from an unlisted subject with 403, not 401', async () => {
		// The token is genuine — the holder simply is not this deployment's owner,
		// which is a different failure from a bad token and deserves a different code.
		expect(await statusOf(verifyAccessToken(envWith({ ALLOWED_SUBJECTS: 'auth0|only-the-owner' }), await mintToken()))).toBe(403);
	});

	it('admits anyone when the allowlist is empty', async () => {
		const claims = await verifyAccessToken(envWith({ ALLOWED_SUBJECTS: '' }), await mintToken({ sub: 'auth0|anybody' }));
		expect(claims.sub).toBe('auth0|anybody');
	});

	it('still rejects an expired token from a listed subject', async () => {
		expect(await statusOf(verifyAccessToken(envWith({ ALLOWED_SUBJECTS: TEST_SUBJECT }), await mintToken({ expiresIn: -60 })))).toBe(401);
	});
});

describe('configuration errors', () => {
	beforeEach(() => publishJwks());

	it('fails loudly when the Auth0 domain is missing', async () => {
		const broken = envWith({ AUTH0_DOMAIN: '' });
		expect(await statusOf(verifyAccessToken(broken, await mintToken()))).toBe(500);
	});

	it('fails loudly when the audience is missing', async () => {
		const broken = envWith({ AUTH0_AUDIENCE: '' });
		expect(await statusOf(verifyAccessToken(broken, await mintToken()))).toBe(500);
	});

	it('tolerates a domain written with a scheme or trailing slash', async () => {
		const claims = await verifyAccessToken(envWith({ AUTH0_DOMAIN: 'https://auth.test.example/' }), await mintToken());
		expect(claims.sub).toBe(TEST_SUBJECT);
	});
});
