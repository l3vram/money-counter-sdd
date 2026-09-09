package com.moneycounter.access

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moneycounter.auth.AuthUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreAccessRepository(
    private val firestore: FirebaseFirestore
) : AccessRepository {

    private val usersCollection = firestore.collection("users")

    override suspend fun getAccess(uid: String): AccessStatus? {
        val doc = usersCollection.document(uid).get().await()
        if (!doc.exists()) return null
        val accessStr = doc.getString("access")
        return AccessStatus.fromStorage(accessStr)
    }

    override suspend fun ensureUserDocument(user: AuthUser): AccessStatus {
        val docRef = usersCollection.document(user.uid)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) {
            // Create new user document with default PENDING status
            val data = hashMapOf<String, Any?>(
                "email" to user.email,
                "displayName" to user.displayName,
                "photoUrl" to user.photoUrl,
                "access" to AccessStatus.PENDING.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(data).await()
            return AccessStatus.PENDING
        } else {
            // Document already exists; just return its access status
            val accessStr = snapshot.getString("access")
            return AccessStatus.fromStorage(accessStr) ?: AccessStatus.PENDING
        }
    }

    override suspend fun getUserProfile(uid: String): UserProfileData? {
        val doc = usersCollection.document(uid).get().await()
        if (!doc.exists()) return null
        return toProfile(uid, doc)
    }

    override fun observeUserProfile(uid: String): Flow<UserProfileData?> = callbackFlow {
        val docRef = usersCollection.document(uid)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                trySend(null)
            } else {
                trySend(toProfile(uid, snapshot))
            }
        }
        awaitClose { registration.remove() }
    }

    private fun toProfile(uid: String, doc: com.google.firebase.firestore.DocumentSnapshot): UserProfileData {
        val data = hashMapOf<String, Any?>(
            "email" to doc.getString("email"),
            "displayName" to doc.getString("displayName"),
            "photoUrl" to doc.getString("photoUrl"),
            "access" to doc.getString("access"),
            "createdAt" to doc.getTimestamp("createdAt")?.toDate()?.time,
            "updatedAt" to doc.getTimestamp("updatedAt")?.toDate()?.time
        )
        return UserProfileData.fromMap(uid, data)
    }
}