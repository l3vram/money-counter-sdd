package com.moneycounter.access

import com.google.firebase.firestore.FirebaseFirestore
import com.moneycounter.domain.Member
import com.moneycounter.firestore.FirestorePaths
import com.moneycounter.firestore.memberFromMap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreMembershipRepository(
    private val firestore: FirebaseFirestore
) : MembershipRepository {

    override fun observeMember(uid: String): Flow<Member?> = callbackFlow {
        val docRef = firestore.document(FirestorePaths.member(uid))
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                trySend(null)
            } else {
                trySend(memberFromMap(uid, snapshot.data ?: emptyMap()))
            }
        }
        awaitClose { registration.remove() }
    }
}