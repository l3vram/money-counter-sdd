package com.moneycounter.auth

import org.junit.Assert.*
import org.junit.Test

/**
 * Very lightweight test that ensures the enum values exist and are ordered
 * as defined – this is mostly a sanity check for the project template.
 */
class AuthStateTest {

    @Test
    fun `AuthState values are as expected`() {
        val expected = listOf(
            AuthState.LOADING,
            AuthState.SIGNED_OUT,
            AuthState.SIGNED_IN,
            AuthState.ERROR
        )
        assertEquals(expected, AuthState.values().toList())
    }
}