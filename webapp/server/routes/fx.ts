import { Hono } from 'hono';
import { today } from '../dates';
import { unavailable } from '../errors';
import { exchangeRate } from '../fx';
import { requireAuth } from '../middleware/auth';
import { fxQuerySchema } from '../schemas';
import { parseQuery } from '../validate';
import type { AppEnv } from '../types';

/**
 * `GET /api/fx?from=PHP&to=USD&date=YYYY-MM-DD`: the rate a transfer's form suggests. Behind sign-in like every
 * other route, so the Worker is not an open proxy to the rate sources. A pair no source quotes answers 503, and
 * the form leaves the rate for the person to type.
 */
export const fxRoutes = new Hono<AppEnv>().use('*', requireAuth).get('/', async (c) => {
	const input = parseQuery(c, fxQuerySchema);
	const now = today();
	const found = await exchangeRate(c.env.CACHE, input.from, input.to, input.date ?? now, now);
	if (!found) throw unavailable('No exchange rate is available for that pair right now.');
	return c.json(found);
});
