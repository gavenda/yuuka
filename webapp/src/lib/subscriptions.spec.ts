import { describe, expect, it } from 'vitest';
import { nextDay, ordinal, scheduleLabel, utcToday } from './subscriptions';

describe('ordinal', () => {
	it('suffixes each day of the month', () => {
		expect([1, 2, 3, 4, 10, 11, 12, 13, 14, 21, 22, 23, 30, 31].map(ordinal)).toEqual([
			'1st',
			'2nd',
			'3rd',
			'4th',
			'10th',
			'11th',
			'12th',
			'13th',
			'14th',
			'21st',
			'22nd',
			'23rd',
			'30th',
			'31st',
		]);
	});
});

describe('scheduleLabel', () => {
	it('names the day the subscription posts on', () => {
		expect(scheduleLabel(15)).toBe('Monthly on the 15th');
		expect(scheduleLabel(1)).toBe('Monthly on the 1st');
	});
});

describe('utcToday', () => {
	it('is the UTC calendar day, not the local one', () => {
		expect(utcToday(new Date('2026-09-19T23:59:00Z'))).toBe('2026-09-19');
		expect(utcToday(new Date('2026-09-20T00:00:00Z'))).toBe('2026-09-20');
	});
});

describe('nextDay', () => {
	it('rolls over month and year ends', () => {
		expect(nextDay('2026-09-19')).toBe('2026-09-20');
		expect(nextDay('2026-09-30')).toBe('2026-10-01');
		expect(nextDay('2026-12-31')).toBe('2027-01-01');
		expect(nextDay('2028-02-28')).toBe('2028-02-29');
	});
});
