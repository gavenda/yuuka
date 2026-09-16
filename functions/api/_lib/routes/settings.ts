import { Hono } from 'hono';
import { invalidateAllSummaries } from '../cache';
import { notFound } from '../errors';
import { buildUpdate, NOW_SQL } from '../sql';
import { parseJson } from '../validate';
import { requireAuth } from '../middleware/auth';
import { toSettings, type SettingsRow } from '../mappers';
import { settingsUpdateSchema } from '../schemas';
import type { AppEnv } from '../types';

const SETTINGS_COLUMNS = 'display_currency, budget_mode, created_at, updated_at';

/** Per-user preferences. The row always exists by the time this runs — `requireAuth` provisions it. */
export const settingsRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const row = await c.env.DB.prepare(`SELECT ${SETTINGS_COLUMNS} FROM users WHERE id = ?`).bind(c.get('userId')).first<SettingsRow>();

		if (!row) throw notFound('Settings not found.');
		return c.json({ settings: toSettings(row) });
	})
	.patch('/', async (c) => {
		const input = await parseJson(c, settingsUpdateSchema);
		const userId = c.get('userId');

		const { clause, values } = buildUpdate({ display_currency: input.displayCurrency, budget_mode: input.budgetMode });

		await c.env.DB.prepare(`UPDATE users SET ${clause}, updated_at = ${NOW_SQL} WHERE id = ?`)
			.bind(...values, userId)
			.run();

		// Switching budget modes changes what "planned" means for every month at
		// once, not just the one being viewed — a narrower purge would leave
		// stale figures behind for every other month.
		if (input.budgetMode !== undefined) await invalidateAllSummaries(c.env.CACHE, userId);

		const row = await c.env.DB.prepare(`SELECT ${SETTINGS_COLUMNS} FROM users WHERE id = ?`).bind(userId).first<SettingsRow>();

		return c.json({ settings: toSettings(row!) });
	});
