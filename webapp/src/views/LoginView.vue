<script setup lang="ts">
import { useAuth0 } from '@auth0/auth0-vue';
import { ref } from 'vue';
import { useRoute } from 'vue-router';

const { loginWithRedirect, error } = useAuth0();
const route = useRoute();
const redirecting = ref(false);

async function signIn(): Promise<void> {
	redirecting.value = true;
	const target = typeof route.query.redirect === 'string' ? route.query.redirect : undefined;

	try {
		// `appState` survives the round trip to Auth0; the plugin reads `target`
		// back on return and navigates there instead of the dashboard.
		await loginWithRedirect({ appState: target ? { target } : undefined });
	} catch {
		redirecting.value = false;
	}
}
</script>

<template>
	<div class="flex min-h-dvh items-center justify-center px-4 py-12">
		<div class="w-full max-w-sm text-center">
			<span class="mx-auto mb-4 grid h-12 w-12 place-items-center rounded-md bg-primary text-xl font-medium text-on-primary">¥</span>
			<h1 class="text-2xl font-medium text-on-surface">yuuka</h1>
			<p class="mt-1 text-sm text-on-surface-variant">Budgeting and financial tracking.</p>

			<div class="card mt-8 p-6">
				<p class="mb-4 text-sm text-on-surface-variant">Sign in to continue.</p>

				<button type="button" class="btn-primary w-full" :disabled="redirecting" @click="signIn">
					{{ redirecting ? 'Redirecting…' : 'Sign in' }}
				</button>

				<p v-if="error" class="mt-4 banner-error" role="alert">
					{{ error.message }}
				</p>
			</div>
		</div>
	</div>
</template>
