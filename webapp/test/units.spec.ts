import { describe, expect, it } from 'vitest';
import {
	addMonths,
	currentMonth,
	dateInMonth,
	dayOf,
	daysInMonth,
	firstOccurrenceOnOrAfter,
	isDate,
	isMonth,
	isRealDate,
	monthOf,
	monthRange,
	nextOccurrence,
} from '../server/dates';
import { buildUpdate, toSqliteBool } from '../server/sql';
import { bearerToken } from '../server/middleware/auth';

describe('date helpers', () => {
	it('validates months and dates', () => {
		expect(isMonth('2026-09')).toBe(true);
		expect(isMonth('2026-13')).toBe(false);
		expect(isMonth('2026-9')).toBe(false);
		expect(isDate('2026-09-15')).toBe(true);
		expect(isDate('2026-09-32')).toBe(false);
	});

	it('shifts months across year boundaries', () => {
		expect(addMonths('2026-09', 1)).toBe('2026-10');
		expect(addMonths('2026-12', 1)).toBe('2027-01');
		expect(addMonths('2026-01', -1)).toBe('2025-12');
		expect(addMonths('2026-01', -13)).toBe('2024-12');
		expect(addMonths('2026-06', 12)).toBe('2027-06');
	});

	it('produces a half-open range for a month', () => {
		expect(monthRange('2026-09')).toEqual({ start: '2026-09-01', end: '2026-10-01' });
		expect(monthRange('2026-12')).toEqual({ start: '2026-12-01', end: '2027-01-01' });
	});

	it('derives the month a date falls in', () => {
		expect(monthOf('2026-09-15')).toBe('2026-09');
		expect(currentMonth(new Date('2026-03-08T00:00:00Z'))).toBe('2026-03');
	});
});

describe('monthly schedule helpers', () => {
	it('tells a real day from one that only looks like a date', () => {
		expect(isRealDate('2028-02-29')).toBe(true);
		expect(isRealDate('2027-02-29')).toBe(false);
		expect(isRealDate('2026-04-31')).toBe(false);
		expect(isRealDate('2026-4-3')).toBe(false);
	});

	it('knows how long a month is, leap years included', () => {
		expect(daysInMonth('2026-09')).toBe(30);
		expect(daysInMonth('2026-12')).toBe(31);
		expect(daysInMonth('2027-02')).toBe(28);
		expect(daysInMonth('2028-02')).toBe(29);
		expect(daysInMonth('2100-02')).toBe(28);
	});

	it('clamps a day to the end of a short month', () => {
		expect(dateInMonth('2026-09', 15)).toBe('2026-09-15');
		expect(dateInMonth('2026-09', 31)).toBe('2026-09-30');
		expect(dateInMonth('2027-02', 30)).toBe('2027-02-28');
		expect(dayOf('2026-09-05')).toBe(5);
	});

	it('finds the occurrence in the following month, keeping the anchor day', () => {
		expect(nextOccurrence(15, '2026-09-15')).toBe('2026-10-15');
		expect(nextOccurrence(31, '2026-12-31')).toBe('2027-01-31');
		expect(nextOccurrence(31, '2027-01-31')).toBe('2027-02-28');
		expect(nextOccurrence(31, '2027-02-28')).toBe('2027-03-31');
	});

	it('finds the first occurrence on or after a date', () => {
		expect(firstOccurrenceOnOrAfter(15, '2026-09-10')).toBe('2026-09-15');
		expect(firstOccurrenceOnOrAfter(15, '2026-09-15')).toBe('2026-09-15');
		expect(firstOccurrenceOnOrAfter(15, '2026-09-16')).toBe('2026-10-15');
		expect(firstOccurrenceOnOrAfter(31, '2026-09-01')).toBe('2026-09-30');
		expect(firstOccurrenceOnOrAfter(5, '2026-12-20')).toBe('2027-01-05');
	});
});

describe('buildUpdate', () => {
	it('skips undefined fields', () => {
		expect(buildUpdate({ name: 'a', color: undefined, sort_order: 2 })).toEqual({
			clause: 'name = ?, sort_order = ?',
			values: ['a', 2],
		});
	});

	it('keeps nulls, which are a meaningful value', () => {
		expect(buildUpdate({ category_id: null })).toEqual({ clause: 'category_id = ?', values: [null] });
	});
});

describe('toSqliteBool', () => {
	it('maps booleans to 0/1 and leaves undefined alone', () => {
		expect(toSqliteBool(true)).toBe(1);
		expect(toSqliteBool(false)).toBe(0);
		expect(toSqliteBool(undefined)).toBeUndefined();
	});
});

describe('bearerToken', () => {
	it('extracts the token, case-insensitively', () => {
		expect(bearerToken('Bearer abc123')).toBe('abc123');
		expect(bearerToken('bearer abc123')).toBe('abc123');
		expect(bearerToken('  Bearer   abc123  ')).toBe('abc123');
	});

	it('returns null for anything else', () => {
		expect(bearerToken(undefined)).toBeNull();
		expect(bearerToken('')).toBeNull();
		expect(bearerToken('Basic abc123')).toBeNull();
	});
});
