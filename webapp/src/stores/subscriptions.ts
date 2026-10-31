import { api, isNetworkError } from '@/lib/api';
import { readSubscriptions, replaceSubscriptions } from '@/lib/cache';
import type { Subscription } from '@/types';
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { t } from '@/i18n';

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

	/** Bumped whenever the list on screen is replaced, so a copy still being read cannot land on top of it. */
	let revision = 0;

	/** Shows the local copy, if there is one and nothing has replaced the list since it was asked for. */
	async function showCopy(): Promise<void> {
		const asked = revision;
		const cached = await readSubscriptions();
		if (!cached || revision !== asked) return;

		revision++;
		subscriptions.value = cached;
		loaded.value = true;
	}

	/** Resolves whether the API answered; a failure is said in `error` rather than thrown. */
	async function load(force = false): Promise<boolean> {
		if (synced && !force) return true;
		loading.value = true;
		error.value = null;
		// Read beside the request rather than before it: the copy only fills the wait for the answer.
		const copy = loaded.value ? null : showCopy();

		try {
			const fresh = (await api.listSubscriptions()).subscriptions;
			revision++;
			subscriptions.value = fresh;
			loaded.value = true;
			synced = true;
			void replaceSubscriptions(fresh);
			return true;
		} catch (caught) {
			// Out of reach with a copy on screen: keep showing it. A copy still on its way counts —
			// offline, the request can fail before the copy is read.
			await copy;
			if (!(loaded.value && isNetworkError(caught))) {
				error.value = caught instanceof Error ? caught.message : t('common.errors.loadSubscriptions');
			}
			return false;
		} finally {
			loading.value = false;
		}
	}

	function refresh(): Promise<boolean> {
		return load(true);
	}

	function reset(): void {
		revision++;
		subscriptions.value = [];
		loaded.value = false;
		synced = false;
		error.value = null;
	}

	/** Shows the list as the local copy now holds it — what a save has just written there — without asking the API. */
	async function showSaved(): Promise<void> {
		if (loaded.value) await showCopy();
	}

	return { subscriptions, loaded, loading, error, load, refresh, showSaved, reset };
});
