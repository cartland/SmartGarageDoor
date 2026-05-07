package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeDiagnosticsCountersRepository
import com.chriscartland.garage.testcommon.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAuthViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepo: FakeAuthRepository
    private lateinit var logger: FakeAppLoggerRepository
    private lateinit var viewModel: DefaultAuthViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authRepo = FakeAuthRepository()
        logger = FakeAppLoggerRepository()
        viewModel = DefaultAuthViewModel(
            observeAuthState = ObserveAuthStateUseCase(authRepo),
            signInWithGoogleUseCase = SignInWithGoogleUseCase(authRepo),
            signOutUseCase = SignOutUseCase(authRepo),
            logAppEvent = LogAppEventUseCase(logger, FakeDiagnosticsCountersRepository()),
            dispatchers = TestDispatcherProvider(testDispatcher),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialAuthStateIsUnknown() {
        assertIs<AuthState.Unknown>(viewModel.authState.value)
    }

    /**
     * ADR-022: VM exposes the repository's StateFlow by reference — no mirror.
     * An `assertSame` guard catches any future refactor that reintroduces a
     * local `MutableStateFlow` in the VM.
     */
    @Test
    fun authStateIsSameInstanceAsRepoStateFlow() {
        assertSame(
            authRepo.authState,
            viewModel.authState,
            "VM must expose repo's StateFlow by reference (ADR-022)",
        )
    }

    @Test
    fun signInWithGoogleUpdatesAuthState() =
        runTest(testDispatcher) {
            val user = User(
                name = DisplayName("Alice"),
                email = Email("alice@test.com"),
                idToken = FirebaseIdToken("token", exp = 9999999999L),
            )
            authRepo.setSignInResult(AuthState.Authenticated(user))

            viewModel.signInWithGoogle(GoogleIdToken("test-token"))
            advanceUntilIdle()

            assertIs<AuthState.Authenticated>(viewModel.authState.value)
            assertEquals(
                "Alice",
                (viewModel.authState.value as AuthState.Authenticated).user.name.asString(),
            )
        }

    @Test
    fun signInLogsEvent() =
        runTest(testDispatcher) {
            viewModel.signInWithGoogle(GoogleIdToken("token"))
            advanceUntilIdle()

            assertTrue(logger.loggedKeys.isNotEmpty())
        }

    @Test
    fun signOutUpdatesAuthState() =
        runTest(testDispatcher) {
            // First sign in
            authRepo.setSignInResult(
                AuthState.Authenticated(
                    user = User(
                        name = DisplayName("Test"),
                        email = Email("test@test.com"),
                        idToken = FirebaseIdToken("token", exp = 9999999999L),
                    ),
                ),
            )
            viewModel.signInWithGoogle(GoogleIdToken("token"))
            advanceUntilIdle()
            assertIs<AuthState.Authenticated>(viewModel.authState.value)

            // Then sign out
            viewModel.signOut()
            advanceUntilIdle()
            assertIs<AuthState.Unauthenticated>(viewModel.authState.value)
        }

    /**
     * Verifies that direct repository state changes (not via ViewModel methods)
     * propagate to the ViewModel's authState StateFlow.
     *
     * Previously the ViewModel used stateIn(Eagerly) which had subtle timing
     * issues causing the Compose UI to not reflect auth state changes.
     * This test ensures the explicit MutableStateFlow + collect pattern
     * (matching DoorViewModel) works correctly.
     */
    @Test
    fun authState_reflectsRepositoryChanges() =
        runTest(testDispatcher) {
            // Initial state is Unknown.
            advanceUntilIdle()
            assertIs<AuthState.Unknown>(viewModel.authState.value)

            // Repository emits Authenticated (simulating Firebase auth update).
            val user = User(
                name = DisplayName("Alice"),
                email = Email("alice@test.com"),
                idToken = FirebaseIdToken("token", exp = 9999999999L),
            )
            authRepo.setAuthState(AuthState.Authenticated(user))
            advanceUntilIdle()
            assertIs<AuthState.Authenticated>(viewModel.authState.value)

            // Repository emits Unauthenticated.
            authRepo.setAuthState(AuthState.Unauthenticated)
            advanceUntilIdle()
            assertIs<AuthState.Unauthenticated>(viewModel.authState.value)
        }
}
