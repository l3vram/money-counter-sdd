package com.moneycounter.appwrite

/**
 * Connectivity probe against the Appwrite project.
 */
object AppwriteHealth {

    /**
     * Calls the Appwrite ping endpoint and returns the round-trip time in
     * milliseconds, or the failure.
     */
    suspend fun ping(): Result<Long> {
        val start = System.currentTimeMillis()
        return try {
            Appwrite.client.ping()
            Result.success(System.currentTimeMillis() - start)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}