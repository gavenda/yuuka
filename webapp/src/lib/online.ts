import { readonly, ref } from 'vue';

const online = ref(navigator.onLine);

window.addEventListener('online', () => (online.value = true));
window.addEventListener('offline', () => (online.value = false));

/**
 * Whether the browser believes it has a network. Only a hint for the interface — a browser can
 * report a connection that leads nowhere — so nothing decides on it: requests are still tried, and
 * an unreachable API is an `ApiError` like any other.
 */
export const isOnline = readonly(online);
