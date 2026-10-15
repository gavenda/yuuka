import { IDBFactory, IDBKeyRange } from 'fake-indexeddb';

/**
 * An empty IndexedDB of the spec's own, from `fake-indexeddb`: the test DOM (happy-dom) has none.
 * It is installed over the globals, so the code under test opens it exactly as it would the
 * browser's. Each call is a fresh, empty one; a module that already opened the last one keeps it.
 */
export function installIndexedDB(): void {
	Object.defineProperty(globalThis, 'indexedDB', { configurable: true, writable: true, value: new IDBFactory() });
	Object.defineProperty(globalThis, 'IDBKeyRange', { configurable: true, writable: true, value: IDBKeyRange });
}

/** Makes reaching IndexedDB throw, as a browser that refuses it does. */
export function blockIndexedDB(): void {
	Object.defineProperty(globalThis, 'indexedDB', {
		configurable: true,
		get: () => {
			throw new DOMException('blocked', 'SecurityError');
		},
	});
}
