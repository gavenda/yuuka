import { describe, expect, it } from 'vitest';
import { addMonths, currentMonth, isYuukaBirthday, today } from './dates';

describe('date helpers', () => {
	it('shifts months across year boundaries', () => {
		expect(addMonths('2026-09', 1)).toBe('2026-10');
		expect(addMonths('2026-12', 1)).toBe('2027-01');
		expect(addMonths('2026-01', -1)).toBe('2025-12');
		expect(addMonths('2026-01', -13)).toBe('2024-12');
	});

	it('formats the current month and day in local time', () => {
		// Built from local parts, so a late-evening UTC offset cannot roll the date.
		const date = new Date(2026, 2, 8, 23, 30);
		expect(currentMonth(date)).toBe('2026-03');
		expect(today(date)).toBe('2026-03-08');
	});

	it("knows Yuuka's birthday, whatever the year", () => {
		expect(isYuukaBirthday(new Date(2026, 2, 14))).toBe(true);
		expect(isYuukaBirthday(new Date(2031, 2, 14, 23, 59))).toBe(true);
		expect(isYuukaBirthday(new Date(2026, 2, 13))).toBe(false);
		expect(isYuukaBirthday(new Date(2026, 3, 14))).toBe(false);
	});
});
