import type { Category } from '@/types';

/** One choice in a `SelectField`. */
export interface SelectOption {
	value: string;
	label: string;
	/** Shown but not choosable — a placeholder such as "Select an account". */
	disabled?: boolean;
	/** Set in a step, for a subcategory beneath its parent. */
	indent?: boolean;
}

/** Anything with an id and a name — an account, an account type, a category — as choices, after any leading ones. */
export function namedOptions(items: { id: string; name: string }[], ...lead: SelectOption[]): SelectOption[] {
	return [...lead, ...items.map((item) => ({ value: item.id, label: item.name }))];
}

/** Categories grouped for a picker: each parent, then its children set in a step beneath it. Any leading choices come first. */
export function categoryOptions(groups: { parent: Category; children: Category[] }[], ...lead: SelectOption[]): SelectOption[] {
	return [
		...lead,
		...groups.flatMap((group) => [
			{ value: group.parent.id, label: group.parent.name },
			...group.children.map((child) => ({ value: child.id, label: child.name, indent: true })),
		]),
	];
}
