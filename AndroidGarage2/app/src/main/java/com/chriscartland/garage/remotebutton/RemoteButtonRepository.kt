package com.chriscartland.garage.remotebutton

import android.text.format.DateFormat
import android.util.Log
import com.chriscartland.garage.APP_CONFIG
import com.chriscartland.garage.internet.ButtonAckToken
import com.chriscartland.garage.internet.GarageNetworkService
import com.chriscartland.garage.internet.IdToken
import com.chriscartland.garage.internet.RemoteButtonBuildTimestamp
import com.chriscartland.garage.internet.RemoteButtonPushKey
import com.chriscartland.garage.repository.ServerConfigRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.time.delay
import java.time.Duration
import java.util.Date
import javax.inject.Inject

interface RemoteButtonRepository {
    val pushStatus: StateFlow<PushStatus>
    suspend fun pushRemoteButton(idToken: IdToken, buttonAckToken: String)
}

class RemoteButtonRepositoryImpl @Inject constructor(
    private val network: GarageNetworkService,
    private val serverConfigRepository: ServerConfigRepository,
) : RemoteButtonRepository {
    private val _pushStatus = MutableStateFlow(PushStatus.IDLE)
    override val pushStatus: StateFlow<PushStatus> = _pushStatus

    /**
     * Send a command to the server to push the remote button.
     */
    override suspend fun pushRemoteButton(
        idToken: IdToken,
        buttonAckToken: String,
    ) {
        _pushStatus.value = PushStatus.SENDING
        val tag = "pushRemoteButton"
        val serverConfig = serverConfigRepository.serverConfigCached()
        if (serverConfig == null) {
            Log.e(tag, "Server config is null")
            _pushStatus.value = PushStatus.IDLE
            return
        }
        Log.d(tag, "Pushing remote button")
        Log.d(tag, "Server config: $serverConfig")
        Log.d(tag, "Button ack token: $buttonAckToken")

        if (!APP_CONFIG.remoteButtonPushEnabled) {
            Log.w(tag, "Remote button push is disabled: !remoteButtonPushEnabled")
        }
        if (APP_CONFIG.remoteButtonPushEnabled) {
            val response = network.postRemoteButtonPush(
                remoteButtonBuildTimestamp = RemoteButtonBuildTimestamp(
                    serverConfig.remoteButtonBuildTimestamp,
                ),
                buttonAckToken = ButtonAckToken(buttonAckToken),
                remoteButtonPushKey = RemoteButtonPushKey(
                    serverConfig.remoteButtonPushKey,
                ),
                idToken = idToken,
            )
            Log.i(tag, "Response: ${response.code()}")
            Log.i(tag, "Response body: ${response.body()}")
        }
        delay(Duration.ofMillis(100))
        _pushStatus.value = PushStatus.IDLE
    }
}

enum class PushStatus {
    IDLE,
    SENDING,
}

/**
 * Create a button ack token.
 *
 * This token is created by the client so the server can acknowledge the remote button push.
 * The client can send the same token to the server multiple times and the server is
 * responsible for only processing the token once.
 * When the server receives a button press, it will respond with the token to the client.
 */
fun createButtonAckToken(now: Date): String {
    val humanReadable = DateFormat.format("yyyy-MM-dd hh:mm:ss a", now).toString()
    val timestampMillis = now.time
    val appVersion = "AppVersionTODO"
    val buttonAckTokenData = "android-$appVersion-$humanReadable-$timestampMillis"
    val re = Regex("[^a-zA-Z0-9-_.]")
    val filtered = re.replace(buttonAckTokenData, ".")
    return filtered
}

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class RemoteButtonRepositoryModule {
    @Binds
    abstract fun bindRemoteButtonRepository(
        remoteButtonRepository: RemoteButtonRepositoryImpl,
    ): RemoteButtonRepository
}