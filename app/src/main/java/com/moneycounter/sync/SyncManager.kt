package com.moneycounter.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Stub implementation that defines the extension points for future synchronization
 * with Firestore.
 *
 * The intended flow (future implementation):
 *   1. Read local JSON repositories.
 *   2. Push changes to Firestore via this manager.
 *   3. Pull remote changes from Firestore.
 *   4. Update local repositories accordingly.
 *
 * At the moment the manager does not depend on any Firebase classes, making it
 * unit‑testable and safe to include without configuring Firestore.
 */
class SyncManager(
    // In a full implementation a Firestore instance would be injected here.
    // Keeping it optional/null allows the stub to compile without Firebase.
    private val firestoreProvider: (() -> Any)? = null,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    /** Public read‑only view of the sync state. */
    val syncState: StateFlow<SyncState> = _state

    /**
     * Initiates a push of local data to the cloud.
     * Future implementation will serialize local JSON repositories and write them
     * to Firestore.
     */
    fun pushLocalToCloud() {
        externalScope.launch {
            _state.value = SyncState.Syncing
            try {
                // TODO: perform push using firestoreProvider()
                // Simulate immediate success for the stub.
                _state.value = SyncState.UpToDate
            } catch (e: Exception) {
                _state.value = SyncState.Error(e)
            }
        }
    }

    /**
     * Initiates a pull of remote data into the local storage.
     * Future implementation will read Firestore documents and update the local JSON
     * repositories.
     */
    fun pullCloudToLocal() {
        externalScope.launch {
            _state.value = SyncState.Syncing
            try {
                // TODO: perform pull using firestoreProvider()
                // Simulate immediate success for the stub.
                _state.value = SyncState.UpToDate
            } catch (e: Exception) {
                _state.value = SyncState.Error(e)
            }
        }
    }

    /**
     * Returns a StateFlow that can be observed to react to sync state changes.
     */
    fun observeSyncState(): StateFlow<SyncState> = syncState
}