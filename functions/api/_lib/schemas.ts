import { z } from 'zod';
import { BUDGET_MODES } from './budget-mode';
import { DATE_PATTERN, DATE_TIME_PATTERN, MONTH_PATTERN } from './dates';
import { DEFAULT_CURRENCY } from './defaults';

export const CATEGORY_KINDS = ['income', 'expense'] as const;
export const CATEGORY_SCOPES = ['standard', 'transfer'] as const;

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

export const accountTypeCreateSchema = z.object({
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
	name: label,
	typeId: z.string().min(1),
	currency: currency.default(DEFAULT_CURRENCY),
	startingBalance: money.default(0),
	logoUrl: logoUrl.optional(),
	logoInvertDark: z.boolean().default(false),
});

export const accountUpdateSchema = z
	.object({
		name: label,
		typeId: z.string().min(1),
		currency,
		startingBalance: money,
		logoUrl,
		logoInvertDark: z.boolean(),
		archived: z.boolean(),
	})
	.partial()
	.refine((value) => Object.keys(value).length > 0, 'No fields to update.');

export const categoryCreateSchema = z.object({
	name: label,
	kind: z.enum(CATEGORY_KINDS),
	color: color.default('#64748b'),
	sortOrder: z.number().int().min(0).default(0),
	/** Only used when this is a top-level category; a child inherits its parent's. */
	appliesTo: z.enum(CATEGORY_SCOPES).default('standard'),
	/** Set to nest under a top-level category. Kind and scope are inherited. */
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

export const transactionCreateSchema = z.object({
	accountId: z.string().min(1),
	categoryId: z.string().min(1).nullable().default(null),
	amount: money.refine((value) => value !== 0, 'Amount cannot be zero.'),
	occurredOn: dateTimeString,
	payee: z.string().trim().max(120).default(''),
	notes: z.string().trim().max(500).default(''),
});

export const transactionUpdateSchema = z
	.object({
		accountId: z.string().min(1),
		categoryId: z.string().min(1).nullable(),
		amount: money.refine((value) => value !== 0, 'Amount cannot be zero.'),
		occurredOn: dateTimeString,
		payee: z.string().trim().max(120),
		notes: z.string().trim().max(500),
	})
	.partial()
	.refine((value) => Object.keys(value).length > 0, 'No fields to update.');

export const transferCreateSchema = z
	.object({
		fromAccountId: z.string().min(1),
		toAccountId: z.string().min(1),
		amount: z.number().int().positive('Transfer amount must be positive.'),
		occurredOn: dateTimeString,
		notes: z.string().trim().max(500).default(''),
		/** Must be a transfer-scope category, e.g. Cashflow or one of its children. */
		categoryId: z.string().min(1).nullable().default(null),
		/** Shown as the transfer's name. Blank becomes "From → To". */
		payee: z.string().trim().max(120).default(''),
	})
	.refine((value) => value.fromAccountId !== value.toAccountId, {
		message: 'Cannot transfer to the same account.',
		path: ['toAccountId'],
	});

/** A share of the month's planned income, in whole or fractional percent (e.g. 12.5). */
const percent = z.number().min(0).max(100);

export const budgetUpsertSchema = z
	.object({
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

export const transactionQuerySchema = z.object({
	month: monthString.optional(),
	from: dateString.optional(),
	to: dateString.optional(),
	accountId: z.string().min(1).optional(),
	categoryId: z.string().min(1).optional(),
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
