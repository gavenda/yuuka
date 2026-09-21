import { Hono } from 'hono';
import { badRequest } from '../errors';
import { requireAuth } from '../middleware/auth';
import { notifyChange } from '../notify';
import { syncBatchSchema } from '../schemas';
import { applyBatch, type Dispatch } from '../sync';
import { parseJson } from '../validate';
import type { AppEnv } from '../types';

/** Paths a queued operation may not name, whatever it claims. */
const FORBIDDEN_PREFIXES = ['/api/sync', '/api/auth', '/api/devices'];

/**
 * The endpoint a client's offline queue drains into. The work is in
 * `server/sync.ts`; this is the route around it.
 *
 * It takes the dispatcher rather than importing the app, because the app is
 * what mounts this route — passing it in is what keeps that from being a cycle,
 * and it is also the seam a test uses to watch what a batch actually called.
 *
 * Three things a queued operation may not do, checked here rather than in
 * `applyBatch` so the rule sits next to the route that exposes it:
 *
 * - **Nest.** A batch inside a batch would recurse, and there is no reason to
 *   want one.
 * - **Re-authenticate.** The token on the batch is the identity for everything
 *   in it; an operation naming `/api/auth` could only be trying to change that.
 * - **Register a device.** A registration is about the connection that is
 *   happening now, so replaying a queued one would resurrect a token that has
 *   since been retired.
 */
export const createSyncRoutes = (dispatch: Dispatch) =>
	new Hono<AppEnv>().use('*', requireAuth).post('/batch', async (c) => {
		const { operations } = await parseJson(c, syncBatchSchema);
		const userId = c.get('userId');

		const forbidden = operations.find((operation) =>
			FORBIDDEN_PREFIXES.some((prefix) => operation.path === prefix || operation.path.startsWith(`${prefix}/`)),
		);
		if (forbidden) throw badRequest(`An operation cannot target ${forbidden.path}.`);

		const { results, slices } = await applyBatch(c.env, c.executionCtx, userId, c.req.raw, operations, dispatch);

		// One push for the whole batch, after it has all landed — the other
		// device wants the state the queue drained to, not a running commentary.
		c.executionCtx.waitUntil(notifyChange(c.env, userId, slices, c.req.header('X-Yuuka-Device') ?? null));

		return c.json({ results });
	});
