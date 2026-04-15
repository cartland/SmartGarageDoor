package com.chriscartland.garage.usecase

import com.chriscartland.garage.domain.model.AuthState
import com.chriscartland.garage.domain.model.DisplayName
import com.chriscartland.garage.domain.model.Email
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.GoogleIdToken
import com.chriscartland.garage.domain.model.User
import com.chriscartland.garage.testcommon.FakeAppLoggerRepository
import com.chriscartland.garage.testcommon.FakeAuthRepository
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
            logAppEvent = LogAppEventUseCase(logger),
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

    @Test
    fun signInWithGoogleUpdatesAuthState() =
        runTest(testDispatcher) {
            val user = User(
                name = DisplayName("Alice"),
                email = Email("alice@test.com"),
                idToken = FirebaseIdToken("token", exp = 9999999999L),
            )
            authRepo.signInResult = AuthState.Authenticated(user)

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
            authRepo.signInResult = AuthState.Authenticated(
                user = User(
                    name = DisplayName("Test"),
                    email = Email("test@test.com"),
                    idToken = FirebaseIdToken("token", exp = 9999999999L),
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
}
