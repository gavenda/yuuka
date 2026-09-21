/**
 * The queue of changes that have not reached the API yet.
 *
 * This is the one thing in the browser that is **not** a cache. Everything in
 * `cache.ts` can be thrown away and refetched; an entry here is a change the
 * user made that exists nowhere else yet, so losing it loses their work. That
 * is why it is in IndexedDB rather than beside the cache in `localStorage`:
 * the cache drops its oldest entries when the quota is tight, and the queue
 * must never be what gets dropped.
 *
 * Two properties the rest of the app depends on:
 *
 * - **Order is preserved.** Entries are keyed by an auto-incrementing sequence
 *   and always read in that order. Creating an account and then spending from
 *   it is two entries, and the second only works after the first.
 * - **An entry is removed only once the server has accounted for it.** A flush
 *   that never got an answer leaves the queue as it was, and sends again. The
 *   API is built to be sent the same batch twice, so a duplicate send is
 *   cheaper than a lost change.
 *
 * The queue holds one person's unsent work, so it is emptied when someone else
 * signs in — the same rule the cache follows, for the same reason.
 */

import type { ChangeSlice } from './slices';

const DATABASE = 'yuuka-outbox';
const STORE = 'operations';
const VERSION = 1;

/** One queued call, in the shape `POST /api/sync/batch` takes. */
export interface OutboxEntry {
	/** Assigned by IndexedDB; the queue's order and the key to remove it by. */
	seq?: number;
	/** The client's own name for this operation, echoed back in the batch's results. */
	opId: string;
	method: 'POST' | 'PATCH' | 'PUT' | 'DELETE';
	/** An `/api/...` path, exactly as the same call would look online. */
	path: string;
	/** When the user made the change, not when it is sent — this is what a stale edit is judged against. */
	at: string;
	/** What it touches, for the server's staleness check. */
	entity?: string;
	id?: string;
	body?: unknown;
	/** Which slices to refresh once this lands or fails, so the screens catch up. */
	slices: ChangeSlice[];
	/** How many times sending this has been attempted, so a permanently broken entry can be given up on. */
	attempts: number;
	/** Why the last attempt failed, kept so the interface can say what went wrong. */
	lastError?: string;
}

let opening: Promise<IDBDatabase | null> | null = null;

/**
 * Opens the database, once. A browser that refuses IndexedDB — a locked-down
 * private window — resolves to null, and every function here then behaves as
 * if the queue were empty: writes go straight to the API and fail when
 * offline, which is how the app behaved before there was a queue at all.
 */
function open(): Promise<IDBDatabase | null> {
	if (opening) return opening;

	opening = new Promise<IDBDatabase | null>((resolve) => {
		let request: IDBOpenDBRequest;
		try {
			request = indexedDB.open(DATABASE, VERSION);
		} catch {
			resolve(null);
			return;
		}

		request.onupgradeneeded = () => {
			const db = request.result;
			if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE, { keyPath: 'seq', autoIncrement: true });
		};
		request.onsuccess = () => resolve(request.result);
		request.onerror = () => resolve(null);
		request.onblocked = () => resolve(null);
	});

	return opening;
}

function run<T>(mode: IDBTransactionMode, work: (store: IDBObjectStore) => IDBRequest<T>): Promise<T | null> {
	return open().then(
		(db) =>
			new Promise<T | null>((resolve) => {
				if (!db) {
					resolve(null);
					return;
				}

				try {
					const transaction = db.transaction(STORE, mode);
					const request = work(transaction.objectStore(STORE));
					request.onsuccess = () => resolve(request.result);
					request.onerror = () => resolve(null);
					transaction.onabort = () => resolve(null);
				} catch {
					resolve(null);
				}
			}),
	);
}

/** Adds an entry to the back of the queue and returns it with the sequence it was given. */
export async function enqueue(entry: Omit<OutboxEntry, 'seq' | 'attempts'>): Promise<OutboxEntry | null> {
	const record: OutboxEntry = { ...entry, attempts: 0 };
	const seq = await run<IDBValidKey>('readwrite', (store) => store.add(record) as IDBRequest<IDBValidKey>);
	if (seq === null) return null;

	return { ...record, seq: Number(seq) };
}

/** Everything waiting, oldest first. */
export async function pending(): Promise<OutboxEntry[]> {
	return (await run<OutboxEntry[]>('readonly', (store) => store.getAll() as IDBRequest<OutboxEntry[]>)) ?? [];
}

/** How many changes have not reached the API. What the interface counts when it says "3 unsynced". */
export async function pendingCount(): Promise<number> {
	return (await run<number>('readonly', (store) => store.count() as IDBRequest<number>)) ?? 0;
}

/** Drops entries the server has accounted for — applied, dropped as stale, or given up on. */
export async function remove(sequences: number[]): Promise<void> {
	if (sequences.length === 0) return;

	const db = await open();
	if (!db) return;

	await new Promise<void>((resolve) => {
		try {
			const transaction = db.transaction(STORE, 'readwrite');
			const store = transaction.objectStore(STORE);
			for (const seq of sequences) store.delete(seq);
			transaction.oncomplete = () => resolve();
			transaction.onerror = () => resolve();
			transaction.onabort = () => resolve();
		} catch {
			resolve();
		}
	});
}

/** Records that sending these failed, so a permanently broken entry can eventually be given up on. */
export async function recordFailure(entries: { seq: number; error: string }[]): Promise<void> {
	if (entries.length === 0) return;

	const db = await open();
	if (!db) return;

	await new Promise<void>((resolve) => {
		try {
			const transaction = db.transaction(STORE, 'readwrite');
			const store = transaction.objectStore(STORE);
			for (const { seq, error } of entries) {
				const read = store.get(seq);
				read.onsuccess = () => {
					const record = read.result as OutboxEntry | undefined;
					if (!record) return;
					store.put({ ...record, attempts: record.attempts + 1, lastError: error });
				};
			}
			transaction.oncomplete = () => resolve();
			transaction.onerror = () => resolve();
			transaction.onabort = () => resolve();
		} catch {
			resolve();
		}
	});
}

/** Empties the queue. Signing out, or someone else signing in — the unsent work is not theirs. */
export async function clearOutbox(): Promise<void> {
	await run('readwrite', (store) => store.clear() as IDBRequest<undefined>);
}
