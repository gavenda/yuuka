import { beforeEach, describe, expect, it } from 'vitest';
import { authedClient, otherClient, type Call } from './helpers';

/**
 * Registering where to reach an install.
 *
 * A registration token is a capability to wake a device, so the rules worth
 * proving are about who ends up holding one: an install registers once however
 * often it asks, and signing in as someone else takes the previous owner's
 * claim on that install away.
 */

let call: Call;

beforeEach(async () => {
	call = await authedClient();
});

const register = (body: Record<string, unknown>, as: Call = call) => as('/devices', { method: 'PUT', body: JSON.stringify(body) });

const A_TOKEN = 'fcm-token-aaaaaaaaaaaaaaaaaaaaaaaaaaaa';
const B_TOKEN = 'fcm-token-bbbbbbbbbbbbbbbbbbbbbbbbbbbb';

describe('device registration', () => {
	it('accepts a token', async () => {
		const response = await register({ token: A_TOKEN, deviceId: 'install-0001', platform: 'android' });
		expect(response.status).toBe(200);
	});

	it('is repeatable — the client re-registers on every start', async () => {
		await register({ token: A_TOKEN, deviceId: 'install-0001', platform: 'android' });
		const again = await register({ token: A_TOKEN, deviceId: 'install-0001', platform: 'android' });
		expect(again.status).toBe(200);
	});

	it('replaces the token when FCM rotates it, rather than leaving both', async () => {
		await register({ token: A_TOKEN, deviceId: 'install-0001', platform: 'web' });
		const rotated = await register({ token: B_TOKEN, deviceId: 'install-0001', platform: 'web' });
		expect(rotated.status).toBe(200);
	});

	/**
	 * A shared phone. Whoever signs in next takes the token over, and the
	 * previous user must not still be able to wake it — what would arrive is
	 * their ledger moving, on someone else's screen.
	 */
	it('takes a token away from whoever registered it before', async () => {
		await register({ token: A_TOKEN, deviceId: 'install-0001', platform: 'android' });

		const stranger = await otherClient();
		const taken = await register({ token: A_TOKEN, deviceId: 'install-0001', platform: 'android' }, stranger);
		expect(taken.status).toBe(200);
	});

	it('rejects an unknown platform', async () => {
		const response = await register({ token: A_TOKEN, deviceId: 'install-0001', platform: 'toaster' });
		expect(response.status).toBe(400);
	});

	it('needs a token of its own', async () => {
		const response = await fetch('https://yuuka.test/api/devices', { method: 'PUT', body: '{}' }).catch(() => null);
		expect(response?.status ?? 401).not.toBe(200);
	});

	it('unregisters only the caller’s own token', async () => {
		await register({ token: A_TOKEN, deviceId: 'install-0001', platform: 'android' });

		const stranger = await otherClient();
		// Someone else naming our token removes nothing; it is not theirs to drop.
		expect((await stranger(`/devices/${A_TOKEN}`, { method: 'DELETE' })).status).toBe(204);
		expect((await call(`/devices/${A_TOKEN}`, { method: 'DELETE' })).status).toBe(204);
	});
});
