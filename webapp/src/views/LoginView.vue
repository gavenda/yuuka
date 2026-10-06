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
	<!-- The Android sign-in screen: the name, what it is, and one button, in the middle of the window. -->
	<div class="flex min-h-dvh flex-col items-center justify-center p-6 text-center text-on-surface">
		<h1 class="type-headline-large">yuuka</h1>
		<p class="type-body-medium pt-1 pb-8">Personal budgeting and financial tracking.</p>

		<button type="button" class="btn-primary w-full" :disabled="redirecting" @click="signIn">
			{{ redirecting ? 'Redirecting…' : 'Log in' }}
		</button>

		<p v-if="error" class="type-body-medium pt-4 text-error" role="alert">{{ error.message }}</p>
	</div>
</template>
