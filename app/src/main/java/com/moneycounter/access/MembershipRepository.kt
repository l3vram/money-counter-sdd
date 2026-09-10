package com.moneycounter.access

import com.moneycounter.domain.Member
import kotlinx.coroutines.flow.Flow

interface MembershipRepository {
    fun observeMember(uid: String): Flow<Member?>
}