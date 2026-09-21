import { Hono } from 'hono';
import { HTTPException } from 'hono/http-exception';
import { HttpError } from './errors';
import { accountTypeRoutes } from './routes/account-types';
import { accountRoutes } from './routes/accounts';
import { authRoutes } from './routes/auth';
import { budgetRoutes } from './routes/budgets';
import { categoryRoutes } from './routes/categories';
import { deviceRoutes } from './routes/devices';
import { incomePlanRoutes } from './routes/income-plan';
import { payeeRoutes } from './routes/payees';
import { roundUpRoutes } from './routes/round-up';
import { settingsRoutes } from './routes/settings';
import { subscriptionRoutes } from './routes/subscriptions';
import { createSyncRoutes } from './routes/sync';
import { summaryRoutes } from './routes/summary';
import { tagRoutes } from './routes/tags';
import { transactionRoutes } from './routes/transactions';
import { notifyChange, slicesForPath } from './notify';
import { runDueSubscriptions } from './subscriptions';
import type { AppEnv } from './types';

/**
 * The Worker's entry point (`main` in `wrangler.jsonc`). A Hono app is already
 * a `fetch` handler, so it answers requests as-is; the same Worker also carries
 * the `scheduled` handler for the cron trigger declared in `wrangler.jsonc`.
 *
 * Routes are declared with their full `/api/...` paths because the request
 * arrives untouched. `assets.run_worker_first` in `wrangler.jsonc` is what sends
 * `/api/*` here; everything else is answered by the static assets.
 *
 * There is no CORS layer: the API and the frontend are served from the same
 * Worker, so requests are same-origin by construction.
 *
 * Authentication is Auth0: every protected route verifies the bearer access
 * token the browser obtained, rather than any session this app issues itself.
 */
const app = new Hono<AppEnv>();

/**
 * Tells this user's other installs that a write landed.
 *
 * It sits here, in front of every route, rather than at the end of each
 * handler: the slices a write disturbs follow from its path, so one middleware
 * knows as much as thirteen sprinkled `notifyChange` calls would, and a route
 * added later is covered without anyone remembering to. A read changes
 * nothing, and a failed write changed nothing either, so both are silent.
 *
 * `waitUntil` is what keeps the push off the response's critical path: the
 * caller gets its answer as soon as the write is done, and the fan-out happens
 * after. A sub-request from a queued batch carries `X-Yuuka-Batch` and is
 * skipped — the batch sends one push for the lot when it has finished.
 */
app.use('*', async (c, next) => {
	await next();

	if (c.req.method === 'GET' || c.req.method === 'HEAD') return;
	if (c.res.status >= 300) return;
	if (c.req.header('X-Yuuka-Batch')) return;

	const slices = slicesForPath(new URL(c.req.url).pathname);
	if (slices.length === 0) return;

	const userId = c.get('userId');
	if (!userId) return;

	c.executionCtx.waitUntil(notifyChange(c.env, userId, slices, c.req.header('X-Yuuka-Device') ?? null));
});

app.get('/api/health', (c) => c.json({ status: 'ok', service: 'yuuka' }));

app.route('/api/auth', authRoutes);
app.route('/api/accounts', accountRoutes);
app.route('/api/account-types', accountTypeRoutes);
app.route('/api/categories', categoryRoutes);
app.route('/api/tags', tagRoutes);
app.route('/api/transactions', transactionRoutes);
app.route('/api/budgets', budgetRoutes);
app.route('/api/income-plan', incomePlanRoutes);
app.route('/api/payees', payeeRoutes);
app.route('/api/settings', settingsRoutes);
app.route('/api/round-up', roundUpRoutes);
app.route('/api/subscriptions', subscriptionRoutes);
app.route('/api/summary', summaryRoutes);
app.route('/api/devices', deviceRoutes);
// Mounted with a dispatcher back into this same app: a queued operation is
// replayed through the router it would have gone through online. Declaring it
// as a closure is what makes referring to `app` here legal.
app.route(
	'/api/sync',
	createSyncRoutes(async (request, env, ctx) => await app.fetch(request, env, ctx as Parameters<typeof app.fetch>[2])),
);

app.notFound((c) => c.json({ error: 'Not found.' }, 404));

app.onError((error, c) => {
	if (error instanceof HttpError) {
		return c.json({ error: error.message, ...(error.details ? { details: error.details } : {}) }, error.status);
	}
	if (error instanceof HTTPException) {
		return error.getResponse();
	}

	console.error('Unhandled error', error);
	return c.json({ error: 'Internal server error.' }, 500);
});

export default {
	fetch: app.fetch,

	/**
	 * Fires at 00:00 UTC (`triggers.crons` in `wrangler.jsonc`) and posts every
	 * subscription that has come due. `scheduledTime` is when the trigger was
	 * meant to fire, not when it happened to start, so a late tick still posts
	 * for the right day.
	 */
	async scheduled(controller, env, ctx): Promise<void> {
		ctx.waitUntil(runDueSubscriptions(env, new Date(controller.scheduledTime)));
	},
} satisfies ExportedHandler<Env>;
