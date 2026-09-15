package com.moneycounter.access

/** What to do with a session whose verification against the server failed (plan 034). */
enum class SessionOutcome {
    /** Keep the last known session and work offline. */
    USE_CACHE,

    /** The session is gone: sign out and send the user back to login. */
    SIGN_OUT,

    /** Nothing to fall back on: show the connection screen. */
    FAIL
}

/**
 * Plan 034, and the whole policy in one line: **a transport failure keeps the last known
 * session; a 401 destroys it.**
 *
 * This is plan 030's rule ([com.moneycounter.appwrite.isMemberRowMissing]: only a 404 means
 * "no membership") applied to identity instead of role. Both are security decisions, so both
 * live in pure functions with tests.
 *
 * Why an offline session is allowed to live indefinitely (owner decision, 2026-09-14): with no
 * network the user only sees data they already held, nothing refreshes, the screen says it is
 * disconnected, and their operations stay pending. There is nothing to gain by locking them
 * out of reading stale data. What makes that acceptable is [SIGN_OUT]: the moment the device
 * reaches the server and hears 401, the session dies.
 *
 * @param code HTTP status of the failed call, or null for a transport failure (no response).
 */
fun sessionOutcomeFor(code: Int?, hasCachedSession: Boolean): SessionOutcome = when {
    // Revocation wins over everything, cache or no cache. Account deleted, session deleted,
    // access removed: all answer 401, and all mean this session must not continue.
    code == 401 -> SessionOutcome.SIGN_OUT

    // No cache to fall back on — the honest answer is the connection screen, not a guess.
    !hasCachedSession -> SessionOutcome.FAIL

    // Everything else with a cache: work offline. A transport failure (null) is the offline
    // case. A 5xx, a 429 or a 403 mean the server is reachable but not answering usefully,
    // which is NOT a revocation: treating a rate limit or a row-permission error as "your
    // session is gone" would throw out legitimate users over a transient server problem.
    else -> SessionOutcome.USE_CACHE
}
