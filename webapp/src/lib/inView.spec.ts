import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick, ref, type App, type Ref } from 'vue';
import { useInView } from './inView';

/** Stands in for the browser's observer, which happy-dom never calls back: a spec says what it sees. */
class FakeObserver {
	static all: FakeObserver[] = [];
	observed: Element[] = [];
	disconnected = false;
	private readonly callback: IntersectionObserverCallback;
	readonly options: IntersectionObserverInit;

	constructor(callback: IntersectionObserverCallback, options: IntersectionObserverInit = {}) {
		this.callback = callback;
		this.options = options;
		FakeObserver.all.push(this);
	}

	observe(element: Element): void {
		this.observed.push(element);
	}

	disconnect(): void {
		this.disconnected = true;
	}

	/** Reports the element as on show, or not. */
	see(...intersecting: boolean[]): void {
		const entries = intersecting.map((isIntersecting) => ({ isIntersecting }) as IntersectionObserverEntry);
		this.callback(entries, this as unknown as IntersectionObserver);
	}
}

let app: App | undefined;

/** Mounts a component that draws the watched element only while [shown], and hands back what it reports. */
function mount(margin?: string) {
	const shown = ref(true);
	let inView!: Ref<boolean>;

	app = createApp(
		defineComponent({
			setup() {
				const target = ref<HTMLElement | null>(null);
				inView = useInView(target, margin);
				return () => (shown.value ? h('div', { ref: target }) : null);
			},
		}),
	);
	app.mount(document.createElement('div'));

	return { shown, inView };
}

beforeEach(() => {
	FakeObserver.all = [];
	vi.stubGlobal('IntersectionObserver', FakeObserver);
});

afterEach(() => {
	app?.unmount();
	app = undefined;
	vi.unstubAllGlobals();
});

describe('useInView', () => {
	it('is false until the element is seen, and follows it from there', async () => {
		const { inView } = mount('0px 0px 100% 0px');
		await nextTick();

		const [observer] = FakeObserver.all;
		expect(observer.observed).toHaveLength(1);
		expect(observer.options.rootMargin).toBe('0px 0px 100% 0px');
		expect(inView.value).toBe(false);

		observer.see(true);
		expect(inView.value).toBe(true);

		observer.see(false);
		expect(inView.value).toBe(false);
	});

	it('goes by where the element stands now when several reports arrive at once', async () => {
		const { inView } = mount();
		await nextTick();

		FakeObserver.all[0].see(false, true);

		expect(inView.value).toBe(true);
	});

	it('stops watching an element that has gone, and watches the one that takes its place', async () => {
		const { shown, inView } = mount();
		await nextTick();
		FakeObserver.all[0].see(true);

		shown.value = false;
		await nextTick();

		expect(FakeObserver.all[0].disconnected).toBe(true);
		expect(inView.value).toBe(false);

		shown.value = true;
		await nextTick();

		expect(FakeObserver.all).toHaveLength(2);
		expect(FakeObserver.all[1].observed).toHaveLength(1);
	});

	it('stops watching when the component goes', async () => {
		mount();
		await nextTick();

		app!.unmount();
		app = undefined;

		expect(FakeObserver.all[0].disconnected).toBe(true);
	});
});
