import { Hono } from 'hono';
import { HTTPException } from 'hono/http-exception';
import { HttpError } from './errors';
import { accountTypeRoutes } from './routes/account-types';
import { accountRoutes } from './routes/accounts';
import { authRoutes } from './routes/auth';
import { budgetRoutes } from './routes/budgets';
import { categoryRoutes } from './routes/categories';
import { incomePlanRoutes } from './routes/income-plan';
import { payeeRoutes } from './routes/payees';
import { roundUpRoutes } from './routes/round-up';
import { settingsRoutes } from './routes/settings';
import { summaryRoutes } from './routes/summary';
import { transactionRoutes } from './routes/transactions';
import type { AppEnv } from './types';

/**
 * The Worker's entry point: a Hono app is already a `fetch` handler, so it is
 * exported as-is (`main` in `wrangler.jsonc`).
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

app.get('/api/health', (c) => c.json({ status: 'ok', service: 'yuuka' }));

app.route('/api/auth', authRoutes);
app.route('/api/accounts', accountRoutes);
app.route('/api/account-types', accountTypeRoutes);
app.route('/api/categories', categoryRoutes);
app.route('/api/transactions', transactionRoutes);
app.route('/api/budgets', budgetRoutes);
app.route('/api/income-plan', incomePlanRoutes);
app.route('/api/payees', payeeRoutes);
app.route('/api/settings', settingsRoutes);
app.route('/api/round-up', roundUpRoutes);
app.route('/api/summary', summaryRoutes);

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

export default app;
