package com.moneycounter.ui

import com.moneycounter.access.AccessRepository
import com.moneycounter.access.AccessStatus
import com.moneycounter.access.AppAccessState
import com.moneycounter.access.CachedSession
import com.moneycounter.access.MemberCacheRepository
import com.moneycounter.access.MembershipRepository
import com.moneycounter.access.MembershipUpdate
import com.moneycounter.access.SessionCacheRepository
import com.moneycounter.access.UserProfileData
import com.moneycounter.appwrite.BranchInfo
import com.moneycounter.appwrite.CloudOrgRepository
import com.moneycounter.appwrite.OrgInfo
import com.moneycounter.appwrite.TenantCloudFields
import com.moneycounter.auth.AuthRepository
import com.moneycounter.auth.AuthUser
import io.appwrite.exceptions.AppwriteException
import com.moneycounter.domain.Branch
import com.moneycounter.domain.Member
import com.moneycounter.domain.Organization
import com.moneycounter.domain.Role
import com.moneycounter.repository.TenantRepository
import com.moneycounter.signup.SignupRepository
import com.moneycounter.signup.SignupRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeAuthRepository(
    var user: AuthUser? = null,
    var signInResult: Result<AuthUser> = Result.success(AuthUser("uid1", "test@example.com"))
) : AuthRepository {
    var lastSignInPassword: String? = null
    var signInCalls = 0
    var signUpCalls = 0
    var lastSignUpPassword: String? = null
    var signUpResult: Result<AuthUser>? = null
    var changePasswordResult: Result<Unit> = Result.success(Unit)
    var changePasswordCalls = 0
    var lastChangeCurrentPassword: String? = null
    var lastChangeNewPassword: String? = null

    override suspend fun currentUser(): AuthUser? = user

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> {
        signInCalls++
        lastSignInPassword = password
        val res = signInResult
        if (res.isSuccess) {
            user = res.getOrNull()
        }
        return res
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser> {
        signUpCalls++
        lastSignUpPassword = password
        val res = signUpResult ?: signInResult
        if (res.isSuccess) {
            user = res.getOrNull()
        }
        return res
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        changePasswordCalls++
        lastChangeCurrentPassword = currentPassword
        lastChangeNewPassword = newPassword
        return changePasswordResult
    }

    override suspend fun signOut() {
        user = null
    }
}

class FakeSessionCacheRepository(
    var stored: CachedSession? = null
) : SessionCacheRepository {
    var saves = 0
    var clears = 0

    override fun load(): CachedSession? = stored

    override fun save(session: CachedSession) {
        saves++
        stored = session
    }

    override fun clear() {
        clears++
        stored = null
    }
}

class FakeMemberCacheRepositoryForOffline(
    var stored: Member? = null
) : MemberCacheRepository {
    override fun load(): Member? = stored
    override fun save(member: Member) { stored = member }
    override fun clear() { stored = null }
}

class FakeAccessRepository(
    var accessStatusToReturn: AccessStatus = AccessStatus.APPROVED,
    var shouldThrowError: Boolean = false,
    var profileFlow: Flow<UserProfileData?> = MutableStateFlow(null),
    /** Plan 034: para distinguir un fallo de transporte (null) de un 401. */
    var errorToThrow: Exception? = null
) : AccessRepository {
    private fun failure(): Exception =
        errorToThrow ?: RuntimeException("Network error")

    override suspend fun getAccess(uid: String): AccessStatus? {
        if (shouldThrowError) throw failure()
        return accessStatusToReturn
    }

    override suspend fun ensureUserDocument(user: AuthUser): AccessStatus {
        if (shouldThrowError) throw failure()
        return accessStatusToReturn
    }

    override suspend fun getUserProfile(uid: String): UserProfileData? = null

    override fun observeUserProfile(uid: String): Flow<UserProfileData?> = profileFlow
}

class FakeMembershipRepository(
    var memberFlow: Flow<Member?> = MutableStateFlow(null),
    /** Plan 034: para probar la revocación, que no se puede expresar como `Member?`. */
    var updateFlow: Flow<MembershipUpdate>? = null
) : MembershipRepository {
    // Los tests existentes se expresan en `Member?`; el fake traduce, así el cambio de tipo
    // del repositorio no obliga a reescribirlos.
    override fun observeMember(uid: String): Flow<MembershipUpdate> =
        updateFlow ?: memberFlow.map { member ->
            if (member != null) MembershipUpdate.Assigned(member) else MembershipUpdate.Missing
        }
}

class FakeSignupRepository(
    val requests: MutableList<SignupRequest> = mutableListOf(),
    val superuserNumber: String? = "+5300000000",
    var shouldFailSubmit: Boolean = false,
    var mustChangePassword: Boolean = false
) : SignupRepository {
    val flagUpdates: MutableList<Pair<String, Boolean>> = mutableListOf()

    override suspend fun submit(request: SignupRequest) {
        if (shouldFailSubmit) throw RuntimeException("submit failed")
        requests.add(request)
    }

    override suspend fun settingsSuperuserWhatsapp(): String? = superuserNumber

    override suspend fun setMustChangePassword(uid: String, flag: Boolean) {
        flagUpdates.add(uid to flag)
        if (!flag) mustChangePassword = false
    }

    override suspend fun readMustChangePassword(uid: String): Boolean = mustChangePassword
}

class FakeTenantRepository(
    var org: Organization? = null,
    var branches: MutableList<Branch> = mutableListOf()
) : TenantRepository {
    val seeds: MutableList<Pair<Organization, List<Branch>>> = mutableListOf()

    override fun loadOrganization(): Organization? = org

    override fun loadBranches(): List<Branch> = branches

    override fun saveOrganization(organization: Organization) {
        org = organization
    }

    override fun saveBranches(branches: List<Branch>) {
        this.branches = branches.toMutableList()
    }

    override fun seedFromCloud(org: Organization, branches: List<Branch>) {
        seeds.add(org to branches)
    }
}

class FakeCloudOrgRepository(
    var org: OrgInfo? = null,
    var branches: List<BranchInfo> = emptyList(),
    var failGetOrg: Boolean = false
) : CloudOrgRepository {
    var getOrgCalls = 0

    override suspend fun getOrg(orgId: String): OrgInfo? {
        getOrgCalls++
        if (failGetOrg) throw RuntimeException("network down")
        return org
    }

    override suspend fun getBranches(orgId: String): List<BranchInfo> = branches
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
        // El mensaje del SDK ya no llega crudo a la pantalla: pasa por mapAuthError, que es
        // lo que evita que el usuario vea texto en inglés.
        assertEquals("Sin conexión. Verifica tu internet.", errorState.message)
    }

    // ---- Plan 034: arrancar sin conexión ----

    private fun cachedSession(access: AccessStatus = AccessStatus.APPROVED) = CachedSession(
        uid = "uid123",
        email = "user@test.com",
        displayName = "Test User",
        photoUrl = null,
        access = access,
        savedAtMs = 1_000
    )

    /**
     * Sin conexión el poll de membresía **no contesta nada** — no emite `Missing`, que
     * significaría que el servidor respondió que no hay fila. Un fake que emita `Missing`
     * apagaría el flag de offline y no representa el caso.
     */
    private fun neverAnswers(): Flow<MembershipUpdate> = MutableSharedFlow()

    private fun cachedMember() = Member(
        uid = "uid123",
        orgId = "org1",
        role = Role.SELLER,
        branchIds = listOf("br1")
    )

    @Test
    fun checkAccess_success_savesTheSessionAndClearsTheOfflineFlag() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val sessionCache = FakeSessionCacheRepository()
        val viewModel = AuthViewModel(
            FakeAuthRepository(user = testUser),
            FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED),
            FakeMembershipRepository(),
            sessionCacheRepository = sessionCache
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.Approved)
        assertFalse(viewModel.isOffline.value)
        assertEquals(1, sessionCache.saves)
        assertEquals("uid123", sessionCache.stored?.uid)
        assertEquals(AccessStatus.APPROVED, sessionCache.stored?.access)
    }

    @Test
    fun checkAccess_offlineWithSessionAndMembershipCached_opensTheAppOffline() = runTest {
        // El caso que motiva el plan: un vendedor abre la app sin señal y tiene que poder
        // mirar lo que ya tenía, en vez de quedarse en la pantalla de error.
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val viewModel = AuthViewModel(
            FakeAuthRepository(user = testUser),
            FakeAccessRepository(shouldThrowError = true),
            FakeMembershipRepository(updateFlow = neverAnswers()),
            memberCacheRepository = FakeMemberCacheRepositoryForOffline(cachedMember()),
            sessionCacheRepository = FakeSessionCacheRepository(cachedSession())
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            "debería abrir con la sesión cacheada, no dar error",
            viewModel.uiState.value is AppAccessState.Approved
        )
        assertTrue("y avisar que está sin conexión", viewModel.isOffline.value)
    }

    @Test
    fun checkAccess_offlineWithSessionButNoMembership_showsTheConnectionScreen() = runTest {
        // Sin membresía cacheada no hay con qué decidir. Abrir reabriría el agujero del plan
        // 033, y quedarse en Loading dejaría la app colgada para siempre.
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val viewModel = AuthViewModel(
            FakeAuthRepository(user = testUser),
            FakeAccessRepository(shouldThrowError = true),
            FakeMembershipRepository(),
            memberCacheRepository = FakeMemberCacheRepositoryForOffline(null),
            sessionCacheRepository = FakeSessionCacheRepository(cachedSession())
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.Error)
        assertFalse(viewModel.isOffline.value)
    }

    @Test
    fun checkAccess_offlineWithAPendingSession_showsPendingWithoutNeedingAMembership() = runTest {
        // Una cuenta pendiente no opera igual, así que la membresía es irrelevante: mostrarle
        // "esperando aprobación" sin conexión es correcto y además útil.
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val viewModel = AuthViewModel(
            FakeAuthRepository(user = testUser),
            FakeAccessRepository(shouldThrowError = true),
            FakeMembershipRepository(updateFlow = neverAnswers()),
            memberCacheRepository = FakeMemberCacheRepositoryForOffline(null),
            sessionCacheRepository = FakeSessionCacheRepository(cachedSession(AccessStatus.PENDING))
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.Pending)
        assertTrue(viewModel.isOffline.value)
    }

    @Test
    fun checkAccess_offlineWithNothingCached_showsTheConnectionScreen() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val viewModel = AuthViewModel(
            FakeAuthRepository(user = testUser),
            FakeAccessRepository(shouldThrowError = true),
            FakeMembershipRepository(),
            sessionCacheRepository = FakeSessionCacheRepository(null)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.Error)
        assertFalse(viewModel.isOffline.value)
    }

    @Test
    fun checkAccess_a401SignsOutAndWipesBothCaches() = runTest {
        // Sesión revocada, cuenta borrada o acceso quitado. Es lo que hace aceptable que una
        // sesión offline no expire: la revocación surte efecto al recuperar la red.
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val sessionCache = FakeSessionCacheRepository(cachedSession())
        val memberCache = FakeMemberCacheRepositoryForOffline(cachedMember())
        val viewModel = AuthViewModel(
            FakeAuthRepository(user = testUser),
            FakeAccessRepository(
                shouldThrowError = true,
                errorToThrow = AppwriteException(message = "revocada", code = 401)
            ),
            FakeMembershipRepository(),
            memberCacheRepository = memberCache,
            sessionCacheRepository = sessionCache
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.SignedOut)
        assertNull("el caché de sesión debe quedar limpio", sessionCache.stored)
        assertNull("y el de membresía también", memberCache.stored)
    }

    @Test
    fun checkAccess_a403IsNotARevocation_soItWorksOffline() = runTest {
        // Solo el 401 mata la sesión. Un 403 es un permiso de fila y expulsar por eso sería
        // un bug muy difícil de diagnosticar desde el lado del usuario.
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val sessionCache = FakeSessionCacheRepository(cachedSession())
        val viewModel = AuthViewModel(
            FakeAuthRepository(user = testUser),
            FakeAccessRepository(
                shouldThrowError = true,
                errorToThrow = AppwriteException(message = "prohibido", code = 403)
            ),
            FakeMembershipRepository(updateFlow = neverAnswers()),
            memberCacheRepository = FakeMemberCacheRepositoryForOffline(cachedMember()),
            sessionCacheRepository = sessionCache
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.Approved)
        assertTrue(viewModel.isOffline.value)
        assertNotNull("no debe borrar nada", sessionCache.stored)
    }

    @Test
    fun observeMember_a401WhileRunning_signsOutAndWipesTheCaches() = runTest {
        // La contraparte de lo del arranque: la sesión puede morir con la app abierta. Antes
        // el 401 del poll se lo comía el catch y la app seguía andando con datos viejos.
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val updates = MutableSharedFlow<MembershipUpdate>(replay = 1)
        val sessionCache = FakeSessionCacheRepository(cachedSession())
        val memberCache = FakeMemberCacheRepositoryForOffline(cachedMember())
        val viewModel = AuthViewModel(
            FakeAuthRepository(user = testUser),
            FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED),
            FakeMembershipRepository(updateFlow = updates),
            memberCacheRepository = memberCache,
            sessionCacheRepository = sessionCache
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AppAccessState.Approved)

        updates.emit(MembershipUpdate.Revoked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.SignedOut)
        assertNull(sessionCache.stored)
        assertNull(memberCache.stored)
    }

    @Test
    fun observeMember_a404DoesNotSignOut_itIsADifferentQuestion() = runTest {
        // 404 es "no tenés membresía asignada" (plan 033), no "tu sesión murió". Confundirlas
        // expulsaría a alguien que sólo está esperando que el administrador lo asigne.
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val updates = MutableSharedFlow<MembershipUpdate>(replay = 1)
        val viewModel = AuthViewModel(
            FakeAuthRepository(user = testUser),
            FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED),
            FakeMembershipRepository(updateFlow = updates),
            sessionCacheRepository = FakeSessionCacheRepository()
        )
        testDispatcher.scheduler.advanceUntilIdle()

        updates.emit(MembershipUpdate.Missing)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(
            "un 404 no debe cerrar la sesión",
            viewModel.uiState.value is AppAccessState.SignedOut
        )
        assertTrue(viewModel.membershipResolved.value)
    }

    @Test
    fun observeMember_answering_clearsTheOfflineBanner() = runTest {
        // El poll ya late cada 10 s: una respuesta suya es la señal más barata de que la
        // conectividad volvió, así que el cartel se apaga solo sin un segundo temporizador.
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val updates = MutableSharedFlow<MembershipUpdate>(replay = 1)
        val viewModel = AuthViewModel(
            FakeAuthRepository(user = testUser),
            FakeAccessRepository(shouldThrowError = true),
            FakeMembershipRepository(updateFlow = updates),
            memberCacheRepository = FakeMemberCacheRepositoryForOffline(cachedMember()),
            sessionCacheRepository = FakeSessionCacheRepository(cachedSession())
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("arranca sin conexión", viewModel.isOffline.value)

        updates.emit(MembershipUpdate.Assigned(cachedMember()))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("el poll contestó: se apaga el cartel", viewModel.isOffline.value)
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

    @Test
    fun signUp_happyPath_submitsRequestAndPublishesSignUpPending() = runTest {
        val fakeAuth = FakeAuthRepository(user = AuthUser("uid9", "owner@x.com"))
        val fakeSignup = FakeSignupRepository()
        val viewModel = AuthViewModel(fakeAuth, FakeAccessRepository(), FakeMembershipRepository(), fakeSignup)

        viewModel.signUp("owner@x.com", Role.OWNER, "Mi Negocio", listOf("Centro"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AppAccessState.SignUpPending)
        val tempPassword = (state as AppAccessState.SignUpPending).tempPassword
        assertTrue(tempPassword.length >= 8)
        assertEquals(1, fakeSignup.requests.size)
        assertEquals("owner@x.com", fakeSignup.requests.first().email)
        assertEquals(Role.OWNER, fakeSignup.requests.first().role)
        assertEquals("Mi Negocio", fakeSignup.requests.first().businessName)
        assertEquals(listOf("Centro"), fakeSignup.requests.first().branches)
        assertEquals("PENDING", fakeSignup.requests.first().status)
        assertTrue(fakeSignup.requests.first().mustChangePassword)
        assertEquals(fakeSignup.superuserNumber, viewModel.superuserWhatsapp.value)
    }

    @Test
    fun signUp_usesGeneratedTempPasswordForAccountCreation() = runTest {
        val fakeAuth = FakeAuthRepository(user = AuthUser("uid9", "owner@x.com"))
        val fakeSignup = FakeSignupRepository()
        val viewModel = AuthViewModel(fakeAuth, FakeAccessRepository(), FakeMembershipRepository(), fakeSignup)

        viewModel.signUp("owner@x.com", Role.SELLER, null, emptyList())
        testDispatcher.scheduler.advanceUntilIdle()

        val tempPassword = (viewModel.uiState.value as AppAccessState.SignUpPending).tempPassword
        // Registration must create the account explicitly. It used to call signInWithEmail and
        // depend on its auto-registration side effect; when that was removed, signing up broke
        // with "invalid credentials" for an account that did not exist yet. These two
        // assertions are the regression guard.
        assertEquals(tempPassword, fakeAuth.lastSignUpPassword)
        assertEquals(1, fakeAuth.signUpCalls)
        assertEquals("signing up must not go through sign-in", 0, fakeAuth.signInCalls)
        assertEquals(emptyList<String>(), fakeSignup.requests.first().branches)
        assertNull(fakeSignup.requests.first().businessName)
    }

    @Test
    fun signUp_failure_setsSignUpErrorWithoutSubmitting() = runTest {
        val fakeAuth = FakeAuthRepository(
            user = null,
            signInResult = Result.failure(Exception("La cuenta ya existe"))
        )
        val fakeSignup = FakeSignupRepository()
        val viewModel = AuthViewModel(fakeAuth, FakeAccessRepository(), FakeMembershipRepository(), fakeSignup)

        viewModel.signUp("owner@x.com", Role.SELLER, null, emptyList())
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("La cuenta ya existe", viewModel.signUpError.value)
        assertEquals(0, fakeSignup.requests.size)
        assertFalse(viewModel.uiState.value is AppAccessState.SignUpPending)
    }

    @Test
    fun signUp_blankEmail_setsErrorWithoutAnyCalls() = runTest {
        val fakeAuth = FakeAuthRepository()
        val fakeSignup = FakeSignupRepository()
        val viewModel = AuthViewModel(fakeAuth, FakeAccessRepository(), FakeMembershipRepository(), fakeSignup)

        viewModel.signUp("   ", Role.SELLER, null, emptyList())
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.signUpError.value)
        assertEquals(0, fakeSignup.requests.size)
        assertNull(fakeAuth.lastSignInPassword)
    }

    @Test
    fun signUp_submitFailure_setsSignUpErrorWithoutPendingState() = runTest {
        val fakeAuth = FakeAuthRepository(user = AuthUser("uid9", "owner@x.com"))
        val fakeSignup = FakeSignupRepository(shouldFailSubmit = true)
        val viewModel = AuthViewModel(fakeAuth, FakeAccessRepository(), FakeMembershipRepository(), fakeSignup)

        viewModel.signUp("owner@x.com", Role.OWNER, "Mi Negocio", listOf("Centro"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.signUpError.value)
        assertFalse(viewModel.uiState.value is AppAccessState.SignUpPending)
    }

    @Test
    fun checkAccess_approvedWithMustChangePassword_returnsPasswordChangeRequired() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val fakeSignup = FakeSignupRepository(mustChangePassword = true)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository(), fakeSignup)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AppAccessState.PasswordChangeRequired)
        assertEquals(testUser, (state as AppAccessState.PasswordChangeRequired).user)
    }

    @Test
    fun checkAccess_approvedWithClearedFlag_returnsApproved() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val fakeSignup = FakeSignupRepository(mustChangePassword = false)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository(), fakeSignup)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.Approved)
    }

    @Test
    fun checkAccess_pendingIgnoresMustChangePasswordFlag() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.PENDING)
        val fakeSignup = FakeSignupRepository(mustChangePassword = true)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository(), fakeSignup)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.Pending)
    }

    @Test
    fun changePassword_success_clearsFlagAndTransitionsToApproved() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val fakeSignup = FakeSignupRepository(mustChangePassword = true)
        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository(), fakeSignup)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AppAccessState.PasswordChangeRequired)

        viewModel.changePassword("temporal123", "nuevaClave123", "nuevaClave123")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AppAccessState.Approved)
        assertEquals("temporal123", fakeAuth.lastChangeCurrentPassword)
        assertEquals("nuevaClave123", fakeAuth.lastChangeNewPassword)
        assertEquals(testUser.uid to false, fakeSignup.flagUpdates.last())
        assertFalse(viewModel.isChangingPassword.value)
        assertNull(viewModel.changePasswordError.value)
    }

    @Test
    fun changePassword_failure_setsErrorAndStaysOnChangeScreen() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        fakeAuth.changePasswordResult = Result.failure(Exception("Correo o contraseña incorrectos"))
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val fakeSignup = FakeSignupRepository(mustChangePassword = true)
        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository(), fakeSignup)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AppAccessState.PasswordChangeRequired)

        viewModel.changePassword("temporal123", "nuevaClave123", "nuevaClave123")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Correo o contraseña incorrectos", viewModel.changePasswordError.value)
        assertTrue(viewModel.uiState.value is AppAccessState.PasswordChangeRequired)
        assertTrue(fakeSignup.flagUpdates.isEmpty())
        assertFalse(viewModel.isChangingPassword.value)
    }

    @Test
    fun changePassword_usesTheRememberedLoginPassword_withoutAskingForIt() = runTest {
        // Nadie recuerda una contraseña generada minutos despues, asi que la pantalla ya no
        // la pide: el ViewModel guarda en memoria la que se uso para entrar y la manda el.
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = null)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val fakeSignup = FakeSignupRepository(mustChangePassword = true)
        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository(), fakeSignup)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("sin login previo no se conoce la actual", viewModel.knowsCurrentPassword.value)

        fakeAuth.signInResult = Result.success(testUser)
        viewModel.signInWithEmail("user@test.com", "temporal123")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("tras entrar, se conoce", viewModel.knowsCurrentPassword.value)
        assertTrue(viewModel.uiState.value is AppAccessState.PasswordChangeRequired)

        // La UI manda la actual vacia: la recordada es la que tiene que llegar al repositorio.
        viewModel.changePassword("", "miClaveNueva", "miClaveNueva")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("temporal123", fakeAuth.lastChangeCurrentPassword)
        assertEquals("miClaveNueva", fakeAuth.lastChangeNewPassword)
        assertTrue(viewModel.uiState.value is AppAccessState.Approved)
    }

    @Test
    fun changePassword_withoutARememberedPassword_asksForTheCurrentOne() = runTest {
        // App reabierta desde una sesion guardada: nunca se tipeo nada en esta corrida.
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val fakeSignup = FakeSignupRepository(mustChangePassword = true)
        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository(), fakeSignup)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.knowsCurrentPassword.value)

        viewModel.changePassword("", "miClaveNueva", "miClaveNueva")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "Escribe tu contraseña actual para poder cambiarla.",
            viewModel.changePasswordError.value
        )
        assertEquals(0, fakeAuth.changePasswordCalls)
        assertTrue(viewModel.uiState.value is AppAccessState.PasswordChangeRequired)
    }

    @Test
    fun signOut_forgetsTheRememberedPassword() = runTest {
        // Dispositivo compartido: el siguiente usuario no debe heredar nada.
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = null, signInResult = Result.success(testUser))
        val viewModel = AuthViewModel(
            fakeAuth,
            FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED),
            FakeMembershipRepository()
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.signInWithEmail("user@test.com", "temporal123")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.knowsCurrentPassword.value)

        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.knowsCurrentPassword.value)
    }

    @Test
    fun changePassword_mismatchConfirm_rejectedWithoutRepoCall() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val fakeSignup = FakeSignupRepository(mustChangePassword = true)
        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository(), fakeSignup)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AppAccessState.PasswordChangeRequired)

        viewModel.changePassword("temporal123", "nuevaClave123", "otraClave123")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Las contraseñas no coinciden.", viewModel.changePasswordError.value)
        assertEquals(0, fakeAuth.changePasswordCalls)
        assertTrue(fakeSignup.flagUpdates.isEmpty())
        assertTrue(viewModel.uiState.value is AppAccessState.PasswordChangeRequired)
    }

    @Test
    fun changePassword_newEqualsCurrent_rejectedWithoutRepoCall() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val fakeSignup = FakeSignupRepository(mustChangePassword = true)
        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository(), fakeSignup)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AppAccessState.PasswordChangeRequired)

        viewModel.changePassword("temporal123", "temporal123", "temporal123")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("La nueva contraseña debe ser diferente de la actual.", viewModel.changePasswordError.value)
        assertEquals(0, fakeAuth.changePasswordCalls)
        assertTrue(fakeSignup.flagUpdates.isEmpty())
        assertTrue(viewModel.uiState.value is AppAccessState.PasswordChangeRequired)
    }

    @Test
    fun changePassword_tooShort_rejectedWithoutRepoCall() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Test User")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val fakeSignup = FakeSignupRepository(mustChangePassword = true)
        val viewModel = AuthViewModel(fakeAuth, fakeAccess, FakeMembershipRepository(), fakeSignup)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AppAccessState.PasswordChangeRequired)

        viewModel.changePassword("temporal123", "abc", "abc")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.changePasswordError.value)
        assertEquals(0, fakeAuth.changePasswordCalls)
        assertTrue(fakeSignup.flagUpdates.isEmpty())
        assertTrue(viewModel.uiState.value is AppAccessState.PasswordChangeRequired)
    }

    @Test
    fun approvedMember_seedsCloudOrgWhenLocalConfigMissingIt() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Pedro")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val members = MutableStateFlow<Member?>(
            Member("uid123", "org-cloud", Role.OWNER, listOf("branch-cloud"))
        )
        val fakeMembership = FakeMembershipRepository(memberFlow = members)
        val fakeTenant = FakeTenantRepository()
        val now = 1750000000000L
        val fakeCloud = FakeCloudOrgRepository(
            org = OrgInfo("org-cloud", "Mi Tienda", "+53 555", TenantCloudFields.STATUS_ACTIVE, now),
            branches = listOf(
                BranchInfo("branch-cloud", "org-cloud", "Principal", TenantCloudFields.STATUS_ACTIVE, now)
            )
        )

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, fakeMembership, tenantRepository = fakeTenant, cloudOrgRepository = fakeCloud)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeTenant.seeds.size)
        val (seededOrg, seededBranches) = fakeTenant.seeds.first()
        assertEquals("org-cloud", seededOrg.id)
        assertEquals("Mi Tienda", seededOrg.name)
        assertEquals("uid123", seededOrg.ownerUid)
        assertEquals("+53 555", seededOrg.whatsappNumber)
        assertEquals(now, seededOrg.createdAt)
        assertEquals(listOf("branch-cloud"), seededBranches.map { it.id })
        assertEquals("org-cloud", seededBranches.first().orgId)
    }

    @Test
    fun noMemberDoc_doesNotSeedCloudOrg() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Pedro")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val fakeTenant = FakeTenantRepository()
        val fakeCloud = FakeCloudOrgRepository(
            org = OrgInfo("org-cloud", "Mi Tienda", null, TenantCloudFields.STATUS_ACTIVE, 1L)
        )

        val viewModel = AuthViewModel(
            fakeAuth, fakeAccess, FakeMembershipRepository(),
            tenantRepository = fakeTenant, cloudOrgRepository = fakeCloud
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeTenant.seeds.size)
        assertNull(fakeTenant.org)
        assertEquals(0, fakeCloud.getOrgCalls)
    }

    @Test
    fun alreadySeededOrg_doesNotSeedAgain() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Pedro")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val members = MutableStateFlow<Member?>(
            Member("uid123", "org-cloud", Role.SELLER, listOf("branch-cloud"))
        )
        val fakeMembership = FakeMembershipRepository(memberFlow = members)
        val fakeTenant = FakeTenantRepository(
            org = Organization("org-cloud", "Mi Tienda", "uid123", createdAt = 1L)
        )
        val fakeCloud = FakeCloudOrgRepository(
            org = OrgInfo("org-cloud", "Mi Tienda", null, TenantCloudFields.STATUS_ACTIVE, 1L)
        )

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, fakeMembership, tenantRepository = fakeTenant, cloudOrgRepository = fakeCloud)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeTenant.seeds.size)
        assertEquals(0, fakeCloud.getOrgCalls)
        assertEquals("org-cloud", fakeTenant.org?.id)
    }

    @Test
    fun suspendedOrMissingCloudOrg_doesNotSeed() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Pedro")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val members = MutableStateFlow<Member?>(
            Member("uid123", "org-cloud", Role.SELLER, listOf("branch-cloud"))
        )
        val fakeMembership = FakeMembershipRepository(memberFlow = members)
        val fakeTenant = FakeTenantRepository()
        val fakeCloud = FakeCloudOrgRepository(org = null)

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, fakeMembership, tenantRepository = fakeTenant, cloudOrgRepository = fakeCloud)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeCloud.getOrgCalls)
        assertEquals(0, fakeTenant.seeds.size)
        assertNull(fakeTenant.org)
    }

    @Test
    fun cloudReadFailure_leavesLocalUntouchedAndRetriesOnNextPoll() = runTest {
        val testUser = AuthUser("uid123", "user@test.com", "Pedro")
        val fakeAuth = FakeAuthRepository(user = testUser)
        val fakeAccess = FakeAccessRepository(accessStatusToReturn = AccessStatus.APPROVED)
        val members = MutableStateFlow<Member?>(
            Member("uid123", "org-cloud", Role.OWNER, listOf("branch-cloud"))
        )
        val fakeMembership = FakeMembershipRepository(memberFlow = members)
        val fakeTenant = FakeTenantRepository()
        val fakeCloud = FakeCloudOrgRepository(
            org = OrgInfo("org-cloud", "Mi Tienda", null, TenantCloudFields.STATUS_ACTIVE, 1L),
            failGetOrg = true
        )

        val viewModel = AuthViewModel(fakeAuth, fakeAccess, fakeMembership, tenantRepository = fakeTenant, cloudOrgRepository = fakeCloud)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeTenant.seeds.size)
        assertNull(fakeTenant.org)

        fakeCloud.failGetOrg = false
        members.value = null
        testDispatcher.scheduler.advanceUntilIdle()
        members.value = Member("uid123", "org-cloud", Role.OWNER, listOf("branch-cloud"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeTenant.seeds.size)
        assertEquals("org-cloud", fakeTenant.seeds.first().first.id)
    }
}