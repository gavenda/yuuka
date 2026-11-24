/**
 * UITF NAVPUs, scraped from uitf.com.ph — the Trust Officers Association of the Philippines' listing of
 * every unit investment trust fund's net asset value per unit. It has no API: `daily_navpu.php?bank_id=<id>`
 * is a plain HTML page of one trust entity's funds at their latest NAVPU, a table per classification under
 * an icon that says the currency, each fund a link carrying its `fund_id`. Its date filter and a fund's
 * history are behind a reCAPTCHA, so past NAVPUs are not to be had: a fund's closes start the day this
 * first sees it, and before that `holdingsValue` counts it at cost.
 *
 * Its robots.txt asks for a minute between requests, so a tick asks for one page at most, every five
 * minutes: a trust entity someone holds a fund of when it has not been read for `HELD_EVERY_HOURS`, any
 * other when it has not been for `CATALOGUE_EVERY_HOURS`, the longest unread first. Which trust entities
 * there are is read off the page's own picker into `fund_providers`, on the first tick and on every page
 * after. Only peso funds are kept, since a broker account is a peso account.
 *
 * A fund is a security like a stock: `securities` (kind `fund`, symbol `UITF-<fund_id>`) and a row a day
 * in `stock_prices`. A NAVPU is quoted to four decimals, the scale prices are kept in.
 */

import { invalidateSummaries } from './cache';
import { currentMonth } from './dates';

const UITF = 'https://www.uitf.com.ph';

/** The UITF tick; must match `triggers.crons` in `wrangler.jsonc`. */
export const FUND_CRON = '*/5 * * * *';

/** How often a trust entity someone holds a fund of is read: its NAVPUs come out once a banking day. */
const HELD_EVERY_HOURS = 6;

/** How often every other trust entity is read, so the funds to pick from stay current. */
const CATALOGUE_EVERY_HOURS = 24;

/** A fund's symbol: the prefix and its id on uitf.com.ph. No PSE symbol has a hyphen. */
export const FUND_PREFIX = 'UITF-';
export const fundSymbol = (fundId: string | number) => `${FUND_PREFIX}${fundId}`;

/** The icon over a table of peso funds; a table under any other (dollar, euro) is left out, since a trade's account holds pesos. */
const PESO_ICON = 'philippine-peso';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

export interface FundProvider {
	id: number;
	name: string;
}

export interface FundNavpu {
	fundId: string;
	name: string;
	classification: string;
	navE4: number;
}

/** Text as the page writes it: tags out, the entities it uses decoded, zero-width marks gone, spaces collapsed. */
export function plainText(html: string): string {
	return html
		.replace(/<[^>]*>/g, '')
		.replace(/&nbsp;/g, ' ')
		.replace(/&quot;/g, '"')
		.replace(/&#0?39;|&apos;/g, "'")
		.replace(/&lt;/g, '<')
		.replace(/&gt;/g, '>')
		.replace(/&amp;/g, '&')
		.replace(/[​-‍﻿]/g, '')
		.replace(/\s+/g, ' ')
		.trim();
}

/**
 * A NAVPU as the page prints it (`1,942.3725`) in ten-thousandths, split on the decimal point as money is. A
 * trust bank may quote six decimals (`0.840905`); those are rounded half up to four, since a price is kept in
 * ten-thousandths. Null for anything else — the `0` and `----` it prints for a fund with no NAVPU that day among them.
 */
export function parseNavE4(text: string): number | null {
	const match = /^(\d{1,3}(?:,\d{3})*|\d+)(?:\.(\d{1,8}))?$/.exec(text.trim());
	if (!match) return null;
	const fraction = (match[2] ?? '').padEnd(5, '0');
	const value = Number(match[1]!.replace(/,/g, '')) * 10_000 + Number(fraction.slice(0, 4)) + (fraction[4]! >= '5' ? 1 : 0);
	return value > 0 && Number.isSafeInteger(value) ? value : null;
}

/** The trust entities in the page's picker, each `<option value='<id>'>` and its name. */
export function parseProviders(html: string): FundProvider[] {
	const providers: FundProvider[] = [];
	for (const match of html.matchAll(/<option value='(\d+)'>([^<]*)/g)) {
		const name = plainText(match[2]!);
		if (name) providers.push({ id: Number(match[1]), name });
	}
	return providers;
}

/** The day the page's NAVPUs are of, from its heading (`… as of Nov 23, 2026`), as `YYYY-MM-DD`. */
export function parseAsOf(html: string): string | null {
	const match = /as of\s+([A-Z][a-z]{2})\s+(\d{1,2}),\s+(\d{4})/.exec(html);
	if (!match) return null;
	const month = MONTHS.indexOf(match[1]!);
	if (month < 0) return null;
	return `${match[3]}-${String(month + 1).padStart(2, '0')}-${match[2]!.padStart(2, '0')}`;
}

/**
 * Every fund on a page with a NAVPU, in page order. The page's commented-out markup goes first, since it
 * keeps old headings and cells there; then each table title sets the classification and currency for the
 * rows after it (only a peso table's rows are kept), and a row is a fund's link and its cells, the NAVPU the first after the name.
 */
export function parseNavpus(html: string): FundNavpu[] {
	const page = html.replace(/<!--[\s\S]*?-->/g, '');
	const funds: FundNavpu[] = [];
	let classification = '';
	let peso = false;
	for (const match of page.matchAll(/<div class="table-title[^"]*">([\s\S]*?)<\/div>|<tr>([\s\S]*?)<\/tr>/g)) {
		if (match[1] !== undefined) {
			const icon = /images\/icons\/([a-z-]+)\.png/.exec(match[1])?.[1];
			peso = icon === PESO_ICON;
			classification = plainText(/<h3[^>]*>([\s\S]*?)<\/h3>/.exec(match[1])?.[1] ?? '');
			continue;
		}
		const link = /fund_id=(\d+)[^"]*">([\s\S]*?)<\/a>/.exec(match[2]!);
		if (!link || !peso) continue;
		const cells = [...match[2]!.matchAll(/<td[^>]*>([\s\S]*?)<\/td>/g)].map((cell) => plainText(cell[1]!));
		const navE4 = parseNavE4(cells[1] ?? '');
		const name = plainText(link[2]!);
		if (navE4 === null || !name) continue;
		funds.push({ fundId: link[1]!, name: name.slice(0, 200), classification, navE4 });
	}
	return funds;
}

async function fetchPage(path: string): Promise<string> {
	const response = await fetch(`${UITF}/${path}`, { headers: { Accept: 'text/html', 'User-Agent': 'Mozilla/5.0 (compatible; Yuuka)' } });
	if (!response.ok) throw new Error(`uitf.com.ph answered ${response.status} for ${path}.`);
	return response.text();
}

function upsertProviders(env: Env, providers: FundProvider[]): D1PreparedStatement[] {
	return providers.map((provider) =>
		env.DB.prepare('INSERT INTO fund_providers (id, name) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET name = excluded.name').bind(
			provider.id,
			provider.name,
		),
	);
}

const hoursBefore = (now: Date, hours: number) => new Date(now.getTime() - hours * 3600_000).toISOString();

/**
 * Reads at most one page: the list of trust entities while there is none, otherwise the most out of date
 * trust entity that is due. Answers how many funds it wrote, or null when it asked for nothing.
 */
export async function refreshFunds(env: Env, now: Date): Promise<number | null> {
	const known = await env.DB.prepare('SELECT COUNT(*) AS n FROM fund_providers').first<{ n: number }>();
	if (!known?.n) {
		const providers = parseProviders(await fetchPage('daily_navpu.php'));
		if (providers.length === 0) throw new Error('uitf.com.ph listed no trust entities.');
		await env.DB.batch(upsertProviders(env, providers));
		return 0;
	}

	const held = `EXISTS (SELECT 1 FROM securities s JOIN trades t ON t.symbol = s.symbol WHERE s.provider_id = p.id)`;
	const due = await env.DB.prepare(
		`SELECT p.id, p.name FROM fund_providers p
		 WHERE p.checked_at IS NULL OR p.checked_at < ? OR (p.checked_at < ? AND ${held})
		 ORDER BY ${held} DESC, p.checked_at IS NOT NULL, p.checked_at
		 LIMIT 1`,
	)
		.bind(hoursBefore(now, CATALOGUE_EVERY_HOURS), hoursBefore(now, HELD_EVERY_HOURS))
		.first<{ id: number; name: string }>();
	if (!due) return null;

	// Marked first, so a page that keeps failing waits its turn rather than holding up the rest.
	await env.DB.prepare('UPDATE fund_providers SET checked_at = ? WHERE id = ?').bind(now.toISOString(), due.id).run();

	const html = await fetchPage(`daily_navpu.php?bank_id=${due.id}`);
	const asOf = parseAsOf(html);
	if (!asOf) throw new Error(`uitf.com.ph gave no date for trust entity ${due.id}.`);
	const funds = parseNavpus(html);

	const statements = upsertProviders(env, parseProviders(html));
	for (const fund of funds) {
		const symbol = fundSymbol(fund.fundId);
		statements.push(
			// The day's move is worked out from the last NAVPU before this one, when there is one; an older
			// page than the one already kept changes nothing.
			env.DB.prepare(
				`INSERT INTO securities (symbol, name, price_e4, change_bp, as_of, kind, provider, provider_id, classification)
				 VALUES (?1, ?2, ?3,
				   (SELECT CAST(ROUND((?3 - close_e4) * 10000.0 / close_e4) AS INTEGER) FROM stock_prices
				     WHERE symbol = ?1 AND day < ?4 AND close_e4 > 0 ORDER BY day DESC LIMIT 1),
				   ?4, 'fund', ?5, ?6, ?7)
				 ON CONFLICT (symbol) DO UPDATE SET name = excluded.name, price_e4 = excluded.price_e4, change_bp = excluded.change_bp,
				   as_of = excluded.as_of, provider = excluded.provider, provider_id = excluded.provider_id,
				   classification = excluded.classification, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
				 WHERE excluded.as_of >= securities.as_of`,
			).bind(symbol, fund.name, fund.navE4, asOf, due.name, due.id, fund.classification || null),
			env.DB.prepare(
				'INSERT INTO stock_prices (symbol, day, close_e4) VALUES (?, ?, ?) ON CONFLICT (symbol, day) DO UPDATE SET close_e4 = excluded.close_e4',
			).bind(symbol, asOf, fund.navE4),
		);
	}
	for (let index = 0; index < statements.length; index += 200) {
		await env.DB.batch(statements.slice(index, index + 200));
	}

	// Net worth counts holdings at the latest NAVPU, so this month's summary is out of date for whoever holds one of these.
	const { results } = await env.DB.prepare(
		'SELECT DISTINCT t.user_id FROM trades t JOIN securities s ON s.symbol = t.symbol WHERE s.provider_id = ?',
	)
		.bind(due.id)
		.all<{ user_id: string }>();
	const month = currentMonth();
	await Promise.all(results.map((row) => invalidateSummaries(env.CACHE, row.user_id, [month]).catch(() => {})));
	return funds.length;
}

/** The five-minute tick: one page of NAVPUs, if one is due. */
export async function runFundTick(env: Env, now: Date): Promise<void> {
	try {
		await refreshFunds(env, now);
	} catch (error) {
		console.error('Refreshing UITF NAVPUs failed', error);
	}
}
