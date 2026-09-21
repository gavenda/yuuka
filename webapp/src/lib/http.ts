/**
 * The transport under `api.ts`: the error type, where the access token comes
 * from, and the one `fetch` every call goes through.
 *
 * It is a module of its own so that the offline queue can use it without
 * importing `api.ts`, which imports the queue back. Nothing here knows what a
 * transaction is.
 */

import { deviceId } from './device';

/** An error carrying the API's status code and any per-field validation detail. */
export class ApiError extends Error {
	readonly status: number;
	readonly details?: Record<string, string[]>;

	constructor(status: number, message: string, details?: Record<string, string[]>) {
		super(message);
		this.name = 'ApiError';
		this.status = status;
		this.details = details;
	}

	/** True when the session is missing or expired and the user must sign in again. */
	get isUnauthorized(): boolean {
		return this.status === 401;
	}

	/** True when the request never got an answer — offline, or the server could not be reached. */
	get isNetworkError(): boolean {
		return this.status === 0;
	}
}

/**
 * Whether a failure is only the network being out of reach. A store already showing its local copy
 * keeps it on that, but not on anything the server actually answered.
 */
export function isNetworkError(error: unknown): boolean {
	return error instanceof ApiError && error.isNetworkError;
}

/**
 * The API asks for a token per request rather than holding one, so Auth0's SDK
 * can refresh it when it is close to expiring. The provider is installed by the
 * auth store, which keeps `api` free of any import back into Pinia.
 */
type TokenProvider = () => Promise<string | null>;

let tokenProvider: TokenProvider | null = null;

export function setTokenProvider(provider: TokenProvider): void {
	tokenProvider = provider;
}

/** Called when any request is rejected as unauthorised, so the app can sign out. */
let onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(handler: () => void): void {
	onUnauthorized = handler;
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
	const token = tokenProvider ? await tokenProvider() : null;

	let response: Response;

	try {
		response = await fetch(`/api${path}`, {
			...init,
			headers: {
				'content-type': 'application/json',
				// Names this install so a write of ours does not come back as a push
				// telling us to refetch what we already have.
				'x-yuuka-device': deviceId(),
				...(token ? { authorization: `Bearer ${token}` } : {}),
				...(init.headers as Record<string, string> | undefined),
			},
		});
	} catch {
		throw new ApiError(0, 'Network error. Check your connection.');
	}

	if (response.status === 204) return undefined as T;

	const body = (await response.json().catch(() => null)) as (T & { error?: string; details?: Record<string, string[]> }) | null;

	if (!response.ok) {
		if (response.status === 401) onUnauthorized?.();
		throw new ApiError(response.status, body?.error ?? `Request failed (${response.status}).`, body?.details);
	}

	return body as T;
}
