import tailwindcss from '@tailwindcss/vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [vue(), tailwindcss()],
	resolve: {
		alias: {
			'@': fileURLToPath(new URL('./src', import.meta.url)),
		},
	},
	server: {
		port: 5173,
		proxy: {
			// In production the API is served from the same origin by the Pages
			// Function. This proxy reproduces that during development, so the
			// frontend always calls a relative `/api` path and never needs to know
			// where the API lives.
			'/api': {
				target: 'http://localhost:8788',
				changeOrigin: true,
			},
		},
	},
});
