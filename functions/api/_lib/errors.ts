import type { ContentfulStatusCode } from 'hono/utils/http-status';

/** An error carrying the HTTP status the API should answer with. */
export class HttpError extends Error {
	constructor(
		readonly status: ContentfulStatusCode,
		message: string,
		readonly details?: unknown,
	) {
		super(message);
		this.name = 'HttpError';
	}
}

export const badRequest = (message: string, details?: unknown) => new HttpError(400, message, details);
export const unauthorized = (message = 'Authentication required.') => new HttpError(401, message);
export const forbidden = (message = 'Not permitted.') => new HttpError(403, message);
export const notFound = (message = 'Not found.') => new HttpError(404, message);
export const conflict = (message: string) => new HttpError(409, message);
export const tooManyRequests = (message: string) => new HttpError(429, message);
