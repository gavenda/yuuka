/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />

/** Public Auth0 configuration, baked in at build time. None of it is secret. */
interface ImportMetaEnv {
	/**
	 * Firebase Cloud Messaging, for the push that tells this browser which
	 * slices of the ledger the other device moved. All optional: a build
	 * without them simply never registers, and the app syncs the slower way.
	 * Every value is a public identifier, not a secret.
	 */
	readonly VITE_FIREBASE_API_KEY?: string;
	readonly VITE_FIREBASE_AUTH_DOMAIN?: string;
	readonly VITE_FIREBASE_PROJECT_ID?: string;
	readonly VITE_FIREBASE_MESSAGING_SENDER_ID?: string;
	readonly VITE_FIREBASE_APP_ID?: string;
	readonly VITE_FIREBASE_VAPID_KEY?: string;
	readonly VITE_AUTH0_DOMAIN: string;
	readonly VITE_AUTH0_CLIENT_ID: string;
	readonly VITE_AUTH0_AUDIENCE: string;
}

interface ImportMeta {
	readonly env: ImportMetaEnv;
}

/** The package.json version, baked in at build time — see vite.config.ts. */
declare const __APP_VERSION__: string;
