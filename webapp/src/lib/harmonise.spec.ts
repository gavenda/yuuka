import { describe, expect, it } from 'vitest';
import { harmonise } from './harmonise';
import { PALETTE } from './palette';

describe('harmonise', () => {
	// What the Android app draws for the same eight palette colours, read off its screen.
	it('gives the colours Android draws for the palette', () => {
		expect(PALETTE.map((slot) => harmonise(slot.light, true))).toEqual([
			'#3d75d8',
			'#ee625b',
			'#00ae94',
			'#fd982a',
			'#dd7ebe',
			'#008149',
			'#363faa',
			'#e24571',
		]);
	});

	it('leaves alone what is not a full hex colour', () => {
		expect(harmonise('teal', false)).toBe('teal');
		expect(harmonise('#fff', false)).toBe('#fff');
	});
});
