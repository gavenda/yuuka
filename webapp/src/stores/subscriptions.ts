import { api } from '@/lib/api';
import type { Subscription } from '@/types';
import { defineStore } from 'pinia';
import { ref } from 'vue';

/**
 * Subscriptions are only read on their own page, so this is a plain list with a
 * loading flag rather than anything shared. The transactions they post are the
 * transaction store's business — the server posts those, not this store.
 */
export const useSubscriptionStore = defineStore('subscriptions', () => {
	const subscriptions = ref<Subscription[]>([]);
	const loaded = ref(false);
	const loading = ref(false);
	const error = ref<string | null>(null);

	async function load(force = false): Promise<void> {
		if (loaded.value && !force) return;
		loading.value = true;
		error.value = null;

		try {
			subscriptions.value = (await api.listSubscriptions()).subscriptions;
			loaded.value = true;
		} catch (caught) {
			error.value = caught instanceof Error ? caught.message : 'Could not load subscriptions.';
		} finally {
			loading.value = false;
		}
	}

	function refresh(): Promise<void> {
		return load(true);
	}

	function reset(): void {
		subscriptions.value = [];
		loaded.value = false;
		error.value = null;
	}

	return { subscriptions, loaded, loading, error, load, refresh, reset };
});
