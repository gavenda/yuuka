import { Hono } from 'hono';
import { requireAuth } from '../middleware/auth';
import type { AppEnv } from '../types';

/**
 * Signing in and out happen against Auth0 in the browser, so there is no login
 * endpoint here — the API only ever validates the resulting access token.
 */
export const authRoutes = new Hono<AppEnv>().get('/me', requireAuth, (c) => {
	const claims = c.get('claims');

	return c.json({
		subject: claims.sub,
		issuer: claims.iss ?? null,
		expiresAt: typeof claims.exp === 'number' ? claims.exp * 1000 : null,
		permissions: claims.permissions ?? [],
	});
});
