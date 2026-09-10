package com.moneycounter.firestore

import org.junit.Test
import org.junit.Assert.*

class FirestorePathsTest {

    @Test
    fun `user path`() {
        assertEquals("users/abc123", FirestorePaths.user("abc123"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `user blank uid throws`() {
        FirestorePaths.user("")
    }

    @Test
    fun `member path`() {
        assertEquals("members/uid42", FirestorePaths.member("uid42"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `member blank uid throws`() {
        FirestorePaths.member(" ")
    }

    @Test
    fun `organization path`() {
        assertEquals("organizations/orgX", FirestorePaths.organization("orgX"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `organization blank id throws`() {
        FirestorePaths.organization("")
    }

    @Test
    fun `branch path`() {
        assertEquals(
            "organizations/org1/branches/branchA",
            FirestorePaths.branch("org1", "branchA")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `branch blank org throws`() {
        FirestorePaths.branch("", "branchA")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `branch blank branch throws`() {
        FirestorePaths.branch("org1", " ")
    }

    @Test
    fun `stock path`() {
        assertEquals(
            "organizations/o/b/branches/b1/stock",
            FirestorePaths.stock("o", "b1")
        )
    }

    @Test
    fun `sales path`() {
        assertEquals(
            "organizations/o/branches/b1/sales",
            FirestorePaths.sales("o", "b1")
        )
    }
}