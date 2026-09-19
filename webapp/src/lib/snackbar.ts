import { reactive, readonly } from 'vue';

export interface SnackbarAction {
	label: string;
	run: () => void;
}

export interface SnackbarMessage {
	id: number;
	message: string;
	action?: SnackbarAction;
	/** Milliseconds on screen; `null` stays until dismissed or acted on. */
	duration: number | null;
}

/** Long enough to read a short sentence; longer when there is something to tap. */
const DEFAULT_MS = 4000;
const WITH_ACTION_MS = 10000;

const queue = reactive<SnackbarMessage[]>([]);
let nextId = 1;

/** Messages waiting to be shown, first one on screen. One at a time, as Material specifies. */
export const snackbarQueue = readonly(queue);

/** Queues a transient message. Returns its id, so a caller can take it back with `dismissSnackbar`. */
export function showSnackbar(message: string, options: { action?: SnackbarAction; duration?: number | null } = {}): number {
	const id = nextId++;
	queue.push({
		id,
		message,
		action: options.action,
		duration: options.duration === undefined ? (options.action ? WITH_ACTION_MS : DEFAULT_MS) : options.duration,
	});
	return id;
}

export function dismissSnackbar(id: number): void {
	const index = queue.findIndex((entry) => entry.id === id);
	if (index !== -1) queue.splice(index, 1);
}

/** Empties the queue, for sign-out: one person's messages must not outlast their session. */
export function clearSnackbars(): void {
	queue.splice(0);
}
