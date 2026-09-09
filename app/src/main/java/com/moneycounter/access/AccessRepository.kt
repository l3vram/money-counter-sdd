package com.moneycounter.access

import com.moneycounter.auth.AuthUser
import kotlinx.coroutines.flow.Flow

interface AccessRepository {
    suspend fun getAccess(uid: String): AccessStatus?
    suspend fun ensureUserDocument(user: AuthUser): AccessStatus
    suspend fun getUserProfile(uid: String): UserProfileData?
    fun observeUserProfile(uid: String): Flow<UserProfileData?>
}