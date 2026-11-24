import { env } from 'cloudflare:test';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { parseAsOf, parseNavE4, parseNavpus, parseProviders, refreshFunds } from '../server/funds';
import { authedClient, json, makeAccount, type Call } from './helpers';

/** A page shaped as uitf.com.ph's `daily_navpu.php` is: a picker, a dated heading and a table per classification. */
const PAGE = `
<select><option value="">Check Daily NAVpu Values</option>
 <option value='6'> BDO Unibank, Inc. &nbsp;  <option value='3'> BPI WEALTH &nbsp; </select>
<h2>Unit Investment Trust Funds - Net Asset Values per unit (UITF NAVpus) as of Nov 23, 2026 <a href="#x">x</a></h2>
<!-- <div class="table-title"><h3>Peso</h3></div> -->
<div class="table-title mt-5">
	<img src="images/icons/philippine-peso.png" width="24" height="24" alt="">
	<h3 class="">Money Market Fund</h3>
</div>
<table><thead><tr><th>Fund Name</th><th>NAVpu</th><th>ROI-YOY%</th><th>ROI-YTD%</th></tr></thead><tbody>
	<tr>
		<td><a href="daily_navpu_details.php?fund_id=128&bank_id=6">BDO PESO MONEY MARKET FUND</a></td>
		<!-- <td></td> -->
		<td>1,942.3725</td><td>3.2617</td><td>2.8978</td>
	</tr>
	<tr>
		<td><a href="daily_navpu_details.php?fund_id=617&bank_id=6">Multi-Class S&amp;P 500 Feeder Fund​ - Class A</a></td>
		<td>0</td><td>----</td><td>----</td>
	</tr>
</tbody></table>
<div class="table-title mt-5">
	<img src="images/icons/dollar.png" width="24" height="24" alt="">
	<h3 class="">Money Market Fund</h3>
</div>
<table><tbody>
	<tr><td><a href="daily_navpu_details.php?fund_id=140&bank_id=6">BDO DOLLAR MONEY MARKET FUND</a></td><td>156.12</td><td>1</td><td>1</td></tr>
</tbody></table>`;

describe('reading uitf.com.ph', () => {
	it('reads a NAVPU to four decimals, and nothing that is not one', () => {
		expect(parseNavE4('1,942.3725')).toBe(19_423_725);
		expect(parseNavE4('76.78')).toBe(767_800);
		expect(parseNavE4('140')).toBe(1_400_000);
		expect(parseNavE4('0')).toBeNull();
		expect(parseNavE4('----')).toBeNull();
		expect(parseNavE4('0.840905')).toBe(8_409);
		expect(parseNavE4('0.840950')).toBe(8_410);
		expect(parseNavE4('1.123456789')).toBeNull();
	});

	it('reads the trust entities off the picker, and the day off the heading', () => {
		expect(parseProviders(PAGE)).toEqual([
			{ id: 6, name: 'BDO Unibank, Inc.' },
			{ id: 3, name: 'BPI WEALTH' },
		]);
		expect(parseAsOf(PAGE)).toBe('2026-11-23');
	});

	it('reads every fund with a NAVPU, under the classification it is listed in, peso funds only', () => {
		expect(parseNavpus(PAGE)).toEqual([
			{ fundId: '128', name: 'BDO PESO MONEY MARKET FUND', classification: 'Money Market Fund', navE4: 19_423_725 },
		]);
	});
});

describe('the NAVPU tick', () => {
	const NOW = new Date('2026-11-24T02:00:00Z');
	let fetched: string[];

	beforeEach(async () => {
		await env.DB.batch([
			env.DB.prepare("DELETE FROM stock_prices WHERE symbol LIKE 'UITF-%'"),
			env.DB.prepare('DELETE FROM fund_providers'),
		]);
		fetched = [];
		vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
			fetched.push(String(input));
			return new Response(PAGE, { status: 200 });
		});
	});

	afterEach(() => vi.restoreAllMocks());

	it('learns the trust entities first, then reads one page a tick, and only when it is due', async () => {
		expect(await refreshFunds(env, NOW)).toBe(0);
		expect(fetched).toEqual(['https://www.uitf.com.ph/daily_navpu.php']);

		// The peso fund, not the dollar one.
		expect(await refreshFunds(env, NOW)).toBe(1);
		expect(await refreshFunds(env, NOW)).toBe(1);
		expect(fetched.slice(1).sort()).toEqual([
			'https://www.uitf.com.ph/daily_navpu.php?bank_id=3',
			'https://www.uitf.com.ph/daily_navpu.php?bank_id=6',
		]);
		// Both read within the day: nothing is due.
		expect(await refreshFunds(env, NOW)).toBeNull();

		const fund = await env.DB.prepare("SELECT * FROM securities WHERE symbol = 'UITF-128'").first();
		expect(fund).toMatchObject({ kind: 'fund', price_e4: 19_423_725, as_of: '2026-11-23', classification: 'Money Market Fund' });
		expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM securities WHERE symbol = 'UITF-140'").first('n')).toBe(0);
		expect(
			await env.DB.prepare("SELECT close_e4 FROM stock_prices WHERE symbol = 'UITF-128' AND day = '2026-11-23'").first('close_e4'),
		).toBe(19_423_725);
	});

	it('works the day’s move out from the NAVPU before', async () => {
		await env.DB.prepare("INSERT INTO stock_prices (symbol, day, close_e4) VALUES ('UITF-128', '2026-11-20', 19400000)").run();
		await env.DB.prepare("INSERT INTO fund_providers (id, name) VALUES (6, 'BDO Unibank, Inc.')").run();
		await refreshFunds(env, NOW);
		// (1,942.3725 − 1,940.0000) / 1,940.0000 is 0.12%.
		expect(await env.DB.prepare("SELECT change_bp FROM securities WHERE symbol = 'UITF-128'").first('change_bp')).toBe(12);
	});
});

describe('a UITF trade', () => {
	let call: Call;
	let trust: string;

	beforeEach(async () => {
		await env.DB.prepare(
			`INSERT OR REPLACE INTO securities (symbol, name, price_e4, change_bp, as_of, kind, provider, provider_id, classification) VALUES
			 ('UITF-128', 'BDO PESO MONEY MARKET FUND', 19423725, NULL, '2999-12-31', 'fund', 'BDO Unibank, Inc.', 6, 'Money Market Fund'),
			 ('BDO', 'BDO Unibank, Inc.', 1200000, 51, '2999-12-31', 'stock', NULL, NULL, NULL)`,
		).run();
		call = await authedClient();
		trust = await makeAccount(call, { name: 'BDO Savings', startingBalance: 5_000_000 });
	});

	const fundTrade = (body: Record<string, unknown>) =>
		call('/trades', {
			method: 'POST',
			body: JSON.stringify({
				accountId: trust,
				symbol: 'UITF-128',
				kind: 'buy',
				occurredOn: '2026-11-23',
				price: 19_423_725,
				fees: 0,
				...body,
			}),
		});

	it('subscribes for what the trust bank took, in fractional units, under the fund’s name', async () => {
		// ₱10,000.00 at ₱1,942.3725 is 5.1483 units, which at that NAVPU come to ₱9,999.92.
		const response = await fundTrade({ units: 51_483, gross: 1_000_000 });
		expect(response.status).toBe(201);
		const { trade } = await json<{ trade: { units: number; gross: number; amount: number; transactionId: string } }>(response);
		expect(trade).toMatchObject({ units: 51_483, gross: 1_000_000, amount: -1_000_000 });

		const { transactions } = await json<{ transactions: { id: string; payee: string; notes: string }[] }>(
			await call('/transactions?month=2026-11&limit=50&offset=0'),
		);
		expect(transactions.find((row) => row.id === trade.transactionId)).toMatchObject({
			payee: 'BDO PESO MONEY MARKET FUND',
			notes: 'Subscribe 5.1483 units',
		});

		const summary = await json<{ holdings: number }>(await call('/summary?month=2026-11'));
		expect(summary.holdings).toBe(999_992);
	});

	it('redeems no more units than are held', async () => {
		await fundTrade({ units: 51_483, gross: 1_000_000 });
		expect((await fundTrade({ kind: 'sell', units: 51_484 })).status).toBe(409);
		const response = await fundTrade({ kind: 'sell', units: 20_000, gross: 388_475, fees: 5_000 });
		expect(response.status).toBe(201);
		expect((await json<{ trade: { amount: number } }>(response)).trade.amount).toBe(383_475);
	});

	it('leaves a stock in whole shares, its cash worked out', async () => {
		const stock = (body: Record<string, unknown>) => fundTrade({ symbol: 'BDO', price: 1_200_000, ...body });
		expect((await stock({ units: 15_000 })).status).toBe(400);
		expect((await stock({ units: 10_000, gross: 12_000 })).status).toBe(400);
		expect((await stock({ units: 10_000 })).status).toBe(201);
	});

	it('refuses a fund that is not listed', async () => {
		expect((await fundTrade({ symbol: 'UITF-999999', units: 10_000 })).status).toBe(404);
	});
});
