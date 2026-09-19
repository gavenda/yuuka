import { describe, expect, it } from 'vitest';
import { categoryOptions, namedOptions } from './selectOptions';
import type { Category } from '@/types';

const category = (id: string, name: string) => ({ id, name }) as Category;

describe('namedOptions', () => {
	it('puts the leading choices first, then one option per item', () => {
		expect(namedOptions([{ id: 'a', name: 'Checking' }], { value: '', label: 'Select an account', disabled: true })).toEqual([
			{ value: '', label: 'Select an account', disabled: true },
			{ value: 'a', label: 'Checking' },
		]);
	});
});

describe('categoryOptions', () => {
	it('lists each parent followed by its children, which are set in a step', () => {
		const options = categoryOptions([{ parent: category('g', 'Groceries'), children: [category('s', 'Supermarket')] }], {
			value: '',
			label: 'Uncategorized',
		});

		expect(options).toEqual([
			{ value: '', label: 'Uncategorized' },
			{ value: 'g', label: 'Groceries' },
			{ value: 's', label: 'Supermarket', indent: true },
		]);
	});
});
