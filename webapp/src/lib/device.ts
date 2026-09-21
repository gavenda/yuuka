/**
 * This install's own name for itself.
 *
 * It exists for one reason: so the server can leave this device out when it
 * tells the user's other devices that something changed. The device that made
 * the change already has the API's answer, and pushing it back would only make
 * it refetch what it just wrote.
 *
 * It is not an identity and not a secret — it says nothing about who is signed
 * in, and a request carrying someone else's would achieve only that they miss
 * one push. It survives a signed-in user changing, because it describes the
 * browser rather than the person; what does not survive is the registration
 * token, which `push.ts` re-registers per user.
 *
 * Losing it costs one redundant refresh, so every accessor here is best-effort
 * in the same way `cache.ts` is: blocked storage reads as "no id yet" and the
 * app carries on with one made for this page load.
 */

const KEY = 'yuuka.device-id';

function storage(): Storage | null {
	try {
		return window.localStorage;
	} catch {
		return null;
	}
}

function mint(): string {
	return `web-${crypto.randomUUID()}`;
}

let cached: string | null = null;

/** This install's id, minted and stored the first time it is asked for. */
export function deviceId(): string {
	if (cached) return cached;

	const store = storage();
	const saved = store?.getItem(KEY);
	if (saved) {
		cached = saved;
		return saved;
	}

	const fresh = mint();
	try {
		store?.setItem(KEY, fresh);
	} catch {
		// A private window with storage blocked: the id lives for this page only,
		// which costs a redundant refresh and nothing else.
	}

	cached = fresh;
	return fresh;
}
