import { handle } from 'hono/cloudflare-pages';
import app from './_lib/app';

/**
 * Every request under `/api/` lands here.
 *
 * A single catch-all keeps routing in one place: Hono matches the path rather
 * than the filesystem, so adding an endpoint means adding a route, not a file.
 * Sibling modules live under `_lib/` — the underscore keeps Pages from
 * publishing them as routes of their own.
 */
export const onRequest = handle(app);
