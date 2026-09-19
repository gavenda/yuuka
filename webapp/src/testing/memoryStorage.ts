/**
 * A `Storage` of our own, for specs to install over the global: the runtime's `localStorage` is not
 * a dependable stand-in (Node ships one of its own that needs a backing file), and the quota and
 * blocked-storage cases need a store that can be told to fail.
 */
export class MemoryStorage implements Storage {
	private items = new Map<string, string>();
	/** How many of the next `setItem` calls throw, as a full quota would. */
	failNext = 0;
	/** Whether every `setItem` throws, however many times it is retried. */
	full = false;

	get length(): number {
		return this.items.size;
	}
	clear(): void {
		this.items.clear();
	}
	getItem(key: string): string | null {
		return this.items.get(key) ?? null;
	}
	key(index: number): string | null {
		return [...this.items.keys()][index] ?? null;
	}
	removeItem(key: string): void {
		this.items.delete(key);
	}
	setItem(key: string, value: string): void {
		if (this.full || this.failNext-- > 0) throw new DOMException('full', 'QuotaExceededError');
		this.items.set(key, value);
	}
}

/** Makes `localStorage` this store, or — given a function — makes reading it throw, as blocked storage does. */
export function installLocalStorage(store: Storage | (() => never)): void {
	Object.defineProperty(globalThis, 'localStorage', {
		configurable: true,
		get: typeof store === 'function' ? store : () => store,
	});
}
