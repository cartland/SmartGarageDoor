package com.chriscartland.garage.data.repository

import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.ActionError
import com.chriscartland.garage.domain.model.AppResult
import com.chriscartland.garage.domain.model.FirebaseIdToken
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.testcommon.FakeAuthRepository
import com.chriscartland.garage.testcommon.FakeNetworkButtonDataSource
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests use real [CachedServerConfigRepository] with [FakeNetworkConfigDataSource],
 * exercising the actual caching and null-handling logic in the repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteButtonRepositoryTest {
    private lateinit var networkButtonDataSource: FakeNetworkButtonDataSource
    private lateinit var networkConfigDataSource: FakeNetworkConfigDataSource
    private lateinit var externalScope: CoroutineScope

    private fun buildRepo(enabled: Boolean = true): NetworkRemoteButtonRepository {
        val authRepo = FakeAuthRepository().apply {
            setIdTokenResult(FirebaseIdToken(idToken = "token", exp = Long.MAX_VALUE))
        }
        return NetworkRemoteButtonRepository(
            networkButtonDataSource,
            CachedServerConfigRepository(networkConfigDataSource, "test-key", externalScope),
            authRepository = authRepo,
            remoteButtonPushEnabled = enabled,
        )
    }

    @BeforeTest
    fun setup() {
        networkButtonDataSource = FakeNetworkButtonDataSource()
        networkConfigDataSource = FakeNetworkConfigDataSource()
        externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        externalScope.cancel()
    }

    @Test
    fun pushFailsWhenServerConfigFetchFails() =
        runTest {
            networkConfigDataSource.setServerConfigResult(NetworkResult.ConnectionFailed)
            val repo = buildRepo()
            val result = repo.pushButton("ack-token")
            assertEquals(AppResult.Error(ActionError.NetworkFailed), result)
            assertEquals(0, networkButtonDataSource.pushCount)
        }

    @Test
    fun pushSucceedsWhenServerConfigAvailable() =
        runTest {
            networkConfigDataSource.setServerConfigResult(
                NetworkResult.Success(
                    ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
                ),
            )
            val repo = buildRepo()
            val result = repo.pushButton("ack-token")
            assertEquals(AppResult.Success(Unit), result)
            assertEquals(1, networkButtonDataSource.pushCount)
        }

    @Test
    fun pushFailsAfterHttpError() =
        runTest {
            networkConfigDataSource.setServerConfigResult(
                NetworkResult.Success(
                    ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
                ),
            )
            networkButtonDataSource.setPushResult(NetworkResult.HttpError(500))
            val repo = buildRepo()
            val result = repo.pushButton("ack-token")
            assertEquals(AppResult.Error(ActionError.NetworkFailed), result)
            assertEquals(1, networkButtonDataSource.pushCount)
        }

    @Test
    fun aForbiddenAnswerIsReportedAsForbiddenNotAsNetworkFailure() =
        runTest {
            networkConfigDataSource.setServerConfigResult(
                NetworkResult.Success(
                    ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
                ),
            )
            networkButtonDataSource.setPushResult(NetworkResult.HttpError(NetworkRemoteButtonRepository.HTTP_FORBIDDEN))
            val repo = buildRepo()
            assertEquals(AppResult.Error(ActionError.Forbidden), repo.pushButton("ack-token"))
            // Positive control: the status one off is still a plain failure,
            // so the verdict cannot come from a mapper that refuses everything.
            networkButtonDataSource.setPushResult(NetworkResult.HttpError(404))
            assertEquals(AppResult.Error(ActionError.NetworkFailed), repo.pushButton("ack-token"))
        }

    @Test
    fun pushFailsAfterConnectionFailure() =
        runTest {
            networkConfigDataSource.setServerConfigResult(
                NetworkResult.Success(
                    ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
                ),
            )
            networkButtonDataSource.setPushResult(NetworkResult.ConnectionFailed)
            val repo = buildRepo()
            val result = repo.pushButton("ack-token")
            assertEquals(AppResult.Error(ActionError.NetworkFailed), result)
        }

    @Test
    fun pushFailsWhenFeatureDisabled() =
        runTest {
            networkConfigDataSource.setServerConfigResult(
                NetworkResult.Success(
                    ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
                ),
            )
            val disabledRepo = buildRepo(enabled = false)
            val result = disabledRepo.pushButton("ack-token")
            assertEquals(AppResult.Error(ActionError.NetworkFailed), result)
            assertEquals(0, networkButtonDataSource.pushCount)
        }

    @Test
    fun pushForcesIdTokenRefresh() =
        runTest {
            // ADR-027: the repo always force-refreshes the token before
            // a push so the request never carries a stale credential.
            networkConfigDataSource.setServerConfigResult(
                NetworkResult.Success(
                    ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
                ),
            )
            networkButtonDataSource.setPushResult(NetworkResult.Success(Unit))
            val authRepo = FakeAuthRepository().apply {
                setIdTokenResult(FirebaseIdToken(idToken = "token", exp = Long.MAX_VALUE))
            }
            val repo = NetworkRemoteButtonRepository(
                networkButtonDataSource,
                CachedServerConfigRepository(networkConfigDataSource, "test-key", externalScope),
                authRepository = authRepo,
                remoteButtonPushEnabled = true,
            )
            repo.pushButton("ack-token")
            assertEquals(1, authRepo.getIdTokenForceRefreshCount)
        }

    @Test
    fun pushFailsWhenIdTokenNull() =
        runTest {
            // ADR-027: the repo handles the case where AuthRepository
            // returns no token (sign-in race or sign-out mid-call).
            networkConfigDataSource.setServerConfigResult(
                NetworkResult.Success(
                    ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
                ),
            )
            val authRepo = FakeAuthRepository() // no setIdTokenResult — getIdToken returns null
            val repo = NetworkRemoteButtonRepository(
                networkButtonDataSource,
                CachedServerConfigRepository(networkConfigDataSource, "test-key", externalScope),
                authRepository = authRepo,
                remoteButtonPushEnabled = true,
            )
            val result = repo.pushButton("ack-token")
            assertEquals(AppResult.Error(ActionError.NetworkFailed), result)
            assertEquals(0, networkButtonDataSource.pushCount, "Should NOT call data source when token is null")
        }
}
