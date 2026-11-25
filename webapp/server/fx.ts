/**
 * Exchange rates, for a transfer between accounts in two currencies.
 *
 * A rate is only ever a suggestion: the form fills it in and the person may type the one their bank actually
 * gave, so nothing here is stored beside the ledger. Each leg of a transfer keeps its own amount in its own
 * account's currency, and the rate is what the two come to divided.
 *
 * Two free sources answer JSON with no key. Frankfurter publishes the European Central Bank's reference rates
 * by day — about thirty currencies, the peso among them — so a back-dated transfer gets that day's rate (or the
 * last business day's before it). open.er-api.com covers far more currencies but only today's rate, so it is
 * asked only when Frankfurter has no rate for the pair. Either answer is cached in KV under the pair and the
 * day: a past day's rate never changes, today's is kept for an hour.
 */

const FRANKFURTER = 'https://api.frankfurter.dev/v1';
const OPEN_ER_API = 'https://open.er-api.com/v6/latest';

/** How long a rate stands in KV: a past day's for a month, today's for an hour. */
const PAST_TTL_SECONDS = 30 * 24 * 60 * 60;
const TODAY_TTL_SECONDS = 60 * 60;

export type FxSource = 'ecb' | 'open-er-api' | 'identity';

export interface FxRate {
	/** One unit of `from` in `to`. */
	rate: number;
	/** The day the rate was published for, which can be before the one asked for (a weekend, a holiday). */
	date: string;
	source: FxSource;
}

const cacheKey = (from: string, to: string, date: string) => `fx:${from}:${to}:${date}`;

/** Frankfurter's rate for the pair on `date` (or `latest`), or null when it does not quote one of them. */
async function fromFrankfurter(from: string, to: string, date: string | null): Promise<FxRate | null> {
	const response = await fetch(`${FRANKFURTER}/${date ?? 'latest'}?base=${from}&symbols=${to}`, {
		headers: { Accept: 'application/json' },
	});
	if (!response.ok) return null;

	const body = (await response.json()) as { date?: string; rates?: Record<string, number> };
	const rate = body.rates?.[to];
	return typeof rate === 'number' && rate > 0 && body.date ? { rate, date: body.date, source: 'ecb' } : null;
}

/** open.er-api.com's latest rate for the pair, or null when it does not know one of them. */
async function fromOpenErApi(from: string, to: string, today: string): Promise<FxRate | null> {
	const response = await fetch(`${OPEN_ER_API}/${from}`, { headers: { Accept: 'application/json' } });
	if (!response.ok) return null;

	const body = (await response.json()) as { result?: string; rates?: Record<string, number> };
	const rate = body.rates?.[to];
	return body.result === 'success' && typeof rate === 'number' && rate > 0 ? { rate, date: today, source: 'open-er-api' } : null;
}

/**
 * The rate one `from` bought in `to` on `date`, or today's when the day is today or later. Null when neither
 * source quotes the pair or neither could be reached; a failed request is never cached, so the next asks again.
 */
export async function exchangeRate(cache: KVNamespace, from: string, to: string, date: string, today: string): Promise<FxRate | null> {
	if (from === to) return { rate: 1, date, source: 'identity' };

	const day = date < today ? date : today;
	const key = cacheKey(from, to, day);
	const cached = await cache.get<FxRate>(key, 'json');
	if (cached) return cached;

	let found: FxRate | null = null;
	try {
		found = await fromFrankfurter(from, to, day < today ? day : null);
		// Only today's rate is to be had from the second source, so a past day it cannot answer for stays unanswered
		// rather than being given today's under that day's name.
		if (!found && day === today) found = await fromOpenErApi(from, to, today);
	} catch (error) {
		console.error('Exchange rate lookup failed', error);
		return null;
	}
	if (!found) return null;

	await cache.put(key, JSON.stringify(found), { expirationTtl: day < today ? PAST_TTL_SECONDS : TODAY_TTL_SECONDS });
	return found;
}
