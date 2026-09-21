import { Hono } from 'hono';
import { requireAuth } from '../middleware/auth';
import { NOW_SQL } from '../sql';
import { parseJson } from '../validate';
import { deviceRegisterSchema } from '../schemas';
import type { AppEnv } from '../types';

/**
 * Where an install says how to reach it.
 *
 * The only thing kept is an FCM registration token and the install id that
 * owns it — no device name, no model, nothing that would make the table worth
 * reading for its own sake. A token is a capability to wake this install, so
 * it is scoped to the signed-in user like every other row here: registering
 * replaces whatever that install had registered before, which is what stops a
 * shared phone leaving the previous signed-in user addressable.
 *
 * Registration is repeated, not once: FCM rotates tokens on its own schedule
 * and the client re-registers on every start, so this is an upsert and
 * `last_seen_at` is the only thing that usually moves.
 */
export const deviceRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.put('/', async (c) => {
		const input = await parseJson(c, deviceRegisterSchema);
		const userId = c.get('userId');

		// Two unique keys are in play: the token (a token addresses one install,
		// and FCM can hand the same one back to a reinstall) and this user's
		// install id. Both resolve to "this is the same registration", so both
		// conflicts update rather than fail.
		await c.env.DB.batch([
			c.env.DB.prepare('DELETE FROM devices WHERE token = ? AND (user_id IS NOT ? OR device_id IS NOT ?)').bind(
				input.token,
				userId,
				input.deviceId,
			),
			c.env.DB.prepare(
				`INSERT INTO devices (token, user_id, device_id, platform) VALUES (?, ?, ?, ?)
				 ON CONFLICT (user_id, device_id) DO UPDATE SET token = excluded.token, platform = excluded.platform, last_seen_at = ${NOW_SQL}`,
			).bind(input.token, userId, input.deviceId, input.platform),
		]);

		return c.json({ registered: true });
	})
	/**
	 * Signing out. The token itself is the path, not the install id: the client
	 * knows the token it registered, and naming it means a stale client cannot
	 * unregister whatever the install happens to hold now.
	 */
	.delete('/:token', async (c) => {
		await c.env.DB.prepare('DELETE FROM devices WHERE token = ? AND user_id = ?').bind(c.req.param('token'), c.get('userId')).run();
		return c.body(null, 204);
	});
