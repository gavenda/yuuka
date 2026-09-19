import { api, isNetworkError } from '@/lib/api';
import { readCache, writeCache } from '@/lib/cache';
import type { Subscription } from '@/types';
import { defineStore } from 'pinia';
import { ref } from 'vue';

const CACHE_KEY = 'subscriptions';

const isSubscriptionList = (data: unknown): data is Subscription[] => Array.isArray(data);

/**
 * Subscriptions are only read on their own page, so this is a plain list with a
 * loading flag rather than anything shared. The transactions they post are the
 * transaction store's business — the server posts those, not this store. The list is shown from
 * the browser's local copy while the API is asked for the current one.
 */
export const useSubscriptionStore = defineStore('subscriptions', () => {
	const subscriptions = ref<Subscription[]>([]);
	const loaded = ref(false);
	const loading = ref(false);
	const error = ref<string | null>(null);
	/** Whether the API has answered since this page opened; a copy shown from the browser does not count. */
	let synced = false;

	async function load(force = false): Promise<void> {
		if (synced && !force) return;
		loading.value = true;
		error.value = null;

		if (!loaded.value) {
			const cached = readCache(CACHE_KEY, isSubscriptionList);
			if (cached) {
				subscriptions.value = cached;
				loaded.value = true;
			}
		}

		try {
			subscriptions.value = (await api.listSubscriptions()).subscriptions;
			loaded.value = true;
			synced = true;
			writeCache(CACHE_KEY, subscriptions.value);
		} catch (caught) {
			// Out of reach with a copy on screen: keep showing it.
			if (!(loaded.value && isNetworkError(caught))) {
				error.value = caught instanceof Error ? caught.message : 'Could not load subscriptions.';
			}
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
		synced = false;
		error.value = null;
	}

	return { subscriptions, loaded, loading, error, load, refresh, reset };
});
