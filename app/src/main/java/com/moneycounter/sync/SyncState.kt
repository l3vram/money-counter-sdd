package com.moneycounter.sync

/**
 * Minimal representation of the synchronization state.
 *
 * The UI or other layers can observe this to know whether a sync is ongoing,
 * completed or has failed.
 */
sealed interface SyncState {
    /** No sync activity currently. */
    object Idle : SyncState

    /** A sync operation is in progress (push or pull). */
    object Syncing : SyncState

    /** Local data is known to be up‑to‑date with the cloud. */
    object UpToDate : SyncState

    /** An error occurred during sync. */
    data class Error(val throwable: Throwable) : SyncState
}