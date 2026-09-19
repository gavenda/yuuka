/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />

/** Public Auth0 configuration, baked in at build time. None of it is secret. */
interface ImportMetaEnv {
	readonly VITE_AUTH0_DOMAIN: string;
	readonly VITE_AUTH0_CLIENT_ID: string;
	readonly VITE_AUTH0_AUDIENCE: string;
}

interface ImportMeta {
	readonly env: ImportMetaEnv;
}

/** The package.json version, baked in at build time — see vite.config.ts. */
declare const __APP_VERSION__: string;
