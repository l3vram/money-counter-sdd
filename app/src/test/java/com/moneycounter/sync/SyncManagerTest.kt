package com.moneycounter.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncManagerTest {

    @Test
    fun `initial state is Idle`() = runBlocking {
        val manager = SyncManager()
        assertTrue(manager.observeSyncState().value is SyncState.Idle)
    }
}