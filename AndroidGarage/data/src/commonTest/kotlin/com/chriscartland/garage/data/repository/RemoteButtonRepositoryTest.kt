package com.chriscartland.garage.data.repository

import com.chriscartland.garage.data.NetworkResult
import com.chriscartland.garage.domain.model.ServerConfig
import com.chriscartland.garage.testcommon.FakeNetworkButtonDataSource
import com.chriscartland.garage.testcommon.FakeNetworkConfigDataSource
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests use real [CachedServerConfigRepository] with [FakeNetworkConfigDataSource],
 * exercising the actual caching and null-handling logic in the repository.
 */
class RemoteButtonRepositoryTest {
    private lateinit var networkButtonDataSource: FakeNetworkButtonDataSource
    private lateinit var networkConfigDataSource: FakeNetworkConfigDataSource
    private lateinit var repo: NetworkRemoteButtonRepository

    @BeforeTest
    fun setup() {
        networkButtonDataSource = FakeNetworkButtonDataSource()
        networkConfigDataSource = FakeNetworkConfigDataSource()
        repo = NetworkRemoteButtonRepository(
            networkButtonDataSource,
            CachedServerConfigRepository(networkConfigDataSource, "test-key"),
            remoteButtonPushEnabled = true,
        )
    }

    @Test
    fun pushReturnsFalseWhenServerConfigFetchFails() =
        runTest {
            networkConfigDataSource.serverConfigResult = NetworkResult.ConnectionFailed
            val result = repo.pushButton("token", "ack-token")
            assertEquals(false, result)
            assertEquals(0, networkButtonDataSource.pushCount)
        }

    @Test
    fun pushReturnsTrueWhenServerConfigAvailable() =
        runTest {
            networkConfigDataSource.serverConfigResult = NetworkResult.Success(
                ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
            )
            val result = repo.pushButton("token", "ack-token")
            assertEquals(true, result)
            assertEquals(1, networkButtonDataSource.pushCount)
        }

    @Test
    fun pushReturnsFalseAfterHttpError() =
        runTest {
            networkConfigDataSource.serverConfigResult = NetworkResult.Success(
                ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
            )
            networkButtonDataSource.pushResult = NetworkResult.HttpError(500)
            val result = repo.pushButton("token", "ack-token")
            assertEquals(false, result)
            assertEquals(1, networkButtonDataSource.pushCount)
        }

    @Test
    fun pushReturnsFalseAfterConnectionFailure() =
        runTest {
            networkConfigDataSource.serverConfigResult = NetworkResult.Success(
                ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
            )
            networkButtonDataSource.pushResult = NetworkResult.ConnectionFailed
            val result = repo.pushButton("token", "ack-token")
            assertEquals(false, result)
        }

    @Test
    fun pushReturnsFalseWhenFeatureDisabled() =
        runTest {
            val disabledRepo = NetworkRemoteButtonRepository(
                networkButtonDataSource,
                CachedServerConfigRepository(networkConfigDataSource, "test-key"),
                remoteButtonPushEnabled = false,
            )
            networkConfigDataSource.serverConfigResult = NetworkResult.Success(
                ServerConfig(buildTimestamp = "test", remoteButtonBuildTimestamp = "test", remoteButtonPushKey = "key"),
            )
            val result = disabledRepo.pushButton("token", "ack-token")
            assertEquals(false, result)
            assertEquals(0, networkButtonDataSource.pushCount)
        }
}
