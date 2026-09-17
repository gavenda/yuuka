import { beforeEach, describe, expect, it } from 'vitest';
import { formatMoney } from './money';
import { displayMoney, MASK, useAmountVisibility } from './privacy';

const { hidden, toggle } = useAmountVisibility();

beforeEach(() => {
	hidden.value = false;
});

describe('amount visibility', () => {
	it('shows real figures by default', () => {
		expect(displayMoney(123456, 'PHP')).toBe(formatMoney(123456, 'PHP'));
	});

	it('masks the digits once hidden', () => {
		toggle();
		expect(displayMoney(123456, 'PHP')).toContain(MASK);
		expect(displayMoney(123456, 'PHP')).not.toMatch(/\d/);
	});

	it('keeps the currency visible, so a masked column still says what it measures', () => {
		toggle();
		expect(displayMoney(123456, 'PHP')).toBe('₱' + MASK);
		expect(displayMoney(123456, 'USD')).toBe('$' + MASK);
		// A code Intl does not recognise is rendered as the code itself. The
		// separator it inserts is a non-breaking space, so match on the parts
		// rather than on an exact string.
		expect(displayMoney(123456, 'ZZZ')).toMatch(/^ZZZ\s/u);
		expect(displayMoney(123456, 'ZZZ')).toContain(MASK);
	});

	it('masks to a fixed width, so the magnitude does not leak', () => {
		toggle();
		// A mask that grew with the amount would give away what it hides.
		expect(displayMoney(1, 'PHP')).toBe(displayMoney(999_999_999, 'PHP'));
		expect(displayMoney(0, 'PHP')).toBe(displayMoney(50_000, 'PHP'));
	});

	it('leaks no digits for any magnitude', () => {
		toggle();
		for (const amount of [0, 1, 99, 100, 123456, 999_999_999]) {
			expect(displayMoney(amount, 'PHP'), String(amount)).not.toMatch(/\d/);
		}
	});

	it('falls back rather than throwing on a malformed currency', () => {
		toggle();
		expect(() => displayMoney(123456, 'BAD!')).not.toThrow();
		expect(displayMoney(123456, 'BAD!')).toContain(MASK);
	});

	it('defaults to the app currency when none is given', () => {
		toggle();
		expect(displayMoney(123456)).toBe(displayMoney(123456, 'PHP'));
	});

	it('toggles back', () => {
		toggle();
		expect(displayMoney(500, 'PHP')).toContain(MASK);
		toggle();
		expect(displayMoney(500, 'PHP')).toBe(formatMoney(500, 'PHP'));
	});

	it('is one shared switch, not a per-caller setting', () => {
		const second = useAmountVisibility();
		toggle();
		expect(second.hidden.value).toBe(true);
	});
});
