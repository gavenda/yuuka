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
import { settingsRoutes } from './routes/settings';
import { summaryRoutes } from './routes/summary';
import { transactionRoutes } from './routes/transactions';
import type { AppEnv } from './types';

/**
 * The API, mounted by `functions/api/[[route]].ts`.
 *
 * Routes are declared with their full `/api/...` paths because the Pages
 * adapter hands the original request straight through — the catch-all filename
 * decides what reaches this app, not what the app strips off the front.
 *
 * There is no CORS layer: the API and the frontend are served from the same
 * Pages deployment, so requests are same-origin by construction.
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
