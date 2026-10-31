/**
 * The database behind the local copy of the ledger: IndexedDB `yuuka-cache`, laid out as the Android
 * app's Room database (`YuukaDatabase`) is — an object store per table, keyed and indexed the same
 * way. `cache.ts` is its DAOs; nothing else touches this module but the lifecycle below.
 *
 * It is a cache, and losing it costs nothing but a round trip, so everything here is best-effort and
 * nothing throws or rejects. A browser that refuses IndexedDB, a write that fails, a reader that
 * meets a row it cannot use: each reads as "nothing saved", and the stores load as they always did.
 * It is also a database of its own, apart from the outbox's (`yuuka-outbox`): the queue is the one
 * thing that is not a cache, so nothing done here — a clear, a trim, an upgrade that drops every
 * store — can reach it.
 *
 * **Every row is read into memory once per page**, store by store, and reads are answered from
 * there: a read resolves within the task that asked, so a screen paints from the copy before a
 * frame is drawn without it. The memory keeps the same indexes as the stores, and queries go
 * through them. A write is a list of [Op]s, applied to memory at once and then to IndexedDB in one
 * transaction, op by op, where the cascading ones walk the stores' own indexes.
 *
 * **Whose books these are** is recorded in `localStorage` (`yuuka.cache.owner`), not here: it also
 * decides whether the outbox goes, so it must outlive this database being dropped, and a tab still
 * running an older build reads it there. Every row is stamped with the owner it was written for, and
 * a read only ever sees the rows of the owner recorded now.
 */

/**
 * The database's version. Bump it when a store, an index or the shape of a stored row changes: the
 * upgrade drops every store and creates them afresh, as Room's destructive migration does, so a row
 * written under another version is never read. 1 was a single key-value `entries` store; 3 added the PSE
 * investments' `securities`, `trades` and `stock_prices`.
 */
const VERSION = 3;
const DATABASE = 'yuuka-cache';

interface TableSpec {
	keyPath: string | string[];
	/** Single-field indexes, each named after its field. A composite key's first field is looked up by too. */
	indexes?: readonly string[];
}

/**
 * Room's tables, one store each, plus `transaction_pages`. Android filters a page of transactions
 * out of its table on the device; the web asks the API for each filter set's page, so it keeps
 * which first pages it has seen, and their totals, to answer for that filter set and bound it.
 */
export const TABLES = {
	accounts: { keyPath: 'id', indexes: ['typeId'] },
	account_types: { keyPath: 'id' },
	categories: { keyPath: 'id', indexes: ['parentId'] },
	tags: { keyPath: 'id' },
	transaction_tags: { keyPath: ['transactionId', 'tagId'], indexes: ['tagId'] },
	transactions: { keyPath: 'id', indexes: ['accountId', 'categoryId', 'month', 'transferId'] },
	transaction_pages: { keyPath: 'key', indexes: ['cachedAt'] },
	budgets: { keyPath: 'id', indexes: ['queriedMonth', 'categoryId'] },
	income_plans: { keyPath: 'queriedMonth' },
	summaries: { keyPath: 'month', indexes: ['cachedAt'] },
	payees: { keyPath: 'id' },
	subscriptions: { keyPath: 'id', indexes: ['accountId', 'categoryId'] },
	settings: { keyPath: 'id' },
	round_up_rule: { keyPath: 'id' },
	securities: { keyPath: 'symbol' },
	trades: { keyPath: 'id', indexes: ['accountId', 'transactionId'] },
	stock_prices: { keyPath: ['symbol', 'day'] },
} as const satisfies Record<string, TableSpec>;

export type TableName = keyof typeof TABLES;
const TABLE_NAMES = Object.keys(TABLES) as TableName[];

/** A stored row: the entity's own fields, plus whose books it came from. */
export type StoredRow = Record<string, unknown> & { owner: string | null };

/** One step of a write. Each runs against memory, then against IndexedDB in the same order. */
export type Op =
	| { op: 'put'; table: TableName; row: Record<string, unknown> }
	| { op: 'delete'; table: TableName; key: IDBValidKey }
	/** Every row, or every row but those keyed [except]. */
	| { op: 'clear'; table: TableName; except?: IDBValidKey[] }
	/** Every row whose [field] — an index, or a composite key's first field — is [value]. */
	| { op: 'deleteWhere'; table: TableName; field: string; value: string }
	| { op: 'patchWhere'; table: TableName; field: string; value: string; patch: Record<string, unknown> }
	/** All but the [keep] rows with the largest [field], which is an index. */
	| { op: 'trim'; table: TableName; field: string; keep: number };

/** What a reader sees of one table: only the rows of the owner recorded now. */
export interface TableView {
	get(key: IDBValidKey): StoredRow | undefined;
	all(): StoredRow[];
	where(field: string, value: IDBValidKey): StoredRow[];
}

export type DatabaseView = (table: TableName) => TableView;

/** Whose books the copy holds. The one `yuuka.cache.*` key left in `localStorage`. */
const OWNER_KEY = 'yuuka.cache.owner';
/** Where the copy used to live, a key per entry; whatever is still there is discarded on first open. */
const LEGACY_PREFIX = 'yuuka.cache.';

const keyOf = (key: IDBValidKey): string => (Array.isArray(key) ? key.map(String).join('\u0000') : String(key));

function primaryKey(spec: TableSpec, row: Record<string, unknown>): IDBValidKey {
	return Array.isArray(spec.keyPath) ? spec.keyPath.map((field) => row[field] as IDBValidKey) : (row[spec.keyPath] as IDBValidKey);
}

/** The fields a table is looked up by in memory: its indexes, and a composite key's first field. */
function lookupFields(spec: TableSpec): string[] {
	return [...(spec.indexes ?? []), ...(Array.isArray(spec.keyPath) ? [spec.keyPath[0]!] : [])];
}

/** One store's rows in memory, with the same lookups its IndexedDB store has. */
class MemoryTable {
	private readonly rows = new Map<string, StoredRow>();
	private readonly lookups = new Map<string, Map<string, Set<string>>>();

	readonly spec: TableSpec;

	constructor(spec: TableSpec) {
		this.spec = spec;
		for (const field of lookupFields(spec)) this.lookups.set(field, new Map());
	}

	keyOf(row: Record<string, unknown>): string {
		return keyOf(primaryKey(this.spec, row));
	}

	get(key: IDBValidKey): StoredRow | undefined {
		return this.rows.get(keyOf(key));
	}

	all(): StoredRow[] {
		return [...this.rows.values()];
	}

	/** Like IndexedDB, a row whose field is null or missing is in no lookup. */
	where(field: string, value: IDBValidKey): StoredRow[] {
		const keys = this.lookups.get(field)?.get(keyOf(value));
		if (keys) return [...keys].map((key) => this.rows.get(key)!);
		return this.lookups.has(field) ? [] : this.all().filter((row) => row[field] === value);
	}

	put(row: StoredRow): void {
		const key = this.keyOf(row);
		this.remove(key);
		this.rows.set(key, row);
		for (const [field, lookup] of this.lookups) {
			const value = row[field];
			if (value === null || value === undefined) continue;
			const slot = keyOf(value as IDBValidKey);
			lookup.set(slot, (lookup.get(slot) ?? new Set()).add(key));
		}
	}

	remove(key: string): void {
		const row = this.rows.get(key);
		if (!row) return;
		this.rows.delete(key);
		for (const [field, lookup] of this.lookups) {
			const value = row[field];
			if (value !== null && value !== undefined) lookup.get(keyOf(value as IDBValidKey))?.delete(key);
		}
	}

	clear(): void {
		this.rows.clear();
		for (const lookup of this.lookups.values()) lookup.clear();
	}
}

interface LocalCopy {
	db: IDBDatabase;
	tables: Record<TableName, MemoryTable>;
}

/**
 * The recorded owner, or undefined when `localStorage` cannot be read. Nothing is cached then:
 * nothing could say whose it was.
 */
function recordedOwner(): string | null | undefined {
	try {
		return localStorage.getItem(OWNER_KEY);
	} catch {
		return undefined;
	}
}

/** Removes what the `localStorage` copy left behind, which shares its quota with the session and the preferences. */
function discardLegacyCopy(): void {
	try {
		const stale: string[] = [];
		for (let index = 0; index < localStorage.length; index++) {
			const key = localStorage.key(index);
			if (key?.startsWith(LEGACY_PREFIX) && key !== OWNER_KEY) stale.push(key);
		}
		for (const key of stale) localStorage.removeItem(key);
	} catch {
		// Blocked: then nothing was ever written there either.
	}
}

let opening: Promise<LocalCopy | null> | null = null;

/** Opens the database and reads every store into memory, once per page; null when IndexedDB is refused or blocked. */
function open(): Promise<LocalCopy | null> {
	if (opening) return opening;
	discardLegacyCopy();

	opening = new Promise<LocalCopy | null>((resolve) => {
		let settled = false;
		const settle = (copy: LocalCopy | null) => {
			settled = true;
			resolve(copy);
		};

		let request: IDBOpenDBRequest;
		try {
			request = indexedDB.open(DATABASE, VERSION);
		} catch {
			settle(null);
			return;
		}

		request.onupgradeneeded = () => {
			const db = request.result;
			// A cache is never migrated: whatever an older layout held is dropped and fetched again.
			for (const name of Array.from(db.objectStoreNames)) db.deleteObjectStore(name);
			for (const name of TABLE_NAMES) {
				const spec: TableSpec = TABLES[name];
				const store = db.createObjectStore(name, { keyPath: spec.keyPath });
				for (const field of spec.indexes ?? []) store.createIndex(field, field);
			}
		};
		request.onsuccess = () => {
			const db = request.result;
			// It opened after all, once this page had already given up on it.
			if (settled) {
				db.close();
				return;
			}
			// A newer build changing the layout from another tab: step aside rather than hold it up.
			// This page carries on answering from memory.
			db.onversionchange = () => db.close();

			try {
				const transaction = db.transaction(TABLE_NAMES, 'readonly');
				const reads = TABLE_NAMES.map((name) => [name, transaction.objectStore(name).getAll() as IDBRequest<StoredRow[]>] as const);
				transaction.oncomplete = () => {
					const tables = {} as Record<TableName, MemoryTable>;
					for (const [name, read] of reads) {
						tables[name] = new MemoryTable(TABLES[name]);
						for (const row of read.result) tables[name].put(row);
					}
					settle({ db, tables });
				};
				transaction.onabort = () => {
					db.close();
					settle(null);
				};
			} catch {
				db.close();
				settle(null);
			}
		};
		request.onerror = () => settle(null);
		// Another tab holds an older layout open and will not let go: carry on without a copy.
		request.onblocked = () => settle(null);
	});

	return opening;
}

function viewOf(copy: LocalCopy, owner: string | null): DatabaseView {
	const mine = (row: StoredRow | undefined) => (row?.owner === owner ? row : undefined);
	return (name) => {
		const table = copy.tables[name];
		return {
			get: (key) => mine(table.get(key)),
			all: () => table.all().filter((row) => row.owner === owner),
			where: (field, value) => table.where(field, value).filter((row) => row.owner === owner),
		};
	};
}

function applyToMemory(copy: LocalCopy, op: Op, owner: string | null): void {
	const table = copy.tables[op.table];
	switch (op.op) {
		case 'put':
			table.put({ ...op.row, owner });
			return;
		case 'delete':
			table.remove(keyOf(op.key));
			return;
		case 'clear': {
			if (!op.except?.length) return table.clear();
			const kept = new Set(op.except.map(keyOf));
			for (const row of table.all()) if (!kept.has(table.keyOf(row))) table.remove(table.keyOf(row));
			return;
		}
		case 'deleteWhere':
			for (const row of table.where(op.field, op.value)) table.remove(table.keyOf(row));
			return;
		case 'patchWhere':
			for (const row of table.where(op.field, op.value)) table.put({ ...row, ...op.patch });
			return;
		case 'trim': {
			const newestFirst = table.all().sort((a, b) => Number(b[op.field]) - Number(a[op.field]));
			for (const row of newestFirst.slice(op.keep)) table.remove(table.keyOf(row));
			return;
		}
	}
}

/** Walks [source] over [range], calling [visit] on each position, then [done]. */
function walk(
	source: IDBObjectStore | IDBIndex,
	range: IDBKeyRange | null,
	direction: IDBCursorDirection,
	visit: (cursor: IDBCursorWithValue) => void,
	done: () => void,
): void {
	const request = source.openCursor(range, direction);
	request.onsuccess = () => {
		const cursor = request.result;
		if (!cursor) return done();
		visit(cursor);
		cursor.continue();
	};
}

/**
 * Applies [ops] to IndexedDB in order, inside [store]'s transaction. A cursor op finishes before the
 * next op starts, so a put that follows a delete-where cannot be caught by its cursor.
 */
function applyToStores(ops: Op[], owner: string | null, store: (name: TableName) => IDBObjectStore): void {
	let next = 0;
	const step = (): void => {
		while (next < ops.length) {
			const op = ops[next++]!;
			const target = store(op.table);
			const spec: TableSpec = TABLES[op.table];

			switch (op.op) {
				case 'put':
					target.put({ ...op.row, owner });
					break;
				case 'delete':
					target.delete(op.key);
					break;
				case 'clear': {
					if (!op.except?.length) {
						target.clear();
						break;
					}
					const kept = new Set(op.except.map(keyOf));
					return walk(target, null, 'next', (cursor) => void (kept.has(keyOf(cursor.primaryKey)) || cursor.delete()), step);
				}
				case 'deleteWhere':
				case 'patchWhere': {
					const byIndex = spec.indexes?.includes(op.field) ?? false;
					const byKey = Array.isArray(spec.keyPath) && spec.keyPath[0] === op.field;
					const source = byIndex ? target.index(op.field) : target;
					// An index answers for its value; a composite key's first field, for every key that starts
					// with it. Any other field is a walk over the whole store.
					const range = byIndex ? IDBKeyRange.only(op.value) : byKey ? IDBKeyRange.bound([op.value], [op.value, []]) : null;
					const visit = (cursor: IDBCursorWithValue) => {
						if (range === null && cursor.value[op.field] !== op.value) return;
						if (op.op === 'deleteWhere') cursor.delete();
						else cursor.update({ ...cursor.value, ...op.patch });
					};
					return walk(source, range, 'next', visit, step);
				}
				case 'trim': {
					let seen = 0;
					return walk(target.index(op.field), null, 'prev', (cursor) => void (++seen > op.keep && cursor.delete()), step);
				}
			}
		}
	};
	step();
}

/** Runs [ops] in one read-write transaction, settling once it has committed or failed. Never rejects. */
function commit(copy: LocalCopy, ops: Op[], owner: string | null): Promise<void> {
	if (ops.length === 0) return Promise.resolve();
	const scope = [...new Set(ops.map((op) => op.table))];

	return new Promise<void>((resolve) => {
		try {
			const transaction = copy.db.transaction(scope, 'readwrite');
			transaction.oncomplete = () => resolve();
			transaction.onabort = () => resolve();
			applyToStores(ops, owner, (name) => transaction.objectStore(name));
		} catch {
			// The connection was closed under it — a newer build, or the site's data cleared. Memory
			// still answers for this page; the next one reads whatever is there.
			resolve();
		}
	});
}

/**
 * Answers [reader] from memory, or null when there is no copy. A reader that throws — a row it
 * cannot use — reads as no copy too.
 */
export async function read<T>(reader: (db: DatabaseView) => T): Promise<T | null> {
	const copy = await open();
	const owner = recordedOwner();
	if (!copy || owner === undefined) return null;

	try {
		return reader(viewOf(copy, owner));
	} catch {
		return null;
	}
}

/**
 * Writes [ops] — or the ops [build] works out from what is held now — to memory at once and to
 * IndexedDB behind it. Settles once IndexedDB has committed or failed; a store need not wait.
 */
export async function write(build: Op[] | ((db: DatabaseView) => Op[])): Promise<void> {
	const copy = await open();
	const owner = recordedOwner();
	if (!copy || owner === undefined) return;

	let ops: Op[];
	try {
		ops = typeof build === 'function' ? build(viewOf(copy, owner)) : build;
		for (const op of ops) applyToMemory(copy, op, owner);
	} catch {
		return;
	}

	await commit(copy, ops, owner);
}

/**
 * Starts reading the copy into memory, so it is ready by the time a screen asks. Called at start-up,
 * alongside the session being restored rather than after it.
 */
export async function warmCache(): Promise<void> {
	await open();
}

/**
 * Discards the whole local copy, every store. Whose it was stays recorded: until someone else signs
 * in it is the same person, and a session that merely expired must not cost them the unsent queue.
 */
export async function clearCache(): Promise<void> {
	const copy = await open();
	if (!copy) return;

	const ops: Op[] = TABLE_NAMES.map((table) => ({ op: 'clear', table }));
	for (const op of ops) applyToMemory(copy, op, null);
	await commit(copy, ops, null);
}

/**
 * Ties the local copy to whoever is signed in, and resolves once it is ready to be read. It holds
 * one person's books, so a different account signing in on the same browser must never be shown
 * it, even for the instant before its own data arrives. True when the owner changed — the router's
 * cue to discard the outbox as well.
 */
export async function adoptCacheFor(owner: string | undefined): Promise<boolean> {
	let recorded: string | null;
	try {
		recorded = localStorage.getItem(OWNER_KEY);
	} catch {
		// No marker can be kept, so no copy is either (see read): there is nothing to hand over.
		return false;
	}

	if (recorded === owner) {
		await open();
		return false;
	}

	// The marker moves first: from here on a read refuses what was written for anyone else, even
	// before those rows are gone.
	try {
		if (owner) localStorage.setItem(OWNER_KEY, owner);
		else localStorage.removeItem(OWNER_KEY);
	} catch {
		// A full quota. At least stop vouching for the previous owner; unrecorded, the next adoption
		// clears again, which is the safe direction.
		try {
			localStorage.removeItem(OWNER_KEY);
		} catch {
			// Blocked outright.
		}
	}

	await clearCache();
	return true;
}
