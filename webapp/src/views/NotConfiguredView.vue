<script setup lang="ts">
// Rendered instead of the app when the Auth0 environment variables are absent.
// Without them there is no sign-in to attempt, so this says what is missing
// rather than failing somewhere less obvious.
</script>

<template>
	<div class="flex min-h-dvh items-center justify-center px-4 py-12">
		<div class="w-full max-w-md">
			<div class="card p-6">
				<h1 class="text-base font-semibold text-slate-900 dark:text-white">yuuka is not configured</h1>
				<p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
					Sign-in needs the Auth0 settings. Copy <code class="font-mono text-xs">.env.example</code> to
					<code class="font-mono text-xs">.env</code> and fill in:
				</p>
				<ul class="mt-3 space-y-1 font-mono text-xs text-slate-700 dark:text-slate-300">
					<li>VITE_AUTH0_DOMAIN</li>
					<li>VITE_AUTH0_CLIENT_ID</li>
					<li>VITE_AUTH0_AUDIENCE</li>
				</ul>
				<p class="mt-3 text-sm text-slate-600 dark:text-slate-400">
					<code class="font-mono text-xs">VITE_AUTH0_AUDIENCE</code> must match <code class="font-mono text-xs">AUTH0_AUDIENCE</code> in
					<code class="font-mono text-xs">wrangler.jsonc</code>, then restart the dev server.
				</p>
			</div>
		</div>
	</div>
</template>
