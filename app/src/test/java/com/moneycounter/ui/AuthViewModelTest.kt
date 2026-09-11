package com.moneycounter.ui

import com.moneycounter.access.AccessRepository
import com.moneycounter.access.AccessStatus
import com.moneycounter.access.AppAccessState
import com.moneycounter.access.MembershipRepository
import com.moneycounter.access.UserProfileData
import com.moneycounter.auth.AuthRepository
import com.moneycounter.auth.AuthUser
import com.moneycounter.domain.Member
import com.moneycounter.domain.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeAuthRepository(
    var user: AuthUser? = null,
    var signInResult: Result<AuthUser> = Result.success(AuthUser("uid1", "test@example.com"))
) : AuthRepository {
    override suspend fun currentUser(): AuthUser? = user

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> {
        val res = signInResult
        if (res.isSuccess) {
            user = res.getOrNull()
        }
        return res
    }

    override suspend fun signOut() {
        user = null
    }
}

class FakeAccessRepository(
    var accessStatusToReturn: AccessStatus = AccessStatus.APPROVED,
    var shouldThrowError: Boolean = false,
    var profileFlow: Flow<UserProfileData?> = MutableStateFlow(null)
) : AccessRepository {
    override suspend fun getAccess(uid: String): AccessStatus? {
        if (shouldThrowError) throw RuntimeException("Network error")
        return accessStatusToReturn
    }

    override suspend fun ensureUserDocument(user: AuthUser): AccessStatus {
        if (shouldThrowError) throw RuntimeException("Network error")
        return accessStatusToReturn
    }

    override suspend fun getUserProfile(uid: String): UserProfileData? = null

    override fun observeUserProfile(uid: String): Flow<UserProfileData?> = profileFlow
}

class FakeMembershipRepository(
    var memberFlow: Flow<Member?> = MutableStateFlow(null)
) : MembershipRepository {
    override fun observeMember(uid: String): Flow<Member?> = memberFlow
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
        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository())

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppAccessState.SignedOut, viewModel.uiState.value)
    }

    @Test
    fun checkAccess_whenUserApproved_returnsApprovedState() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository())
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

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.Pending)
    }

    @Test
    fun checkAccess_whenUserBlocked_returnsBlockedState() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.BLOCKED)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.Blocked)
    }

    @Test
    fun checkAccess_whenNetworkError_returnsErrorState() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(shouldThrowError = true)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository())
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

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppAccessState.SignedOut, viewModel.uiState.value)
    }

    @Test
    fun loadProfile_publishesProfilesFromObservedFlow() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Juana")
        val profiles = MutableStateFlow<UserProfileData?>(
            UserProfileData("uid123", "user@test.com", "Juana", null, AccessStatus.APPROVED, null, null)
        )
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(profileFlow = profiles)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository())
        viewModel.loadProfile()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Juana", viewModel.profile.value?.displayName)

        profiles.value = UserProfileData("uid123", "user@test.com", "Juana 2", null, AccessStatus.PENDING, null, null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Juana 2", viewModel.profile.value?.displayName)
        assertEquals(AccessStatus.PENDING, viewModel.profile.value?.access)
    }

    @Test
    fun loadProfile_withoutUser_keepsProfileNull() = runTest {
        val fakeAuth = FakeAuthRepository(user = null)
        val fakeAccess = FakeAccessRepository()

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository())
        viewModel.loadProfile()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.profile.value)
        assertEquals(AppAccessState.SignedOut, viewModel.uiState.value)
    }

@Test
    fun signOut_clearsProfileAndSetsSignedOut() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Juana")
        val profiles = MutableStateFlow<UserProfileData?>(
            UserProfileData("uid123", "user@test.com", "Juana", null, AccessStatus.APPROVED, null, null)
        )
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED, profileFlow = profiles)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository())
        viewModel.loadProfile()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Juana", viewModel.profile.value?.displayName)

        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.profile.value)
        assertEquals(AppAccessState.SignedOut, viewModel.uiState.value)
    }

    @Test
    fun checkAccess_observesSellerMember() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Pedro")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val members = MutableStateFlow<Member?>(
            Member("uid123", "org1", Role.SELLER, listOf("branch1"))
        )
        val fakeMembership = FakeMembershipRepository(memberFlow = members)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, fakeMembership)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Role.SELLER, viewModel.member.value?.role)
        assertEquals("org1", viewModel.member.value?.orgId)
    }

    @Test
    fun checkAccess_observesOwnerMember() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Dueno")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val members = MutableStateFlow<Member?>(
            Member("uid123", "org1", Role.OWNER, listOf("branch1"))
        )
        val fakeMembership = FakeMembershipRepository(memberFlow = members)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, fakeMembership)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Role.OWNER, viewModel.member.value?.role)
    }

    @Test
    fun checkAccess_keepsMemberNullWhenNoMemberDoc() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Pedro")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.member.value)
        assertTrue(viewModel.uiState.value is AppAccessState.Approved)
    }

    @Test
    fun checkAccess_noUser_clearsMember() = runTest {
        val fakeAuth = FakeAuthRepository(user = null)
        val members = MutableStateFlow<Member?>(
            Member("uid1", "org1", Role.SELLER)
        )
        val fakeMembership = FakeMembershipRepository(memberFlow = members)
        val viewModel = AuthViewModel(fakeAuth, FakeAccessRepository(), fakeMembership)

        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.member.value)
        assertEquals(AppAccessState.SignedOut, viewModel.uiState.value)
    }
}