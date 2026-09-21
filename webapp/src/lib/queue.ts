/**
 * Local-first writes, and the batch that drains them.
 *
 * Every mutation in the app goes through `queued()`, and `queued()` never waits
 * for the network. It writes the change to the outbox, hands the caller a
 * provisional answer built locally — the row the user just made, with the
 * fields the server would have filled in worked out here — and returns. The
 * screen updates at once, whether there is a connection or not, because
 * nothing about the path it took depends on there being one.
 *
 * The queue drains separately: right away when there is a connection, on the
 * browser reporting one again, when the tab is looked at, and on a slow timer
 * behind all of that. It drains as one `POST /api/sync/batch`, in the order the
 * user made the changes, which is what makes "spend from the account I just
 * made" work after an hour with no signal.
 *
 * Three things follow from writing first and sending later:
 *
 * - **The provisional answer is a guess, and the server's answer replaces it.**
 *   Once a batch lands, the slices it touched are refetched, so any figure the
 *   client worked out approximately — a balance, a running total, a summary —
 *   is corrected by the only party that can be sure.
 * - **A rejection arrives late.** A name the server considers a duplicate is
 *   only known to be one when the batch lands, which may be a day later. That
 *   is reported as a snackbar and the optimistic row is dropped by the refresh,
 *   rather than pretending the save is still in question.
 * - **Sending twice is better than losing one.** An entry is removed only when
 *   the server has accounted for it, so a flush that never got an answer sends
 *   again. The API is built for that.
 */

import { ref, computed, readonly } from 'vue';
import { request } from './http';
import { deviceId } from './device';
import { clearOutbox, enqueue, pending, pendingCount, recordFailure, remove, type OutboxEntry } from './outbox';
import { slicesForPath, type ChangeSlice } from './slices';
import { showSnackbar } from './snackbar';

/** Given up on after this many failed sends, so one impossible change cannot block the queue forever. */
const MAX_ATTEMPTS = 8;

/** The slow heartbeat behind the event-driven flushes, for a connection that came back without the browser noticing. */
const HEARTBEAT_MS = 60_000;

/** What the server says about each operation it was sent. */
interface SyncResult {
	opId: string;
	status: 'applied' | 'stale' | 'failed';
	code: number;
	body?: unknown;
	error?: string;
}

const unsent = ref(0);
const flushing = ref(false);

/** How many changes have not reached the API. Zero is "everything is saved". */
export const unsentChanges = readonly(unsent);
/** Whether a batch is in flight, for the interface to say "syncing". */
export const isSyncing = readonly(flushing);
/** Whether there is anything to say about sync at all. */
export const hasUnsentChanges = computed(() => unsent.value > 0);

/**
 * Refetches the slices a landed batch touched. Registered by `sync.ts`, which
 * is where the stores are known — the queue must not import them, or every
 * store would import the queue and itself back.
 */
type SliceRefresher = (slices: ChangeSlice[]) => Promise<void>;

let refreshSlices: SliceRefresher = async () => {};

export function setSliceRefresher(refresher: SliceRefresher): void {
	refreshSlices = refresher;
}

async function countUnsent(): Promise<void> {
	unsent.value = await pendingCount();
}

/** What a mutation needs to say about itself to be queued. */
export interface QueuedCall<T> {
	method: OutboxEntry['method'];
	/** The `/api/...` path, exactly as the online call would use. */
	path: string;
	body?: unknown;
	/** What it touches, so the server can drop it if the row has since moved on. */
	entity?: string;
	id?: string;
	/**
	 * The answer to hand back now, in the shape the API would have returned.
	 * Built from what the client can know: the row the user just typed, plus the
	 * names and totals it can work out from the ledger it holds.
	 */
	optimistic: T;
}

/**
 * Queues a mutation and answers immediately with its provisional result.
 *
 * `at` is stamped here, when the user made the change, rather than when it is
 * sent — that is what the server judges a stale edit against, and a change made
 * on a plane should lose to one made on the ground an hour later, not win by
 * arriving second.
 */
export async function queued<T>(call: QueuedCall<T>): Promise<T> {
	const entry = await enqueue({
		opId: crypto.randomUUID(),
		method: call.method,
		path: call.path,
		at: new Date().toISOString(),
		entity: call.entity,
		id: call.id,
		body: call.body,
		slices: slicesForPath(call.path),
	});

	// No queue to write to — a private window with IndexedDB blocked. Fall back
	// to the way the app worked before there was one: send it now, and let the
	// caller see the failure if there is no connection.
	if (!entry) {
		return await request<T>(call.path.replace(/^\/api/, ''), {
			method: call.method,
			body: call.body === undefined ? undefined : JSON.stringify(call.body),
		});
	}

	await countUnsent();
	void flush();

	return call.optimistic;
}

let queuedFlush: Promise<void> | null = null;

/**
 * Sends everything waiting, as one batch.
 *
 * Only one runs at a time: a second call while one is in flight waits for it
 * rather than sending the same entries again. Nothing here decides on
 * `navigator.onLine` — a browser can report a connection that leads nowhere —
 * so a flush is always attempted and an unreachable API simply leaves the queue
 * as it was.
 */
export async function flush(): Promise<void> {
	if (queuedFlush) return await queuedFlush;

	queuedFlush = (async () => {
		flushing.value = true;
		try {
			await drain();
		} finally {
			flushing.value = false;
			queuedFlush = null;
			await countUnsent();
		}
	})();

	return await queuedFlush;
}

async function drain(): Promise<void> {
	const entries = await pending();
	if (entries.length === 0) return;

	const operations = entries.map((entry) => ({
		opId: entry.opId,
		method: entry.method,
		path: entry.path,
		at: entry.at,
		entity: entry.entity,
		id: entry.id,
		body: entry.body,
	}));

	let results: SyncResult[];
	try {
		({ results } = await request<{ results: SyncResult[] }>('/sync/batch', {
			method: 'POST',
			headers: { 'x-yuuka-device': deviceId() },
			body: JSON.stringify({ operations }),
		}));
	} catch (error) {
		// Still offline, or the session expired. The queue is untouched and will
		// be sent again; the only thing recorded is that the attempt happened, so
		// an entry the server will never accept is eventually given up on.
		await recordFailure(
			entries
				.filter((entry): entry is OutboxEntry & { seq: number } => entry.seq !== undefined)
				.map((entry) => ({
					seq: entry.seq,
					error: error instanceof Error ? error.message : 'Could not reach the API.',
				})),
		);
		return;
	}

	const bySeq = new Map(entries.filter((entry) => entry.seq !== undefined).map((entry) => [entry.opId, entry]));
	const settled: number[] = [];
	const touched = new Set<ChangeSlice>();
	const rejections: string[] = [];

	for (const result of results) {
		const entry = bySeq.get(result.opId);
		if (!entry?.seq) continue;

		if (result.status === 'applied' || result.status === 'stale') {
			settled.push(entry.seq);
			for (const slice of entry.slices) touched.add(slice);
			continue;
		}

		// The server answered, and said no. Retrying a 4xx would only be told the
		// same thing, so it is given up on at once rather than after eight tries —
		// those are for a batch that never got through.
		const permanent = result.code >= 400 && result.code < 500;
		if (permanent || entry.attempts + 1 >= MAX_ATTEMPTS) {
			settled.push(entry.seq);
			for (const slice of entry.slices) touched.add(slice);
			if (result.error) rejections.push(result.error);
		} else {
			await recordFailure([{ seq: entry.seq, error: result.error ?? `Failed with ${result.code}.` }]);
		}
	}

	await remove(settled);

	// The provisional rows are replaced by what the server actually has, which
	// is also what takes a rejected change back off the screen.
	if (touched.size > 0) await refreshSlices([...touched]);

	if (rejections.length === 1) showSnackbar(`Not saved: ${rejections[0]}`);
	else if (rejections.length > 1) showSnackbar(`${rejections.length} changes could not be saved.`);
}

/** Forgets the unsent work. Signing out, or someone else signing in — it is not theirs to send. */
export async function discardQueue(): Promise<void> {
	await clearOutbox();
	await countUnsent();
}

let started = false;

/**
 * Starts draining. Called once, from `main.ts`, after the session has settled.
 *
 * The four triggers overlap deliberately: `online` is the one that matters and
 * the one browsers are least reliable about, so a tab being looked at and a
 * slow timer both cover for it, and `flush` is cheap when there is nothing to
 * send.
 */
export function startQueue(): void {
	if (started) return;
	started = true;

	void countUnsent();
	void flush();

	window.addEventListener('online', () => void flush());
	document.addEventListener('visibilitychange', () => {
		if (document.visibilityState === 'visible') void flush();
	});
	window.setInterval(() => void flush(), HEARTBEAT_MS);
}
