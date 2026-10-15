import { ref, watch, type Ref } from 'vue';

/**
 * Whether [target] is on show, or within [margin] of it — an `IntersectionObserver` root margin, so
 * `'0px 0px 100% 0px'` reads "no more than a screen below the fold". It follows the element as it comes
 * and goes, and is `false` while there is none. Call it from a component's setup: it stops watching when
 * the component goes.
 */
export function useInView(target: Ref<Element | null>, margin = '0px'): Ref<boolean> {
	const inView = ref(false);

	watch(
		target,
		(element, _previous, onCleanup) => {
			if (!element) return;

			// Entries arrive oldest first, and only where the element stands now matters.
			const observer = new IntersectionObserver((entries) => (inView.value = entries[entries.length - 1].isIntersecting), {
				rootMargin: margin,
			});
			observer.observe(element);

			onCleanup(() => {
				observer.disconnect();
				inView.value = false;
			});
		},
		{ immediate: true },
	);

	return inView;
}
