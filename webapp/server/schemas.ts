import { z } from 'zod';
import { BUDGET_MODES } from './budget-mode';
import { DATE_PATTERN, DATE_TIME_PATTERN, isRealDate, MONTH_PATTERN } from './dates';
import { DEFAULT_CURRENCY } from './defaults';

/**
 * What a category is for. 'income' and 'expense' categorise spending and
 * income; 'transfer' categorises movements between the user's own accounts.
 * One field rather than a kind and a separate scope: a transfer category had
 * no meaningful kind, and (income, transfer) was a state nothing could read.
 */
export const CATEGORY_KINDS = ['income', 'expense', 'transfer'] as const;

/** Amounts are integer minor units (cents); negative is an outflow. */
const money = z.number().int();
const label = z.string().trim().min(1, 'Required.').max(80);
const color = z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Must be a hex colour such as #64748b.');
/**
 * Exactly three letters. `Intl.NumberFormat` throws on anything else, so this
 * is what keeps a bad value from crashing every figure on the page; an unknown
 * but well-formed code simply renders as the code itself.
 */
const currency = z
	.string()
	.trim()
	.regex(/^[A-Za-z]{3}$/, 'Must be a 3-letter ISO currency code.')
	.transform((value) => value.toUpperCase());

/**
 * An optional logo URL.
 *
 * Restricted to http(s): the value ends up in an `<img src>`, and allowing
 * arbitrary schemes there invites `data:` payloads and similar surprises. An
 * empty string means "clear it", so the field can be emptied from a form
 * without sending a literal null.
 */
const logoUrl = z
	.union([z.null(), z.string().trim().max(2048, 'That URL is too long.')])
	.transform((value) => (value === null || value === '' ? null : value))
	.refine((value) => {
		if (value === null) return true;
		try {
			return ['http:', 'https:'].includes(new URL(value).protocol);
		} catch {
			return false;
		}
	}, 'Must be an http or https URL.');

export const dateString = z.string().regex(DATE_PATTERN, 'Must be a YYYY-MM-DD date.');
/** When a transaction records what time of day it happened, not just the date. */
export const dateTimeString = z.string().regex(DATE_TIME_PATTERN, 'Must be a YYYY-MM-DD date, optionally with a THH:MM time.');
export const monthString = z.string().regex(MONTH_PATTERN, 'Must be a YYYY-MM month.');

/**
 * An id the client chose for a row it is creating.
 *
 * Offline-first means a row exists, and is referenced by later rows, before the
 * server has ever seen it: a transaction entered on a plane names an account
 * that may itself still be queued. Letting the client name the row is what
 * makes that work without a second pass to rewrite references once the server
 * answers — the id the client wrote is the id the server stores.
 *
 * It is scoped to the caller's own data like every other id, so a collision
 * with someone else's row is impossible, and a repeat of the same create is a
 * no-op rather than a duplicate: that is what makes replaying a queued batch
 * safe. The shape is the server's own (`newId`), so nothing downstream can tell
 * which side generated it.
 */
export const clientId = (prefix: string) =>
	z
		.string()
		.regex(new RegExp(`^${prefix}_[0-9a-zA-Z]{8,64}$`), `Must be a ${prefix}_ identifier.`)
		.optional();

export const accountTypeCreateSchema = z.object({
	id: clientId('atp'),
	name: label,
	sortOrder: z.number().int().min(0).default(0),
});

export const accountTypeUpdateSchema = z
	.object({
		name: label,
		sortOrder: z.number().int().min(0),
		archived: z.boolean(),
	})
	.partial()
	.refine((value) => Object.keys(value).length > 0, 'No fields to update.');

export const accountCreateSchema = z.object({
	id: clientId('acc'),
	name: label,
	typeId: z.string().min(1),
	currency: currency.default(DEFAULT_CURRENCY),
	startingBalance: money.default(0),
	logoUrl: logoUrl.optional(),
	logoInvertDark: z.boolean().default(false),
	/** Whether this account's own purchases round up under "Save the Change". */
	roundUpSource: z.boolean().default(false),
});

export const accountUpdateSchema = z
	.object({
		name: label,
		typeId: z.string().min(1),
		currency,
		startingBalance: money,
		logoUrl,
		logoInvertDark: z.boolean(),
		roundUpSource: z.boolean(),
		archived: z.boolean(),
	})
	.partial()
	.refine((value) => Object.keys(value).length > 0, 'No fields to update.');

export const accountAdjustSchema = z.object({
	/** The id for the transaction this posts, so an offline adjustment keeps the row the client already showed. */
	id: clientId('txn'),
	/** The account's balance after this adjustment posts; the API computes the difference itself. */
	balance: money,
	occurredOn: dateTimeString,
	payee: z.string().trim().max(120).default(''),
	notes: z.string().trim().max(500).default(''),
});

export const categoryCreateSchema = z.object({
	id: clientId('cat'),
	name: label,
	kind: z.enum(CATEGORY_KINDS),
	color: color.default('#64748b'),
	sortOrder: z.number().int().min(0).default(0),
	/** Set to nest under a top-level category, whose kind it then inherits. */
	parentId: z.string().min(1).nullable().default(null),
});

export const categoryUpdateSchema = z
	.object({
		name: label,
		kind: z.enum(CATEGORY_KINDS),
		color,
		sortOrder: z.number().int().min(0),
		archived: z.boolean(),
	})
	.partial()
	.refine((value) => Object.keys(value).length > 0, 'No fields to update.');

export const MAX_TAGS_PER_TRANSACTION = 10;

export const tagCreateSchema = z.object({
	id: clientId('tag'),
	name: label,
	color: color.default('#64748b'),
});

export const tagUpdateSchema = z
	.object({
		name: label,
		color,
	})
	.partial()
	.refine((value) => Object.keys(value).length > 0, 'No fields to update.');

/** The tags a transaction wears. Repeats collapse, since a transaction either has a tag or it does not. */
const tagIds = z
	.array(z.string().min(1))
	.max(MAX_TAGS_PER_TRANSACTION, `A transaction takes at most ${MAX_TAGS_PER_TRANSACTION} tags.`)
	.transform((ids) => [...new Set(ids)]);

/** The three rows a transfer is: its parent and the two legs that reference it. */
const transferIds = z.object({
	transferId: clientId('tfr'),
	fromId: clientId('txn'),
	toId: clientId('txn'),
});

/** The same three rows, for the transfer a round-up posts. */
const roundUpIds = transferIds;

export const transactionCreateSchema = z.object({
	id: clientId('txn'),
	/**
	 * Ids for the "Save the Change" transfer this create may trigger. A client
	 * that worked out the round-up itself — as an offline one must, to show the
	 * right balance — names the three rows it already drew, so the API's answer
	 * replaces them instead of arriving as a second copy.
	 */
	roundUpIds: roundUpIds.optional(),
	accountId: z.string().min(1),
	categoryId: z.string().min(1).nullable().default(null),
	amount: money.refine((value) => value !== 0, 'Amount cannot be zero.'),
	occurredOn: dateTimeString,
	payee: z.string().trim().max(120).default(''),
	notes: z.string().trim().max(500).default(''),
	tagIds: tagIds.default([]),
});

export const transactionUpdateSchema = z
	.object({
		accountId: z.string().min(1),
		categoryId: z.string().min(1).nullable(),
		amount: money.refine((value) => value !== 0, 'Amount cannot be zero.'),
		occurredOn: dateTimeString,
		payee: z.string().trim().max(120),
		notes: z.string().trim().max(500),
		/** Replaces the whole set. Left out, the transaction's tags stay as they are. */
		tagIds,
	})
	.partial()
	.refine((value) => Object.keys(value).length > 0, 'No fields to update.');

export const transferCreateSchema = z
	.object({
		ids: transferIds.optional(),
		fromAccountId: z.string().min(1),
		toAccountId: z.string().min(1),
		amount: z.number().int().positive('Transfer amount must be positive.'),
		occurredOn: dateTimeString,
		notes: z.string().trim().max(500).default(''),
		/** Must be a transfer-scope category, e.g. Cashflow or one of its children. */
		categoryId: z.string().min(1).nullable().default(null),
		/** Shown as the transfer's name. Blank becomes "From → To". */
		payee: z.string().trim().max(120).default(''),
		/** Worn by both legs, so the pair reads the same from either account. Left out on an edit, the tags stay as they are. */
		tagIds: tagIds.optional(),
	})
	.refine((value) => value.fromAccountId !== value.toAccountId, {
		message: 'Cannot transfer to the same account.',
		path: ['toAccountId'],
	});

/** A subscription's payee is required — it is the only label the subscription has in a list. */
const subscriptionPayee = z.string().trim().min(1, 'Required.').max(120);

/** A day that exists on the calendar — `DATE_PATTERN` alone would accept `2026-02-31`. */
const realDate = dateString.refine(isRealDate, 'Must be a real calendar date.');

export const subscriptionCreateSchema = z.object({
	id: clientId('sub'),
	accountId: z.string().min(1),
	/** Must be a standard-scope category — a subscription posts an ordinary transaction, never a transfer. */
	categoryId: z.string().min(1).nullable().default(null),
	amount: money.refine((value) => value !== 0, 'Amount cannot be zero.'),
	payee: subscriptionPayee,
	notes: z.string().trim().max(500).default(''),
	/** The first run. Its day of the month anchors every later one. */
	startOn: realDate,
});

export const subscriptionUpdateSchema = z
	.object({
		accountId: z.string().min(1),
		categoryId: z.string().min(1).nullable(),
		amount: money.refine((value) => value !== 0, 'Amount cannot be zero.'),
		payee: subscriptionPayee,
		notes: z.string().trim().max(500),
		/** Restarts the schedule: this becomes the next run, and its day the new anchor. */
		startOn: realDate,
		/** Paused subscriptions post nothing. Resuming skips whatever fell due while paused. */
		enabled: z.boolean(),
	})
	.partial()
	.refine((value) => Object.keys(value).length > 0, 'No fields to update.');

/** A share of the month's planned income, in whole or fractional percent (e.g. 12.5). */
const percent = z.number().min(0).max(100);

export const budgetUpsertSchema = z
	.object({
		id: clientId('bdg'),
		categoryId: z.string().min(1),
		month: monthString,
		amount: z.number().int().min(0, 'Budgeted amount cannot be negative.').optional(),
		percent: percent.optional(),
	})
	.refine((value) => (value.amount === undefined) !== (value.percent === undefined), 'Provide either an amount or a percent, not both.');

export const incomePlanUpsertSchema = z
	.object({
		month: monthString,
		amount: money.min(0, 'Planned income cannot be negative.'),
		/** Whether `amount` was typed directly or is the take-home net of `grossAmount`. */
		mode: z.enum(['gross', 'fixed']).default('fixed'),
		grossAmount: money.min(0).optional(),
	})
	.refine((value) => value.mode !== 'gross' || value.grossAmount !== undefined, {
		message: 'Gross mode requires a gross amount.',
		path: ['grossAmount'],
	});

/** Minor-unit multiple "Save the Change" rounds up to: 1000 (₱10) or 10000 (₱100). */
export const ROUND_TO_VALUES = [1000, 10000] as const;

export const roundUpRuleUpdateSchema = z
	.object({
		enabled: z.boolean(),
		roundTo: z.union([z.literal(1000), z.literal(10000)]),
		destinationAccountId: z.string().min(1).nullable(),
		/** Must be a transfer-scope category, e.g. Cashflow or one of its children. Null means uncategorised. */
		categoryId: z.string().min(1).nullable(),
	})
	.partial()
	.refine((value) => Object.keys(value).length > 0, 'No fields to update.');

export const settingsUpdateSchema = z
	.object({
		displayCurrency: currency,
		/** Whether a category's planned amount applies to every month or is set per month. */
		budgetMode: z.enum(BUDGET_MODES),
		/** Which account a new transaction opens on. Null falls back to the first active account. */
		defaultAccountId: z.string().min(1).nullable(),
	})
	.partial()
	.refine((value) => Object.keys(value).length > 0, 'No fields to update.');

/** One id, or several separated by commas, as a filter that matches any of them. */
const idList = z
	.string()
	.transform((value) =>
		value
			.split(',')
			.map((id) => id.trim())
			.filter(Boolean),
	)
	.pipe(z.array(z.string().min(1)).min(1).max(50));

export const transactionQuerySchema = z.object({
	month: monthString.optional(),
	from: dateString.optional(),
	to: dateString.optional(),
	accountId: idList.optional(),
	/** `none` stands for uncategorised, alongside real ids. */
	categoryId: idList.optional(),
	/** A transaction wearing any of these tags. */
	tagId: idList.optional(),
	search: z.string().trim().max(120).optional(),
	limit: z.coerce.number().int().min(1).max(200).default(50),
	offset: z.coerce.number().int().min(0).default(0),
});

export const payeeQuerySchema = z.object({
	search: z.string().trim().max(120).optional(),
	limit: z.coerce.number().int().min(1).max(50).default(20),
});

export const monthQuerySchema = z.object({
	month: monthString.optional(),
});

export const listQuerySchema = z.object({
	includeArchived: z
		.enum(['true', 'false'])
		.default('false')
		.transform((value) => value === 'true'),
});

/**
 * Which table an offline mutation was aimed at, for the staleness check that
 * makes a queued batch last-write-wins rather than blind replay.
 *
 * Only rows a person can edit from two places at once are listed. Anything
 * absent is applied without a check, which is the same answer a row that has
 * never been touched elsewhere would have given.
 */
export const SYNC_ENTITIES = [
	'account',
	'accountType',
	'category',
	'tag',
	'transaction',
	'transfer',
	'subscription',
	'budget',
	'settings',
	'roundUpRule',
] as const;

/** Methods a queued operation may use. A GET is never queued: reading is what a sync already does. */
const SYNC_METHODS = ['POST', 'PATCH', 'PUT', 'DELETE'] as const;

export const syncOperationSchema = z.object({
	/** The client's own name for this operation, echoed in the result so it can retire the right queue entry. */
	opId: z.string().min(1).max(64),
	method: z.enum(SYNC_METHODS),
	/** An `/api/...` path, exactly as the same call would look online. */
	path: z.string().min(2).max(300).startsWith('/api/'),
	/** When the user made the change, not when it was sent. This is what a stale edit is judged against. */
	at: z.string().datetime(),
	/** What the operation touches, for the staleness check. Left out, the operation is applied unconditionally. */
	entity: z.enum(SYNC_ENTITIES).optional(),
	/** Which row, for an entity that has more than one. */
	id: z.string().min(1).max(80).optional(),
	body: z.unknown().optional(),
});

/** At most a few hundred: a queue longer than this is a sign of something wrong, not of a long flight. */
export const MAX_SYNC_OPERATIONS = 200;

export const syncBatchSchema = z.object({
	operations: z.array(syncOperationSchema).max(MAX_SYNC_OPERATIONS, `A batch takes at most ${MAX_SYNC_OPERATIONS} operations.`),
});

export const deviceRegisterSchema = z.object({
	/** The FCM registration token. Opaque, and rotated by FCM rather than by us. */
	token: z.string().min(10).max(4096),
	/** The install's own stable id, which survives the token being rotated. */
	deviceId: z.string().min(8).max(80),
	platform: z.enum(['android', 'web']),
});
