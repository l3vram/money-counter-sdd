package com.moneycounter.signup

import com.moneycounter.domain.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignupRepositoryTest {

    private val request = SignupRequest(
        uid = "uid123",
        email = "owner@negocio.com",
        role = Role.OWNER,
        businessName = "Mi Negocio",
        branches = listOf("Centro", "Plaza"),
        mustChangePassword = true,
        status = "PENDING",
        createdAtMs = 1_620_000_000_000L
    )

    @Test
    fun payload_containsAllSignupFields() {
        val payload = signupRequestToPayload(request)

        assertEquals("owner@negocio.com", payload[SignupFields.EMAIL])
        assertEquals("OWNER", payload[SignupFields.ROLE])
        assertEquals("Mi Negocio", payload[SignupFields.BUSINESS_NAME])
        assertEquals(listOf("Centro", "Plaza"), payload[SignupFields.BRANCHES])
        assertEquals(true, payload[SignupFields.MUST_CHANGE_PASSWORD])
        assertEquals("PENDING", payload[SignupFields.STATUS])
        assertEquals(1_620_000_000_000L, payload[SignupFields.CREATED_AT])
    }

    @Test
    fun payload_serializesRoleToStorageString() {
        val payload = signupRequestToPayload(request.copy(role = Role.ADMIN))
        assertEquals("ADMIN", payload[SignupFields.ROLE])
    }

    @Test
    fun payload_adminHasNoBusinessFields() {
        val payload = signupRequestToPayload(request.copy(role = Role.ADMIN, businessName = null, branches = emptyList()))

        assertEquals("ADMIN", payload[SignupFields.ROLE])
        assertNull(payload[SignupFields.BUSINESS_NAME])
        assertEquals(emptyList<String>(), payload[SignupFields.BRANCHES])
    }

    @Test
    fun rowToRequest_roundTripsPayload() {
        val payload = signupRequestToPayload(request)

        assertEquals(request, signupRowToRequest("uid123", payload))
    }

    @Test
    fun rowToRequest_defaultsMissingOptionalFields() {
        val row = mapOf(
            SignupFields.EMAIL to "a@b.com",
            SignupFields.ROLE to "SELLER"
        )

        val parsed = signupRowToRequest("uidX", row)

        assertEquals("PENDING", parsed?.status)
        assertEquals(true, parsed?.mustChangePassword)
        assertEquals(emptyList<String>(), parsed?.branches)
    }

    @Test
    fun rowToRequest_returnsNullOnBlankUidOrMissingRequiredFields() {
        assertNull(signupRowToRequest("", mapOf(SignupFields.EMAIL to "a@b.com")))
        assertNull(signupRowToRequest("uid", emptyMap()))
        assertNull(signupRowToRequest("uid", mapOf(SignupFields.EMAIL to "a@b.com", SignupFields.ROLE to "unknown")))
    }
}