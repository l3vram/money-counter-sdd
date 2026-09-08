package com.moneycounter.access

import com.moneycounter.auth.AuthUser

interface AccessRepository {
    suspend fun getAccess(uid: String): AccessStatus?
    suspend fun ensureUserDocument(user: AuthUser): AccessStatus
}