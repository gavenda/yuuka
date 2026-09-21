/**
 * Firebase Cloud Messaging, HTTP v1.
 *
 * The Worker sends data-only messages: a change on one device is a hint to the
 * others that a named slice of the ledger moved, never a notification to show.
 * A visible notification would be wrong — nothing happened that the user did
 * not just do themselves on another screen — and a data-only message is also
 * what lets the web app's service worker stay silent.
 *
 * The legacy server key is gone, so authenticating means minting a Google OAuth
 * access token from the service account: a short RS256 JWT, exchanged at the
 * token endpoint. That exchange is a network round trip on every send unless it
 * is cached, so the token lives in KV for slightly less than its hour.
 *
 * Everything here is best-effort. Push is an optimisation over the sync that
 * the clients already do on their own; a failed send must never fail the write
 * that triggered it.
 */

const TOKEN_KEY = 'fcm:access-token:v1';
const TOKEN_TTL_SECONDS = 3300; // Google issues an hour; renew with five minutes to spare.
const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';

/** The credentials the sender needs. Absent in a deployment that has not configured FCM. */
export interface FcmCredentials {
	projectId: string;
	clientEmail: string;
	privateKey: string;
}

/**
 * Reads the service account out of the environment, or null when this
 * deployment has no FCM configured — in which case every send is a no-op and
 * the clients fall back to syncing when they next open.
 *
 * `FCM_PRIVATE_KEY` is a PEM blob, and the newlines in it do not survive every
 * way of setting a secret, so an escaped `\n` is accepted as the real thing.
 */
export function fcmCredentials(env: Env): FcmCredentials | null {
	const projectId = env.FCM_PROJECT_ID;
	const clientEmail = env.FCM_CLIENT_EMAIL;
	const privateKey = env.FCM_PRIVATE_KEY;
	if (!projectId || !clientEmail || !privateKey) return null;

	return { projectId, clientEmail, privateKey: privateKey.replace(/\\n/g, '\n') };
}

function base64Url(bytes: ArrayBuffer | Uint8Array): string {
	const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
	let binary = '';
	for (const byte of view) binary += String.fromCharCode(byte);
	return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
}

function base64UrlJson(value: unknown): string {
	return base64Url(new TextEncoder().encode(JSON.stringify(value)));
}

/** Imports the service account's PEM private key for RS256 signing. */
async function importPrivateKey(pem: string): Promise<CryptoKey> {
	const body = pem
		.replace(/-----BEGIN [^-]+-----/, '')
		.replace(/-----END [^-]+-----/, '')
		.replace(/\s+/g, '');
	const der = Uint8Array.from(atob(body), (character) => character.charCodeAt(0));

	return await crypto.subtle.importKey('pkcs8', der.buffer as ArrayBuffer, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
}

/**
 * Exchanges a self-signed JWT for a Google access token.
 *
 * The assertion is signed by the service account itself, so this is the only
 * place the private key is used and the token that comes back is what every
 * send carries.
 */
async function mintAccessToken(credentials: FcmCredentials): Promise<string> {
	const issuedAt = Math.floor(Date.now() / 1000);
	const claims = {
		iss: credentials.clientEmail,
		scope: SCOPE,
		aud: 'https://oauth2.googleapis.com/token',
		iat: issuedAt,
		exp: issuedAt + 3600,
	};

	const unsigned = `${base64UrlJson({ alg: 'RS256', typ: 'JWT' })}.${base64UrlJson(claims)}`;
	const key = await importPrivateKey(credentials.privateKey);
	const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned));

	const response = await fetch('https://oauth2.googleapis.com/token', {
		method: 'POST',
		headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
		body: new URLSearchParams({
			grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
			assertion: `${unsigned}.${base64Url(signature)}`,
		}),
	});

	if (!response.ok) throw new Error(`FCM token exchange failed: ${response.status} ${await response.text()}`);

	const { access_token: accessToken } = await response.json<{ access_token?: string }>();
	if (!accessToken) throw new Error('FCM token exchange returned no access_token.');

	return accessToken;
}

/** The cached access token, minted on a miss. Shared by every user — it authenticates the project, not a person. */
async function accessToken(env: Env, credentials: FcmCredentials): Promise<string> {
	const cached = await env.CACHE.get(TOKEN_KEY, 'text');
	if (cached) return cached;

	const token = await mintAccessToken(credentials);
	await env.CACHE.put(TOKEN_KEY, token, { expirationTtl: TOKEN_TTL_SECONDS });
	return token;
}

/** What FCM said about one token: whether the send worked, and whether the token is worth keeping. */
export interface SendOutcome {
	token: string;
	ok: boolean;
	/** FCM says this token no longer addresses an install, so the caller should forget it. */
	stale: boolean;
}

/**
 * Sends one data-only message to one token.
 *
 * Every value in `data` must be a string — that is FCM's wire format, not a
 * choice — so the caller passes an already-stringified payload.
 */
async function sendOne(
	env: Env,
	credentials: FcmCredentials,
	bearer: string,
	token: string,
	data: Record<string, string>,
): Promise<SendOutcome> {
	const response = await fetch(`https://fcm.googleapis.com/v1/projects/${credentials.projectId}/messages:send`, {
		method: 'POST',
		headers: { Authorization: `Bearer ${bearer}`, 'Content-Type': 'application/json' },
		body: JSON.stringify({
			message: {
				token,
				data,
				// Data-only on both platforms. Android needs the priority to wake a
				// dozing app; the web half needs `urgency` for the same reason.
				android: { priority: 'high' },
				webpush: { headers: { Urgency: 'high', TTL: '600' } },
				apns: { headers: { 'apns-priority': '5' }, payload: { aps: { 'content-available': 1 } } },
			},
		}),
	});

	if (response.ok) return { token, ok: true, stale: false };

	const body = await response.text();
	// 404 is UNREGISTERED and 403 SENDER_ID_MISMATCH; both mean this token will
	// never work again for us. A 400 usually means a malformed token.
	const stale = response.status === 404 || response.status === 403 || (response.status === 400 && body.includes('INVALID_ARGUMENT'));
	console.warn('FCM send failed', response.status, body);

	return { token, ok: false, stale };
}

/**
 * Sends the same data-only message to every token, and reports which ones FCM
 * rejected as dead. Failures are swallowed per token: one bad registration
 * must not stop the rest being told.
 */
export async function sendToTokens(env: Env, tokens: string[], data: Record<string, string>): Promise<SendOutcome[]> {
	const credentials = fcmCredentials(env);
	if (!credentials || tokens.length === 0) return [];

	let bearer: string;
	try {
		bearer = await accessToken(env, credentials);
	} catch (error) {
		console.error('FCM authentication failed', error);
		return [];
	}

	return await Promise.all(
		tokens.map(async (token) => {
			try {
				return await sendOne(env, credentials, bearer, token, data);
			} catch (error) {
				console.error('FCM send threw', error);
				return { token, ok: false, stale: false };
			}
		}),
	);
}
