/**
 * Bindings this app adds on top of the generated `worker-configuration.d.ts`.
 *
 * The generated file only knows what `wrangler.jsonc` declares, and these three
 * are secrets (`wrangler secret put`), so they never appear there. Every one is
 * optional: a deployment without FCM configured still works, it just cannot
 * tell a user's other device that something changed — see `server/fcm.ts`.
 */
interface Env {
	/** The Firebase project the service account belongs to, e.g. `yuuka-1a2b3`. */
	FCM_PROJECT_ID?: string;
	/** The service account's address, from its JSON key file. */
	FCM_CLIENT_EMAIL?: string;
	/** The service account's PEM private key. Escaped `\n` is accepted as a real newline. */
	FCM_PRIVATE_KEY?: string;
}
