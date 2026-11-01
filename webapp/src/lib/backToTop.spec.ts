import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, type App, type Ref } from 'vue';
import { useBackToTop } from './backToTop';

function scrollTo(y: number): void {
	Object.defineProperty(window, 'scrollY', { value: y, configurable: true });
	window.dispatchEvent(new Event('scroll'));
}

describe('useBackToTop', () => {
	let active: Ref<boolean>;
	let scroll: () => void;
	let app: App;

	beforeEach(() => {
		Object.defineProperty(window, 'innerHeight', { value: 800, configurable: true });
		scrollTo(0);
		app = createApp(
			defineComponent({
				setup() {
					({ active, scroll } = useBackToTop());
					return () => h('div');
				},
			}),
		);
		app.mount(document.createElement('div'));
	});

	afterEach(() => {
		app.unmount();
		vi.restoreAllMocks();
	});

	it('stays off while scrolling down, however deep', () => {
		scrollTo(5000);
		expect(active.value).toBe(false);
	});

	it('comes on scrolling up from more than two screens down', () => {
		scrollTo(5000);
		scrollTo(4900);
		expect(active.value).toBe(true);
	});

	it('stays off scrolling up in the shallows', () => {
		scrollTo(1500);
		scrollTo(1400);
		expect(active.value).toBe(false);
	});

	it('goes off again once scrolling down resumes', () => {
		scrollTo(5000);
		scrollTo(4900);
		scrollTo(5000);
		expect(active.value).toBe(false);
	});

	it('takes the page to the top', () => {
		const spy = vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
		scrollTo(5000);
		scroll();
		expect(spy).toHaveBeenCalledWith(expect.objectContaining({ top: 0 }));
	});

	it('holds all the way up once pressed, then goes off at the top', () => {
		vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
		scrollTo(5000);
		scrollTo(4900);
		scroll();
		for (const y of [3000, 1200, 600, 100]) {
			scrollTo(y);
			expect(active.value).toBe(true);
		}
		scrollTo(0);
		expect(active.value).toBe(false);
	});

	it('lets go of the hold when scrolled down on the way', () => {
		vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
		scrollTo(5000);
		scrollTo(4900);
		scroll();
		scrollTo(3000);
		scrollTo(3100);
		expect(active.value).toBe(false);
	});

	it('stays on between one and two screens down while still scrolling up', () => {
		scrollTo(5000);
		scrollTo(4900);
		scrollTo(1200);
		expect(active.value).toBe(true);
		scrollTo(700);
		expect(active.value).toBe(false);
	});
});
