import { computed, reactive } from 'vue';

/** One field's check: the message to show when its value is not acceptable, or null when it is. */
export type Rule = () => string | null;

/**
 * Field-level validation for a form, reactive as Material 3 describes it: a field says what is wrong beside
 * the field itself, as it goes, and the form cannot be saved while anything is wrong.
 *
 * - The rules are re-run on every change, so `isValid` is always current: bind it to the save button's
 *   `disabled` and the button is off from the first moment a field is unacceptable and on the moment the
 *   last one is fixed.
 * - A field shows its error once the person has touched it — typed in it, chosen from it or left it — so a
 *   blank form is not covered in red before anyone has done anything, yet its save button is already off.
 *   The error follows the value and clears the moment it is acceptable.
 *
 * A saved change never waits for the network, so everything the server would refuse for the form's own
 * values has to be answered here. Rules are keyed by the `id` of the control they belong to, which is how
 * `onInput`, listening on the whole form, knows which field an edit was in.
 */
export function useFormValidation(rules: Record<string, Rule>) {
	const touched = reactive<Record<string, boolean>>({});

	/** Every field's current problem, whether or not it is being shown yet. */
	const problems = computed(() => Object.fromEntries(Object.entries(rules).map(([key, rule]) => [key, rule()])));
	const isValid = computed(() => Object.values(problems.value).every((message) => message === null));

	/** The message to draw under a field: its problem, once the field has been touched. */
	function error(key: string): string | null {
		return touched[key] ? (problems.value[key] ?? null) : null;
	}

	/** Marks a field as visited. Call it when a control that raises no `input` event changes or loses focus. */
	function touch(key: string): void {
		touched[key] = true;
	}

	/** Put on the `<form>` as `@input`: an edit in any field counts as touching it, so no control has to say so itself. */
	function onInput(event: Event): void {
		const id = (event.target as HTMLElement | null)?.id;
		if (id && id in rules) touch(id);
	}

	/** Back to a pristine form, for when it is reopened. */
	function reset(): void {
		for (const key of Object.keys(touched)) delete touched[key];
	}

	return { error, touch, onInput, reset, isValid };
}

/** The id a field's supporting text carries, for `aria-describedby`. */
export const supportId = (id: string): string => `${id}-support`;

/*
 * Rules the API applies to a value, answered here as well because a save is never held up for the
 * server to say no. Each returns the message for a value that would be refused, or null.
 */

/** The longest a name may be; the API's own limit for an account, category, tag or account type. */
export const NAME_MAX = 80;

/**
 * A required name, at most `NAME_MAX` long. `isTaken` reports a name already in use, for the places that
 * keep names unique, and `takenMessage` says so in that place's own terms.
 */
export function nameProblem(
	value: string,
	isTaken?: (name: string) => boolean,
	takenMessage = 'That name is already in use.',
): string | null {
	const name = value.trim();
	if (!name) return 'Enter a name.';
	if (name.length > NAME_MAX) return `Use ${NAME_MAX} characters or fewer.`;
	return isTaken?.(name) ? takenMessage : null;
}

/** A colour is a full `#rrggbb`; the API accepts nothing shorter. */
export function colorProblem(value: string): string | null {
	return /^#[0-9a-fA-F]{6}$/.test(value.trim()) ? null : 'Use a hex colour such as #64748b.';
}

/** Exactly three letters, the shape of an ISO 4217 code; the API refuses anything else. */
export function currencyProblem(value: string): string | null {
	if (!value.trim()) return 'Enter a currency code.';
	return /^[A-Za-z]{3}$/.test(value.trim()) ? null : 'Use a 3-letter currency code, such as PHP.';
}

/** An optional logo link. It ends up in an image load, so only http(s) is accepted. */
export function logoUrlProblem(value: string): string | null {
	const url = value.trim();
	if (!url) return null;
	if (url.length > 2048) return 'That URL is too long.';

	try {
		return ['http:', 'https:'].includes(new URL(url).protocol) ? null : 'Use an http or https link.';
	} catch {
		return 'Use a full link, such as https://example.com/logo.png.';
	}
}

/** Same name, whatever the case — what tags, and the payee history, are unique by. */
export function sameName(a: string, b: string): boolean {
	return a.trim().toLowerCase() === b.trim().toLowerCase();
}
