import { onBeforeUnmount, ref, watch, type Ref } from 'vue';

/**
 * How wide an element is, in pixels, kept current as it resizes: what a chart that fills its card lays its
 * marks out against. Zero until the element is in the page, and where `ResizeObserver` is missing it is read once.
 */
export function useElementWidth(element: Ref<HTMLElement | null>): Ref<number> {
	const width = ref(0);
	let observer: ResizeObserver | undefined;

	watch(
		element,
		(target) => {
			observer?.disconnect();
			observer = undefined;
			if (!target) return;

			width.value = target.clientWidth;
			if (typeof ResizeObserver !== 'undefined') {
				observer = new ResizeObserver(() => (width.value = target.clientWidth));
				observer.observe(target);
			}
		},
		{ flush: 'post', immediate: true },
	);

	onBeforeUnmount(() => observer?.disconnect());
	return width;
}
