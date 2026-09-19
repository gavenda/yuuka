import { invalidateSummaries } from './cache';
import { monthOf, nextOccurrence, today } from './dates';
import { stableId } from './ids';
import { NOW_SQL } from './sql';

/**
 * How many missed runs one subscription may catch up on in a single tick. A
 * daily cron only ever has one to post; this is what lets a subscription whose
 * runs were missed (an outage, a deploy that lost a tick) recover, without an
 * ancient schedule turning one tick into an unbounded write. Whatever is left is
 * picked up by the next tick.
 */
export const MAX_CATCH_UP = 12;

interface DueRow {
	id: string;
	user_id: string;
	day_of_month: number;
	next_run_on: string;
}

export interface SubscriptionRunResult {
	/** Transactions written by this run. */
	posted: number;
	/** Subscriptions whose batch threw; they stay due and are retried by the next tick. */
	failed: number;
}

/**
 * Posts every subscription that has come due as an ordinary transaction.
 *
 * Runs from the Worker's `scheduled` handler at 00:00 UTC, which is why an
 * automated transaction is stored as a bare date: there is no time of day for
 * anyone to choose.
 *
 * A subscription posts as the transaction it would have been had the user typed
 * it in — with two differences, both deliberate. It is flagged `automated`, so
 * the UI can lock its time of day. And it never triggers "Save the Change" nor
 * teaches the payee history: those follow what a person enters, not what a
 * schedule does.
 *
 * Overlapping or retried invocations cannot post a run twice. Each run is one
 * atomic batch whose insert only fires while `next_run_on` still equals the
 * date being posted, followed by the update that advances it, so whichever
 * invocation gets there second finds the date already moved on and writes
 * nothing. The transaction id is derived from the subscription and the date as
 * well, so even a bypassed guard would collide on the primary key rather than
 * duplicate the row.
 *
 * A user deleting an automated transaction is respected for the same reason:
 * `next_run_on` has already moved past that date, so nothing re-creates it.
 */
export async function runDueSubscriptions(env: Env, now: Date = new Date()): Promise<SubscriptionRunResult> {
	const db = env.DB;
	const through = today(now);
	const result: SubscriptionRunResult = { posted: 0, failed: 0 };

	const { results: due } = await db
		.prepare(
			'SELECT id, user_id, day_of_month, next_run_on FROM subscriptions WHERE enabled = 1 AND next_run_on <= ? ORDER BY next_run_on, id',
		)
		.bind(through)
		.all<DueRow>();

	// Months whose cached summary is now stale, per user.
	const touched = new Map<string, Set<string>>();

	for (const subscription of due) {
		try {
			const occurrences: { date: string; next: string }[] = [];
			for (
				let date = subscription.next_run_on;
				date <= through && occurrences.length < MAX_CATCH_UP;
				date = nextOccurrence(subscription.day_of_month, date)
			) {
				occurrences.push({ date, next: nextOccurrence(subscription.day_of_month, date) });
			}

			const statements = [];
			for (const { date, next } of occurrences) {
				statements.push(
					// Reads the subscription's own row, so what posts is what it holds at
					// this instant — an edit landing mid-run cannot leave a mixed row.
					db
						.prepare(
							`INSERT OR IGNORE INTO transactions (id, user_id, account_id, category_id, amount, occurred_on, payee, notes, transfer_id, automated)
							 SELECT ?, s.user_id, s.account_id, s.category_id, s.amount, ?, s.payee, s.notes, NULL, 1
							 FROM subscriptions s
							 WHERE s.id = ? AND s.enabled = 1 AND s.next_run_on = ?`,
						)
						.bind(await stableId('txn', subscription.id, date), date, subscription.id, date),
					db
						.prepare(
							`UPDATE subscriptions SET next_run_on = ?, last_run_on = ?, updated_at = ${NOW_SQL}
							 WHERE id = ? AND next_run_on = ? AND enabled = 1`,
						)
						.bind(next, date, subscription.id, date),
				);
			}

			const outcomes = await db.batch(statements);

			// Even statements are the inserts. A change count of zero means another
			// invocation got there first, or the row already existed.
			outcomes.forEach((outcome, index) => {
				if (index % 2 !== 0 || !outcome.meta.changes) return;
				result.posted += 1;
				const months = touched.get(subscription.user_id) ?? new Set<string>();
				months.add(monthOf(occurrences[index / 2].date));
				touched.set(subscription.user_id, months);
			});
		} catch (error) {
			// One subscription's failure must not stop the rest from posting.
			result.failed += 1;
			console.error(`Subscription ${subscription.id} failed`, error);
		}
	}

	for (const [userId, months] of touched) {
		try {
			await invalidateSummaries(env.CACHE, userId, months);
		} catch (error) {
			console.error('Could not purge cached summaries', error);
		}
	}

	return result;
}
