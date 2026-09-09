package com.moneycounter.access

import java.util.Date

data class UserProfileData(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val access: AccessStatus?,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?
) {
    companion object {
        fun fromMap(uid: String, map: Map<String, Any?>): UserProfileData {
            return UserProfileData(
                uid = uid,
                email = map["email"] as? String,
                displayName = map["displayName"] as? String,
                photoUrl = map["photoUrl"] as? String,
                access = AccessStatus.fromStorage(map["access"] as? String),
                createdAtMillis = toMillis(map["createdAt"]),
                updatedAtMillis = toMillis(map["updatedAt"])
            )
        }

        private fun toMillis(value: Any?): Long? = when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Date -> value.time
            else -> null
        }
    }
}