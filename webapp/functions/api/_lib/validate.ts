import type { Context } from 'hono';
import type { z } from 'zod';
import { badRequest } from './errors';

/** Parses and validates a JSON request body, raising a 400 with field details. */
export async function parseJson<S extends z.ZodTypeAny>(c: Context, schema: S): Promise<z.infer<S>> {
	let raw: unknown;
	try {
		raw = await c.req.json();
	} catch {
		throw badRequest('Request body must be valid JSON.');
	}

	const result = schema.safeParse(raw);
	if (!result.success) throw badRequest('Invalid request body.', result.error.flatten().fieldErrors);
	return result.data;
}

/** Parses and validates the query string, raising a 400 with field details. */
export function parseQuery<S extends z.ZodTypeAny>(c: Context, schema: S): z.infer<S> {
	const result = schema.safeParse(c.req.query());
	if (!result.success) throw badRequest('Invalid query parameters.', result.error.flatten().fieldErrors);
	return result.data;
}
