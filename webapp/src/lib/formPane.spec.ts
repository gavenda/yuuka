import { afterEach, describe, expect, it, vi } from 'vitest';
import { enterPane, leavePane, paneOpen, pendingSwitch, whenDiscardable } from './formPane';

const standing: symbol[] = [];

function stand(dirty: boolean): symbol {
	const id = Symbol('form');
	enterPane(id, () => dirty);
	standing.push(id);
	return id;
}

afterEach(() => {
	standing.splice(0).forEach(leavePane);
	pendingSwitch.value = null;
});

describe('the form pane', () => {
	it('is open only while a form stands in it', () => {
		expect(paneOpen.value).toBe(false);
		const id = stand(false);
		expect(paneOpen.value).toBe(true);
		leavePane(id);
		expect(paneOpen.value).toBe(false);
	});

	it('counts a form once however often it enters', () => {
		const id = stand(false);
		enterPane(id, () => false);
		leavePane(id);
		expect(paneOpen.value).toBe(false);
	});

	it('opens another form at once when nothing stands in the pane', () => {
		const open = vi.fn();
		whenDiscardable(open);
		expect(open).toHaveBeenCalledOnce();
		expect(pendingSwitch.value).toBeNull();
	});

	it('opens another form at once over one that holds no changes', () => {
		stand(false);
		const open = vi.fn();
		whenDiscardable(open);
		expect(open).toHaveBeenCalledOnce();
	});

	it('asks first over a form that holds changes, and opens nothing until answered', () => {
		stand(true);
		const open = vi.fn();
		whenDiscardable(open);
		expect(open).not.toHaveBeenCalled();
		expect(pendingSwitch.value).toBe(open);
	});

	it('judges by the newest form in the pane', () => {
		stand(true);
		stand(false);
		const open = vi.fn();
		whenDiscardable(open);
		expect(open).toHaveBeenCalledOnce();
	});
});
