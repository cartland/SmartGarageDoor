package com.chriscartland.garage.data.repository

import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.ServerConfig
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

    private fun buildRepo(enabled: Boolean = true): NetworkRemoteButtonRepository =
        NetworkRemoteButtonRepository(
            networkButtonDataSource,
            CachedServerConfigRepository(networkConfigDataSource, "test-key", externalScope),
            remoteButtonPushEnabled = enabled,
        )

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
    fun pushReturnsFalseWhenServerConfigFetchFails() =
        runTest {
            networkConfigDataSource.setServerConfigResult(NetworkResult.ConnectionFailed)
            val repo = buildRepo()
            val result = repo.pushButton("token", "ack-token")
            assertEquals(false, result)
            assertEquals(0, networkButtonDataSource.pushCount)
        }

    @Test
    fun pushReturnsTrueWhenServerConfigAvailable() =
        runTest {
            networkConfigDataSource.setServerConfigResult(
                NetworkResult.Success(
                    ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
                ),
            )
            val repo = buildRepo()
            val result = repo.pushButton("token", "ack-token")
            assertEquals(true, result)
            assertEquals(1, networkButtonDataSource.pushCount)
        }

    @Test
    fun pushReturnsFalseAfterHttpError() =
        runTest {
            networkConfigDataSource.setServerConfigResult(
                NetworkResult.Success(
                    ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
                ),
            )
            networkButtonDataSource.setPushResult(NetworkResult.HttpError(500))
            val repo = buildRepo()
            val result = repo.pushButton("token", "ack-token")
            assertEquals(false, result)
            assertEquals(1, networkButtonDataSource.pushCount)
        }

    @Test
    fun pushReturnsFalseAfterConnectionFailure() =
        runTest {
            networkConfigDataSource.setServerConfigResult(
                NetworkResult.Success(
                    ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
                ),
            )
            networkButtonDataSource.setPushResult(NetworkResult.ConnectionFailed)
            val repo = buildRepo()
            val result = repo.pushButton("token", "ack-token")
            assertEquals(false, result)
        }

    @Test
    fun pushReturnsFalseWhenFeatureDisabled() =
        runTest {
            networkConfigDataSource.setServerConfigResult(
                NetworkResult.Success(
                    ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
                ),
            )
            val disabledRepo = buildRepo(enabled = false)
            val result = disabledRepo.pushButton("token", "ack-token")
            assertEquals(false, result)
            assertEquals(0, networkButtonDataSource.pushCount)
        }
}
