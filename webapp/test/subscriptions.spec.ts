import { createExecutionContext, createScheduledController, env, waitOnExecutionContext } from 'cloudflare:test';
import { beforeEach, describe, expect, it } from 'vitest';
import { nextOccurrence } from '../server/dates';
import worker from '../server/index';
import { MAX_CATCH_UP, runDueSubscriptions } from '../server/subscriptions';
import { authedClient, json, makeAccount, makeCategory, otherClient, type Call } from './helpers';
import { TEST_SUBJECT } from './tokens';

let call: Call;
let accountId: string;
let categoryId: string;

beforeEach(async () => {
	call = await authedClient();
	accountId = await makeAccount(call, { name: 'Checking' });
	categoryId = await makeCategory(call, { name: 'Streaming', kind: 'expense' });
});

interface SubscriptionBody {
	id: string;
	accountId: string;
	accountName: string | null;
	categoryId: string | null;
	categoryName: string | null;
	amount: number;
	payee: string;
	notes: string;
	startOn: string;
	dayOfMonth: number;
	nextRunOn: string;
	lastRunOn: string | null;
	enabled: boolean;
}

interface TransactionBody {
	id: string;
	accountId: string;
	categoryId: string | null;
	amount: number;
	occurredOn: string;
	payee: string;
	notes: string;
	automated: boolean;
}

/** `YYYY-MM-DD` for a day this many days from now, in UTC — the clock the API compares against. */
function daysFromNow(days: number): string {
	return new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10);
}

/** The instant a cron tick for that date fires. */
const tickOf = (date: string) => new Date(`${date}T00:00:00Z`);

function create(body: Record<string, unknown> = {}) {
	return call('/subscriptions', {
		method: 'POST',
		body: JSON.stringify({ accountId, categoryId, amount: -1500, payee: 'Netflix', startOn: daysFromNow(2), ...body }),
	});
}

async function createOne(body: Record<string, unknown> = {}): Promise<SubscriptionBody> {
	const response = await create(body);
	expect(response.status).toBe(201);
	return (await json<{ subscription: SubscriptionBody }>(response)).subscription;
}

async function list(client: Call = call): Promise<SubscriptionBody[]> {
	return (await json<{ subscriptions: SubscriptionBody[] }>(await client('/subscriptions'))).subscriptions;
}

async function transactions(client: Call = call): Promise<TransactionBody[]> {
	return (await json<{ transactions: TransactionBody[] }>(await client('/transactions?limit=200'))).transactions;
}

/**
 * Inserts a subscription straight into the table, for the runner's tests: the
 * API refuses a start date in the past and a fixed clock needs one, and the
 * clamping cases need a 31st that is not on the calendar this test runs on.
 */
async function seed(fields: {
	dayOfMonth: number;
	nextRunOn: string;
	enabled?: boolean;
	amount?: number;
	accountId?: string;
}): Promise<string> {
	const id = `sub_seed_${crypto.randomUUID().replaceAll('-', '')}`;
	await env.DB.prepare(
		`INSERT INTO subscriptions (id, user_id, account_id, category_id, amount, payee, notes, start_on, day_of_month, next_run_on, enabled)
		 VALUES (?, ?, ?, ?, ?, 'Rent', 'monthly', ?, ?, ?, ?)`,
	)
		.bind(
			id,
			TEST_SUBJECT,
			fields.accountId ?? accountId,
			categoryId,
			fields.amount ?? -2500,
			fields.nextRunOn,
			fields.dayOfMonth,
			fields.nextRunOn,
			fields.enabled === false ? 0 : 1,
		)
		.run();
	return id;
}

describe('creating a subscription', () => {
	it('anchors the schedule on the start date and joins its labels', async () => {
		const startOn = daysFromNow(9);
		const subscription = await createOne({ startOn, notes: 'Family plan' });

		expect(subscription).toMatchObject({
			accountId,
			accountName: 'Checking',
			categoryId,
			categoryName: 'Streaming',
			amount: -1500,
			payee: 'Netflix',
			notes: 'Family plan',
			startOn,
			dayOfMonth: Number(startOn.slice(8, 10)),
			nextRunOn: startOn,
			lastRunOn: null,
			enabled: true,
		});
	});

	it('accepts an income subscription and no category', async () => {
		const subscription = await createOne({ amount: 30_000, categoryId: null, payee: 'Retainer' });
		expect(subscription).toMatchObject({ amount: 30_000, categoryId: null, categoryName: null });
	});

	it('accepts a start date of today, and refuses one in the past', async () => {
		expect((await create({ startOn: daysFromNow(0) })).status).toBe(201);
		expect((await create({ startOn: daysFromNow(-1) })).status).toBe(400);
	});

	it.each([
		['a zero amount', { amount: 0 }],
		['a blank payee', { payee: '   ' }],
		['no payee', { payee: undefined }],
		['a date that does not exist', { startOn: '2099-02-31' }],
		['a start date with a time', { startOn: `${daysFromNow(2)}T09:00` }],
	])('rejects %s', async (_, override) => {
		expect((await create(override)).status).toBe(400);
	});

	it('rejects a transfer-scope category', async () => {
		const cashflow = await makeCategory(call, { name: 'Cashflow', kind: 'expense', appliesTo: 'transfer' });
		expect((await create({ categoryId: cashflow })).status).toBe(400);
	});

	it('rejects an account or category it does not own', async () => {
		const theirs = await otherClient();
		const theirAccount = await makeAccount(theirs, { name: 'Theirs' });
		const theirCategory = await makeCategory(theirs, { name: 'Theirs', kind: 'expense' });

		expect((await create({ accountId: theirAccount })).status).toBe(400);
		expect((await create({ categoryId: theirCategory })).status).toBe(400);
		expect(await list()).toEqual([]);
	});

	it('lists the soonest first, with paused ones after', async () => {
		const later = await createOne({ payee: 'Later', startOn: daysFromNow(20) });
		const sooner = await createOne({ payee: 'Sooner', startOn: daysFromNow(4) });
		const paused = await createOne({ payee: 'Paused', startOn: daysFromNow(1) });
		await call(`/subscriptions/${paused.id}`, { method: 'PATCH', body: JSON.stringify({ enabled: false }) });

		expect((await list()).map((entry) => entry.id)).toEqual([sooner.id, later.id, paused.id]);
	});

	it('writes no transaction itself — that is the cron’s job', async () => {
		await createOne({ startOn: daysFromNow(0) });
		expect(await transactions()).toEqual([]);
	});
});

describe('editing a subscription', () => {
	it('changes only the fields it is given', async () => {
		const subscription = await createOne();

		const response = await call(`/subscriptions/${subscription.id}`, {
			method: 'PATCH',
			body: JSON.stringify({ amount: -2000, notes: 'Price rise' }),
		});
		expect(response.status).toBe(200);

		const { subscription: updated } = await json<{ subscription: SubscriptionBody }>(response);
		expect(updated).toMatchObject({ amount: -2000, notes: 'Price rise', payee: 'Netflix', nextRunOn: subscription.nextRunOn, categoryId });
	});

	it('can clear its category', async () => {
		const subscription = await createOne();
		const { subscription: updated } = await json<{ subscription: SubscriptionBody }>(
			await call(`/subscriptions/${subscription.id}`, { method: 'PATCH', body: JSON.stringify({ categoryId: null }) }),
		);
		expect(updated.categoryId).toBeNull();
	});

	it('restarts the schedule from a new start date', async () => {
		const subscription = await createOne();
		const startOn = daysFromNow(12);

		const { subscription: updated } = await json<{ subscription: SubscriptionBody }>(
			await call(`/subscriptions/${subscription.id}`, { method: 'PATCH', body: JSON.stringify({ startOn }) }),
		);
		expect(updated).toMatchObject({ startOn, nextRunOn: startOn, dayOfMonth: Number(startOn.slice(8, 10)) });
	});

	it('refuses a start date in the past', async () => {
		const subscription = await createOne();
		const response = await call(`/subscriptions/${subscription.id}`, {
			method: 'PATCH',
			body: JSON.stringify({ startOn: daysFromNow(-3) }),
		});
		expect(response.status).toBe(400);
	});

	it('resumes at the next occurrence rather than owing every month it was paused', async () => {
		const id = await seed({ dayOfMonth: 15, nextRunOn: '2024-01-15', enabled: false });

		const { subscription } = await json<{ subscription: SubscriptionBody }>(
			await call(`/subscriptions/${id}`, { method: 'PATCH', body: JSON.stringify({ enabled: true }) }),
		);

		expect(subscription.enabled).toBe(true);
		expect(subscription.nextRunOn >= daysFromNow(0)).toBe(true);
		expect(subscription.nextRunOn.endsWith('-15')).toBe(true);
	});

	it('leaves the schedule alone when it is paused, or edited while running', async () => {
		const subscription = await createOne();
		await call(`/subscriptions/${subscription.id}`, { method: 'PATCH', body: JSON.stringify({ enabled: false }) });
		await call(`/subscriptions/${subscription.id}`, { method: 'PATCH', body: JSON.stringify({ payee: 'Netflix Premium' }) });

		expect((await list())[0]).toMatchObject({ enabled: false, nextRunOn: subscription.nextRunOn, payee: 'Netflix Premium' });
	});

	it('rejects an empty patch and unknown ids', async () => {
		const subscription = await createOne();
		expect((await call(`/subscriptions/${subscription.id}`, { method: 'PATCH', body: '{}' })).status).toBe(400);
		expect((await call('/subscriptions/sub_missing', { method: 'PATCH', body: JSON.stringify({ amount: -1 }) })).status).toBe(404);
	});

	it('cannot be re-pointed at a transfer category or someone else’s account', async () => {
		const subscription = await createOne();
		const cashflow = await makeCategory(call, { name: 'Cashflow', kind: 'expense', appliesTo: 'transfer' });
		const theirAccount = await makeAccount(await otherClient(), { name: 'Theirs' });

		const patch = (body: Record<string, unknown>) =>
			call(`/subscriptions/${subscription.id}`, { method: 'PATCH', body: JSON.stringify(body) });
		expect((await patch({ categoryId: cashflow })).status).toBe(400);
		expect((await patch({ accountId: theirAccount })).status).toBe(400);
		expect((await list())[0]).toMatchObject({ accountId, categoryId });
	});
});

describe('deleting', () => {
	it('removes the subscription and 404s afterwards', async () => {
		const subscription = await createOne();

		expect((await call(`/subscriptions/${subscription.id}`, { method: 'DELETE' })).status).toBe(204);
		expect((await call(`/subscriptions/${subscription.id}`, { method: 'DELETE' })).status).toBe(404);
		expect(await list()).toEqual([]);
	});

	it('goes when its account does', async () => {
		await createOne();
		expect((await call(`/accounts/${accountId}`, { method: 'DELETE' })).status).toBe(204);
		expect(await list()).toEqual([]);
	});

	it('keeps what it already posted', async () => {
		const startOn = daysFromNow(1);
		const subscription = await createOne({ startOn });
		await runDueSubscriptions(env, tickOf(startOn));

		await call(`/subscriptions/${subscription.id}`, { method: 'DELETE' });
		expect(await transactions()).toHaveLength(1);
	});

	it('leaves the subscription uncategorised when its category is deleted', async () => {
		await createOne();
		await call(`/categories/${categoryId}`, { method: 'DELETE' });
		expect((await list())[0].categoryId).toBeNull();
	});
});

describe('the daily run', () => {
	it('posts a due subscription as an automated transaction', async () => {
		const startOn = daysFromNow(3);
		const subscription = await createOne({ startOn, notes: 'Family plan' });

		expect(await runDueSubscriptions(env, tickOf(startOn))).toEqual({ posted: 1, failed: 0 });

		expect(await transactions()).toEqual([
			expect.objectContaining({
				accountId,
				categoryId,
				amount: -1500,
				// A bare date: there is no time of day to have chosen.
				occurredOn: startOn,
				payee: 'Netflix',
				notes: 'Family plan',
				automated: true,
			}),
		]);

		// The account's balance moves like any other transaction.
		const { account } = await json<{ account: { balance: number } }>(await call(`/accounts/${accountId}`));
		expect(account.balance).toBe(-1500);

		const [after] = await list();
		expect(after).toMatchObject({ id: subscription.id, lastRunOn: startOn });
		expect(after.nextRunOn).toBe(nextOccurrence(Number(startOn.slice(8, 10)), startOn));
	});

	it('does nothing before the day it is due', async () => {
		const startOn = daysFromNow(3);
		await createOne({ startOn });

		expect(await runDueSubscriptions(env, tickOf(daysFromNow(2)))).toEqual({ posted: 0, failed: 0 });
		expect(await transactions()).toEqual([]);
	});

	it('cannot post the same run twice', async () => {
		const startOn = daysFromNow(3);
		await createOne({ startOn });

		await runDueSubscriptions(env, tickOf(startOn));
		expect(await runDueSubscriptions(env, tickOf(startOn))).toEqual({ posted: 0, failed: 0 });
		expect(await transactions()).toHaveLength(1);
	});

	it('cannot post the same run twice when two invocations overlap', async () => {
		const id = await seed({ dayOfMonth: 10, nextRunOn: '2027-03-10' });

		const results = await Promise.all([runDueSubscriptions(env, tickOf('2027-03-10')), runDueSubscriptions(env, tickOf('2027-03-10'))]);

		expect(results.reduce((sum, result) => sum + result.posted, 0)).toBe(1);
		expect(await transactions()).toHaveLength(1);
		expect((await list()).find((entry) => entry.id === id)!.nextRunOn).toBe('2027-04-10');
	});

	it('repeats every month on the same day', async () => {
		const id = await seed({ dayOfMonth: 15, nextRunOn: '2027-01-15' });

		for (const date of ['2027-01-15', '2027-02-15', '2027-03-15']) await runDueSubscriptions(env, tickOf(date));

		expect((await transactions()).map((entry) => entry.occurredOn).sort()).toEqual(['2027-01-15', '2027-02-15', '2027-03-15']);
		expect((await list()).find((entry) => entry.id === id)).toMatchObject({ nextRunOn: '2027-04-15', lastRunOn: '2027-03-15' });
	});

	it('clamps a 31st to short months without losing the day', async () => {
		const id = await seed({ dayOfMonth: 31, nextRunOn: '2027-01-31' });
		const state = async () => (await list()).find((entry) => entry.id === id)!;

		await runDueSubscriptions(env, tickOf('2027-01-31'));
		expect((await state()).nextRunOn).toBe('2027-02-28');

		await runDueSubscriptions(env, tickOf('2027-02-28'));
		expect((await state()).nextRunOn).toBe('2027-03-31');

		await runDueSubscriptions(env, tickOf('2027-03-31'));
		expect((await state()).nextRunOn).toBe('2027-04-30');
	});

	it('lands on the 29th in a leap year', async () => {
		const id = await seed({ dayOfMonth: 30, nextRunOn: '2028-01-30' });
		await runDueSubscriptions(env, tickOf('2028-01-30'));
		expect((await list()).find((entry) => entry.id === id)!.nextRunOn).toBe('2028-02-29');
	});

	it('catches up on runs that were missed, in order', async () => {
		const id = await seed({ dayOfMonth: 15, nextRunOn: '2026-06-15' });

		expect(await runDueSubscriptions(env, tickOf('2026-09-15'))).toEqual({ posted: 4, failed: 0 });

		expect((await transactions()).map((entry) => entry.occurredOn).sort()).toEqual([
			'2026-06-15',
			'2026-07-15',
			'2026-08-15',
			'2026-09-15',
		]);
		expect((await list()).find((entry) => entry.id === id)).toMatchObject({ nextRunOn: '2026-10-15', lastRunOn: '2026-09-15' });
	});

	it('bounds how far one tick catches up, and finishes on the next', async () => {
		const id = await seed({ dayOfMonth: 10, nextRunOn: '2024-01-10' });
		const now = tickOf('2026-09-10'); // 33 runs due

		expect((await runDueSubscriptions(env, now)).posted).toBe(MAX_CATCH_UP);
		expect((await list()).find((entry) => entry.id === id)!.nextRunOn).toBe('2025-01-10');

		await runDueSubscriptions(env, now);
		await runDueSubscriptions(env, now);
		expect(await transactions()).toHaveLength(33);
		expect((await list()).find((entry) => entry.id === id)!.nextRunOn).toBe('2026-10-10');
	});

	it('skips a paused subscription', async () => {
		await seed({ dayOfMonth: 15, nextRunOn: '2027-01-15', enabled: false });

		expect(await runDueSubscriptions(env, tickOf('2027-01-15'))).toEqual({ posted: 0, failed: 0 });
		expect(await transactions()).toEqual([]);
	});

	it('does not bring back a transaction the user deleted', async () => {
		await seed({ dayOfMonth: 15, nextRunOn: '2027-01-15' });
		await runDueSubscriptions(env, tickOf('2027-01-15'));

		const [posted] = await transactions();
		expect((await call(`/transactions/${posted.id}`, { method: 'DELETE' })).status).toBe(204);

		await runDueSubscriptions(env, tickOf('2027-01-15'));
		expect(await transactions()).toEqual([]);
	});

	it('posts for each user to their own account', async () => {
		const theirs = await otherClient();
		const theirAccount = await makeAccount(theirs, { name: 'Theirs' });
		await seed({ dayOfMonth: 15, nextRunOn: '2027-01-15' });
		await env.DB.prepare(
			`INSERT INTO subscriptions (id, user_id, account_id, amount, payee, start_on, day_of_month, next_run_on)
			 VALUES ('sub_theirs', 'auth0|other-user', ?, -999, 'Theirs', '2027-01-15', 15, '2027-01-15')`,
		)
			.bind(theirAccount)
			.run();

		expect(await runDueSubscriptions(env, tickOf('2027-01-15'))).toEqual({ posted: 2, failed: 0 });

		expect(await transactions()).toEqual([expect.objectContaining({ accountId, amount: -2500 })]);
		expect(await transactions(theirs)).toEqual([expect.objectContaining({ accountId: theirAccount, amount: -999 })]);
	});

	it('refreshes the month’s cached summary', async () => {
		await seed({ dayOfMonth: 15, nextRunOn: '2027-01-15' });
		const summary = async () => await json<{ net: number; cached: boolean }>(await call('/summary?month=2027-01'));

		expect(await summary()).toMatchObject({ net: 0 });
		expect(await summary()).toMatchObject({ net: 0, cached: true });

		await runDueSubscriptions(env, tickOf('2027-01-15'));
		expect(await summary()).toMatchObject({ net: -2500, cached: false });
	});

	it('counts in the month like any other transaction', async () => {
		await seed({ dayOfMonth: 15, nextRunOn: '2027-01-15', amount: -2500 });
		await runDueSubscriptions(env, tickOf('2027-01-15'));

		const { expenses } = await json<{ expenses: number }>(await call('/summary?month=2027-01'));
		expect(Math.abs(expenses)).toBe(2500);
	});

	it('does not trigger Save the Change, nor teach the payee history', async () => {
		const savings = await makeAccount(call, { name: 'Savings' });
		await call(`/accounts/${accountId}`, { method: 'PATCH', body: JSON.stringify({ roundUpSource: true }) });
		await call('/round-up', { method: 'PATCH', body: JSON.stringify({ enabled: true, roundTo: 1000, destinationAccountId: savings }) });

		await seed({ dayOfMonth: 15, nextRunOn: '2027-01-15', amount: -4599 });
		await runDueSubscriptions(env, tickOf('2027-01-15'));

		expect(await transactions()).toHaveLength(1);
		const { payees } = await json<{ payees: unknown[] }>(await call('/payees'));
		expect(payees).toEqual([]);
	});

	it('is what the Worker’s scheduled handler runs', async () => {
		const startOn = daysFromNow(2);
		await createOne({ startOn });

		const context = createExecutionContext();
		await worker.scheduled(createScheduledController({ scheduledTime: tickOf(startOn).getTime(), cron: '0 0 * * *' }), env, context);
		await waitOnExecutionContext(context);

		expect(await transactions()).toEqual([expect.objectContaining({ occurredOn: startOn, automated: true })]);
	});
});

describe('an automated transaction', () => {
	async function postOne(): Promise<TransactionBody> {
		await seed({ dayOfMonth: 15, nextRunOn: '2027-01-15' });
		await runDueSubscriptions(env, tickOf('2027-01-15'));
		return (await transactions())[0];
	}

	const patch = (id: string, body: Record<string, unknown>) => call(`/transactions/${id}`, { method: 'PATCH', body: JSON.stringify(body) });

	it('is flagged, and an ordinary transaction is not', async () => {
		const automated = await postOne();
		await call('/transactions', { method: 'POST', body: JSON.stringify({ accountId, amount: -100, occurredOn: '2027-01-16T09:30' }) });

		expect(automated.automated).toBe(true);
		expect((await transactions()).filter((entry) => entry.automated)).toHaveLength(1);
	});

	it('can be edited like any other, except for its time of day', async () => {
		const posted = await postOne();

		expect((await patch(posted.id, { amount: -3000, notes: 'Adjusted' })).status).toBe(200);
		expect((await patch(posted.id, { occurredOn: '2027-01-20' })).status).toBe(200);
		expect((await patch(posted.id, { occurredOn: '2027-01-20T09:30' })).status).toBe(400);

		expect((await transactions())[0]).toMatchObject({ amount: -3000, notes: 'Adjusted', occurredOn: '2027-01-20', automated: true });
	});

	it('can be deleted like any other', async () => {
		const posted = await postOne();
		expect((await call(`/transactions/${posted.id}`, { method: 'DELETE' })).status).toBe(204);
		expect(await transactions()).toEqual([]);
	});

	it('lets an ordinary transaction have a time', async () => {
		const response = await call('/transactions', {
			method: 'POST',
			body: JSON.stringify({ accountId, amount: -100, occurredOn: '2027-01-16T09:30' }),
		});
		const { transaction } = await json<{ transaction: TransactionBody }>(response);

		expect((await patch(transaction.id, { occurredOn: '2027-01-16T10:45' })).status).toBe(200);
	});
});
