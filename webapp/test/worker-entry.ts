import app from '../functions/api/_lib/app';

/**
 * Test-only entry point.
 *
 * In production `functions/api/[[route]].ts` mounts this same app through the
 * Pages adapter. The adapter passes the request through untouched, so exposing
 * the app as a plain Worker here exercises identical routing without needing
 * the Pages runtime in tests.
 */
export default app;
