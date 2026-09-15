import { describe, expect, it } from 'vitest';
import { addMonths, currentMonth, isDate, isMonth, monthOf, monthRange } from '../functions/api/_lib/dates';
import { buildUpdate, toSqliteBool } from '../functions/api/_lib/sql';
import { bearerToken } from '../functions/api/_lib/middleware/auth';

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
