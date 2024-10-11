package com.chriscartland.garage.repository

import android.text.format.DateFormat
import android.util.Log
import com.chriscartland.garage.APP_CONFIG
import com.chriscartland.garage.internet.ButtonAckToken
import com.chriscartland.garage.internet.GarageNetworkService
import com.chriscartland.garage.internet.IdToken
import com.chriscartland.garage.internet.RemoteButtonBuildTimestamp
import com.chriscartland.garage.internet.RemoteButtonPushKey
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Date
import javax.inject.Inject

class RemoteButtonRepository @Inject constructor(
    private val network: GarageNetworkService,
    private val serverConfigRepository: ServerConfigRepository,
) {
    private val _pushButtonStatus = MutableStateFlow(PushButtonStatus.IDLE)
    val pushButtonStatus: StateFlow<PushButtonStatus> = _pushButtonStatus

    /**
     * Send a command to the server to push the remote button.
     */
    suspend fun pushRemoteButton(
        idToken: IdToken,
        buttonAckToken: String,
    ) {
        _pushButtonStatus.value = PushButtonStatus.SENDING
        val tag = "pushRemoteButton"
        val serverConfig = serverConfigRepository.serverConfigCached()
        if (serverConfig == null) {
            Log.e(tag, "Server config is null")
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
        _pushButtonStatus.value = PushButtonStatus.IDLE
    }

    /**
     * Create a button ack token.
     *
     * This token is created by the client so the server can acknowledge the remote button push.
     * The client can send the same token to the server multiple times and the server is
     * responsible for only processing the token once.
     * When the server receives a button press, it will respond with the token to the client.
     */
    fun createButtonAckToken(): String {
        val now = Date()
        val humanReadable = DateFormat.format("yyyy-MM-dd hh:mm:ss a", now).toString()
        val timestampMillis = now.time
        val appVersion = "AppVersionTODO"
        val buttonAckTokenData = "android-$appVersion-$humanReadable-$timestampMillis"
        val re = Regex("[^a-zA-Z0-9-_.]")
        val filtered = re.replace(buttonAckTokenData, ".")
        return filtered
    }
}
enum class PushButtonStatus {
    IDLE,
    SENDING,
}

@EntryPoint
@InstallIn(SingletonComponent::class)
@Suppress("unused")
interface RemoteButtonRepositoryEntryPoint {
    fun remoteButtonRepository(): RemoteButtonRepository
}
