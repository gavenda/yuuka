import { afterEach, describe, expect, it } from 'vitest';
import { colorProblem, currencyProblem, logoUrlProblem, nameProblem, sameName, useFormValidation } from './validation';
import { ref } from 'vue';

afterEach(() => {
	document.body.innerHTML = '';
});

describe('useFormValidation', () => {
	function setup() {
		const name = ref('');
		const amount = ref('');
		const validation = useFormValidation({
			name: () => (name.value.trim() ? null : 'Enter a name.'),
			amount: () => (Number(amount.value) > 0 ? null : 'Enter an amount.'),
		});
		return { name, amount, validation };
	}

	it('is invalid from the start, so a save button bound to it is off before anyone types', () => {
		const { validation } = setup();
		expect(validation.isValid.value).toBe(false);
	});

	it('turns valid the moment the last problem is fixed, and invalid again when one returns', () => {
		const { name, amount, validation } = setup();
		name.value = 'Rent';
		expect(validation.isValid.value).toBe(false);
		amount.value = '500';
		expect(validation.isValid.value).toBe(true);
		amount.value = '0';
		expect(validation.isValid.value).toBe(false);
	});

	it('keeps an untouched field quiet, though the form is already invalid', () => {
		const { validation } = setup();

		expect(validation.error('name')).toBeNull();
		validation.touch('name');
		expect(validation.error('name')).toBe('Enter a name.');
		// Only the touched one: the other has not been visited.
		expect(validation.error('amount')).toBeNull();
	});

	it('follows the value: the error clears as soon as it is acceptable and returns if it stops being', () => {
		const { name, validation } = setup();
		validation.touch('name');
		expect(validation.error('name')).toBe('Enter a name.');

		name.value = 'Groceries';
		expect(validation.error('name')).toBeNull();
		name.value = '';
		expect(validation.error('name')).toBe('Enter a name.');
	});

	it('counts an edit in a field, heard on the form, as touching it', () => {
		const { validation } = setup();
		document.body.innerHTML = '<form><input id="name"><input id="other"></form>';
		const form = document.querySelector('form')!;
		form.addEventListener('input', validation.onInput);

		document.getElementById('other')!.dispatchEvent(new Event('input', { bubbles: true }));
		expect(validation.error('name')).toBeNull();

		document.getElementById('name')!.dispatchEvent(new Event('input', { bubbles: true }));
		expect(validation.error('name')).toBe('Enter a name.');
	});

	it('goes quiet again on reset', () => {
		const { validation } = setup();
		validation.touch('name');
		expect(validation.error('name')).not.toBeNull();

		validation.reset();
		expect(validation.error('name')).toBeNull();
	});
});

describe('rules mirrored from the API', () => {
	it('asks for a name, within the length the API takes', () => {
		expect(nameProblem('  ')).toBe('Enter a name.');
		expect(nameProblem('x'.repeat(81))).toBe('Use 80 characters or fewer.');
		expect(nameProblem('Cash')).toBeNull();
	});

	it("reports a name already in use in the caller's own words", () => {
		expect(nameProblem('Cash', (name) => name === 'Cash', 'Taken.')).toBe('Taken.');
		expect(nameProblem('Wallet', (name) => name === 'Cash', 'Taken.')).toBeNull();
	});

	it('compares tag names without regard to case', () => {
		expect(sameName(' Travel', 'travel ')).toBe(true);
		expect(sameName('Travel', 'Trips')).toBe(false);
	});

	it('takes only a full hex colour', () => {
		expect(colorProblem('#64748b')).toBeNull();
		expect(colorProblem('#fff')).not.toBeNull();
		expect(colorProblem('64748b')).not.toBeNull();
	});

	it('takes only a three-letter currency code', () => {
		expect(currencyProblem('php')).toBeNull();
		expect(currencyProblem('')).not.toBeNull();
		expect(currencyProblem('PH')).not.toBeNull();
		expect(currencyProblem('P1P')).not.toBeNull();
	});

	it('allows a logo to be left out, and rejects anything but an http(s) link', () => {
		expect(logoUrlProblem('')).toBeNull();
		expect(logoUrlProblem('https://example.com/logo.png')).toBeNull();
		expect(logoUrlProblem('data:image/png;base64,AAAA')).not.toBeNull();
		expect(logoUrlProblem('not a url')).not.toBeNull();
	});
});
