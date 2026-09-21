/**
 * Applying a batch of mutations that were made offline.
 *
 * The clients are offline-first: a change is written to the local database and
 * queued, and the queue is drained when there is a connection. What arrives
 * here is therefore a list of calls the app would have made one at a time, in
 * the order the user made them, some of them minutes or days old.
 *
 * **A queued call is the same call.** Every operation names a method, an
 * `/api/...` path and a body — exactly what an online client would have sent —
 * and is dispatched through this same Worker's router. Nothing here knows what
 * a transfer is, or which category kinds go where, or when a round-up
 * triggers: those live in the routes, and a batch reaches them by the front
 * door. Re-implementing them here is how the two would drift, and an ownership
 * check that only the online path performs is a hole in the batch path.
 *
 * **Order is the user's order.** Operations are applied one at a time, never
 * in parallel: creating an account and then a transaction on it is two
 * operations, and the second is only valid after the first. A failure does not
 * stop the batch, because the operations after it are usually unrelated — but
 * the ones that did depend on it will fail too, and the client is told about
 * each.
 *
 * **An old edit does not overwrite a new one.** Each operation carries the
 * moment the user made it. If the row it names has moved since — the other
 * device edited it, and that reached the server first — the operation is
 * dropped as `stale` rather than applied. That is the whole conflict policy:
 * last write wins, judged by when the user acted rather than by when the
 * connection came back.
 *
 * **Sending the batch twice is safe.** Creates carry the id the client already
 * gave the row, so a repeat resolves to the row that is already there; deleting
 * something already gone reports success rather than a 404. A client that lost
 * the answer can always just send again.
 */

import { HttpError } from './errors';
import type { ChangeSlice } from './notify';
import { slicesForPath } from './notify';
import type { syncOperationSchema } from './schemas';
import type { z } from 'zod';

type SyncOperation = z.infer<typeof syncOperationSchema>;

/** How an operation ended, as the client is told. */
export type SyncStatus = 'applied' | 'stale' | 'failed';

export interface SyncResult {
	opId: string;
	status: SyncStatus;
	/** The HTTP status the route answered with; 0 for an operation that never reached one. */
	code: number;
	/** The route's JSON answer, which is what the client writes over its provisional row. */
	body?: unknown;
	error?: string;
}

/**
 * Where each entity's `updated_at` lives.
 *
 * `perUser` rows are the singletons — one settings row, one round-up rule —
 * which are found by owner rather than by id.
 */
const GUARDS: Record<string, { table: string; perUser?: boolean }> = {
	account: { table: 'accounts' },
	accountType: { table: 'account_types' },
	category: { table: 'categories' },
	tag: { table: 'tags' },
	transaction: { table: 'transactions' },
	subscription: { table: 'subscriptions' },
	budget: { table: 'budgets' },
	settings: { table: 'users', perUser: true },
	roundUpRule: { table: 'round_up_rules', perUser: true },
};

/**
 * When the row this operation names was last written, or null if there is no
 * row to be stale against — it has never existed, was deleted elsewhere, or
 * this is a create.
 *
 * A transfer has no `updated_at` of its own: the parent row is just an owner
 * and a kind, and what a user edits are its legs. The newest leg is therefore
 * when the transfer last moved.
 */
async function lastWrittenAt(db: D1Database, userId: string, operation: SyncOperation): Promise<string | null> {
	if (!operation.entity) return null;

	if (operation.entity === 'transfer') {
		if (!operation.id) return null;
		const row = await db
			.prepare('SELECT MAX(updated_at) AS updated_at FROM transactions WHERE transfer_id = ? AND user_id = ?')
			.bind(operation.id, userId)
			.first<{ updated_at: string | null }>();
		return row?.updated_at ?? null;
	}

	const guard = GUARDS[operation.entity];
	if (!guard) return null;

	// The table name is never caller-supplied — `entity` is a closed enum and
	// this map is the only thing that turns one into a table.
	const row = guard.perUser
		? await db.prepare(`SELECT updated_at FROM ${guard.table} WHERE id = ?`).bind(userId).first<{ updated_at: string }>()
		: operation.id
			? await db
					.prepare(`SELECT updated_at FROM ${guard.table} WHERE id = ? AND user_id = ?`)
					.bind(operation.id, userId)
					.first<{ updated_at: string }>()
			: null;

	return row?.updated_at ?? null;
}

/**
 * Dispatches one already-authenticated request through the Worker's own router.
 *
 * It takes the bindings and execution context explicitly rather than closing
 * over them, so nothing here depends on module state that a second concurrent
 * request could have moved.
 */
export type Dispatch = (request: Request, env: Env, ctx: DispatchContext) => Promise<Response>;

/**
 * The part of an execution context a dispatch needs. Hono's own
 * `ExecutionContext` and the Workers runtime's differ in ways that do not
 * matter here, and naming only what is used keeps the two from having to agree.
 */
export interface DispatchContext {
	waitUntil(promise: Promise<unknown>): void;
	passThroughOnException(): void;
}

/**
 * What the client's queue drains into.
 *
 * `origin` is the request the batch arrived on: its URL gives the operations
 * an origin to be built against, and its `Authorization` and `X-Yuuka-Device`
 * headers are copied onto each one so a sub-request is authenticated as the
 * same person, by the same token, and never by anything in the batch body.
 */
export async function applyBatch(
	env: Env,
	ctx: DispatchContext,
	userId: string,
	origin: Request,
	operations: SyncOperation[],
	dispatch: Dispatch,
): Promise<{ results: SyncResult[]; slices: ChangeSlice[] }> {
	const db = env.DB;
	const results: SyncResult[] = [];
	const slices = new Set<ChangeSlice>();

	const headers = new Headers({ 'Content-Type': 'application/json' });
	const authorization = origin.headers.get('Authorization');
	if (authorization) headers.set('Authorization', authorization);
	const device = origin.headers.get('X-Yuuka-Device');
	if (device) headers.set('X-Yuuka-Device', device);
	// Tells the change notifier to stay quiet: one push goes out for the whole
	// batch, not one per operation, so a day's queue does not wake the other
	// device forty times.
	headers.set('X-Yuuka-Batch', '1');

	for (const operation of operations) {
		try {
			const seen = await lastWrittenAt(db, userId, operation);
			if (seen && seen > operation.at) {
				results.push({ opId: operation.opId, status: 'stale', code: 409 });
				// The row the client holds is out of date either way, so it still
				// needs to refresh what this operation touched.
				for (const slice of slicesForPath(operation.path)) slices.add(slice);
				continue;
			}

			const request = new Request(new URL(operation.path, origin.url), {
				method: operation.method,
				headers,
				body: operation.method === 'DELETE' || operation.body === undefined ? undefined : JSON.stringify(operation.body),
			});

			const response = await dispatch(request, env, ctx);
			const text = await response.text();
			const body = text ? safeJson(text) : undefined;

			// Deleting something that is already gone is what the user asked for.
			const alreadyGone = operation.method === 'DELETE' && response.status === 404;

			if (response.ok || alreadyGone) {
				results.push({ opId: operation.opId, status: 'applied', code: response.status, body });
				for (const slice of slicesForPath(operation.path)) slices.add(slice);
			} else {
				results.push({
					opId: operation.opId,
					status: 'failed',
					code: response.status,
					body,
					error: errorMessage(body) ?? `Request failed with ${response.status}.`,
				});
			}
		} catch (error) {
			results.push({
				opId: operation.opId,
				status: 'failed',
				code: error instanceof HttpError ? error.status : 0,
				error: error instanceof Error ? error.message : 'Unknown error.',
			});
		}
	}

	return { results, slices: [...slices] };
}

function safeJson(text: string): unknown {
	try {
		return JSON.parse(text);
	} catch {
		return text;
	}
}

function errorMessage(body: unknown): string | null {
	if (body && typeof body === 'object' && 'error' in body && typeof (body as { error: unknown }).error === 'string') {
		return (body as { error: string }).error;
	}
	return null;
}
