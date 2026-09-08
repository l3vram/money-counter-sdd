package com.moneycounter.access

enum class AccessStatus {
    PENDING,
    APPROVED,
    BLOCKED;

    companion object {
        fun fromStorage(value: String?): AccessStatus? = when (value) {
            "PENDING" -> PENDING
            "APPROVED" -> APPROVED
            "BLOCKED" -> BLOCKED
            else -> null
        }

        fun toStorage(status: AccessStatus): String = status.name
    }
}