package com.moneycounter.ui

import android.app.Activity
import com.moneycounter.access.AccessRepository
import com.moneycounter.access.AccessStatus
import com.moneycounter.access.AppAccessState
import com.moneycounter.auth.AuthRepository
import com.moneycounter.auth.AuthUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeAuthRepository(
    var user: AuthUser? = null,
    var signInResult: Result<AuthUser> = Result.success(AuthUser("uid1", "test@example.com"))
) : AuthRepository {
    override fun currentUser(): AuthUser? = user

    override suspend fun signInWithGoogle(activity: Activity): Result<AuthUser> {
        val res = signInResult
        if (res.isSuccess) {
            user = res.getOrNull()
        }
        return res
    }

    override fun signOut() {
        user = null
    }
}

class FakeAccessRepository(
    var accessStatusToReturn: AccessStatus = AccessStatus.APPROVED,
    var shouldThrowError: Boolean = false
) : AccessRepository {
    override suspend fun getAccess(uid: String): AccessStatus? {
        if (shouldThrowError) throw RuntimeException("Network error")
        return accessStatusToReturn
    }

    override suspend fun ensureUserDocument(user: AuthUser): AccessStatus {
        if (shouldThrowError) throw RuntimeException("Network error")
        return accessStatusToReturn
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun checkAccess_whenNoUser_returnsSignedOut() = runTest {
        val fakeAuth = FakeAuthRepository(user = null)
        val fakeAccess = FakeAccessRepository()
        val viewModel = AuthViewModel(fakeAuth, fakeAccess)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppAccessState.SignedOut, viewModel.uiState.value)
    }

    @Test
    fun checkAccess_whenUserApproved_returnsApprovedState() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.Approved)
        val approvedState = viewModel.uiState.value as AppAccessState.Approved
        assertEquals(testUser, approvedState.user)
    }

    @Test
    fun checkAccess_whenUserPending_returnsPendingState() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.PENDING)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.Pending)
    }

    @Test
    fun checkAccess_whenUserBlocked_returnsBlockedState() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.BLOCKED)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.Blocked)
    }

    @Test
    fun checkAccess_whenNetworkError_returnsErrorState() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(shouldThrowError = true)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.Error)
        val errorState = viewModel.uiState.value as AppAccessState.Error
        assertEquals("Network error", errorState.message)
    }

    @Test
    fun signOut_setsStateToSignedOut() = runTest {
        val testUser = AuthUser("uid123", "user@test.com")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppAccessState.SignedOut, viewModel.uiState.value)
    }
}