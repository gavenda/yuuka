import { cloudflareTest, readD1Migrations } from '@cloudflare/vitest-plugin';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

const migrations = await readD1Migrations(fileURLToPath(new URL('./migrations', import.meta.url)));

/**
 * The API runs inside workerd for real, against a migrated D1 database.
 *
 * Pages Functions have no Worker config for the pool to read, so the bindings
 * are declared here instead and `main` points at a thin entry that exposes the
 * same Hono app the Pages Function mounts.
 */
export default defineConfig({
	plugins: [
		cloudflareTest({
			main: './test/worker-entry.ts',
			miniflare: {
				compatibilityDate: '2026-09-11',
				compatibilityFlags: ['nodejs_compat'],
				d1Databases: { DB: 'yuuka-test' },
				kvNamespaces: ['CACHE'],
				bindings: {
					// Handed to `applyD1Migrations` in the setup file.
					TEST_MIGRATIONS: migrations,
					// A stand-in Auth0 tenant. Tests publish a JWKS into KV for this
					// issuer and sign their own tokens, so nothing reaches the network.
					AUTH0_DOMAIN: 'auth.test.example',
					AUTH0_AUDIENCE: 'https://api.yuuka.test',
					ALLOWED_SUBJECTS: '',
					// Most specs assert on rows they created themselves, so they start
					// from categories only. `provisioning.spec.ts` turns this back on.
					SEED_DEMO_DATA: 'false',
				},
			},
		}),
	],
	test: {
		include: ['test/**/*.spec.ts'],
		setupFiles: ['./test/setup.ts'],
	},
});
