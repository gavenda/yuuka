import { beforeEach, describe, expect, it } from 'vitest';
import { clearSnackbars, dismissSnackbar, showSnackbar, snackbarQueue } from './snackbar';

describe('snackbar queue', () => {
	beforeEach(() => clearSnackbars());

	it('shows messages one at a time, in the order they arrived', () => {
		showSnackbar('first');
		showSnackbar('second');

		expect(snackbarQueue.map((entry) => entry.message)).toEqual(['first', 'second']);
	});

	it('gives a message with an action longer on screen than a plain one', () => {
		showSnackbar('plain');
		showSnackbar('with action', { action: { label: 'Undo', run: () => {} } });

		const [plain, withAction] = snackbarQueue;
		expect(withAction.duration).toBeGreaterThan(plain.duration as number);
	});

	it('keeps a message until dismissed when the duration is null', () => {
		showSnackbar('sticky', { duration: null });

		expect(snackbarQueue[0].duration).toBeNull();
	});

	it('dismisses one message by id and leaves the rest', () => {
		const first = showSnackbar('first');
		showSnackbar('second');

		dismissSnackbar(first);

		expect(snackbarQueue.map((entry) => entry.message)).toEqual(['second']);
	});

	it('empties the queue', () => {
		showSnackbar('a');
		showSnackbar('b');

		clearSnackbars();

		expect(snackbarQueue).toHaveLength(0);
	});
});
