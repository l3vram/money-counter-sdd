package com.moneycounter.firestore

import com.moneycounter.domain.Organization
import com.moneycounter.domain.Branch
import com.moneycounter.domain.Member
import com.moneycounter.domain.Role

/**
 * Field key constants used for Firestore storage.
 */
object FirestoreFields {
    const val FIELD_NAME = "name"
    const val FIELD_OWNER_UID = "ownerUid"
    const val FIELD_WHATSAPP = "whatsappNumber"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_ORG_ID = "orgId"
    const val FIELD_ROLE = "role"
    const val FIELD_BRANCH_IDS = "branchIds"
}

/** ---------- Organization ---------- */
fun Organization.toMap(): Map<String, Any?> = mapOf(
    FirestoreFields.FIELD_NAME to name,
    FirestoreFields.FIELD_OWNER_UID to ownerUid,
    FirestoreFields.FIELD_WHATSAPP to whatsappNumber,
    FirestoreFields.FIELD_CREATED_AT to createdAt
)

fun organizationFromMap(id: String, map: Map<String, Any?>): Organization? {
    if (id.isBlank()) return null
    val name = map[FirestoreFields.FIELD_NAME] as? String ?: return null
    if (name.isBlank()) return null
    val ownerUid = map[FirestoreFields.FIELD_OWNER_UID] as? String ?: return null
    if (ownerUid.isBlank()) return null
    val createdAt = when (val v = map[FirestoreFields.FIELD_CREATED_AT]) {
        is Number -> v.toLong()
        is Long -> v
        else -> return null
    }
    val whatsapp = map[FirestoreFields.FIELD_WHATSAPP] as? String
    return Organization(id, name, ownerUid, whatsapp, createdAt)
}

/** ---------- Branch ---------- */
fun Branch.toMap(): Map<String, Any?> = mapOf(
    FirestoreFields.FIELD_ORG_ID to orgId,
    FirestoreFields.FIELD_NAME to name
)

fun branchFromMap(id: String, map: Map<String, Any?>): Branch? {
    if (id.isBlank()) return null
    val orgId = map[FirestoreFields.FIELD_ORG_ID] as? String ?: return null
    if (orgId.isBlank()) return null
    val name = map[FirestoreFields.FIELD_NAME] as? String ?: return null
    if (name.isBlank()) return null
    return Branch(id, orgId, name)
}

/** ---------- Member ---------- */
fun Member.toMap(): Map<String, Any?> = mapOf(
    FirestoreFields.FIELD_ORG_ID to orgId,
    FirestoreFields.FIELD_ROLE to Role.toStorage(role),
    FirestoreFields.FIELD_BRANCH_IDS to branchIds
)

fun memberFromMap(uid: String, map: Map<String, Any?>): Member? {
    if (uid.isBlank()) return null
    val orgId = map[FirestoreFields.FIELD_ORG_ID] as? String ?: return null
    if (orgId.isBlank()) return null
    val roleStr = map[FirestoreFields.FIELD_ROLE] as? String ?: return null
    val role = Role.fromStorage(roleStr) ?: return null
    val branchIdsRaw = map[FirestoreFields.FIELD_BRANCH_IDS]
    val branchIds = when (branchIdsRaw) {
        is List<*> -> branchIdsRaw.mapNotNull { it as? String }
        else -> emptyList()
    }
    return Member(uid, orgId, role, branchIds)
}