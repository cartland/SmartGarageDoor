package com.chriscartland.garage

data class AppConfig(
    val server: Server,
    val baseUrl: String,
    val initialData: InitialData,
    val fetchOnViewModelInit: FetchOnViewModelInit,
    val recentEventCount: Int,
    val serverConfigKey: String,
    val snoozeNotificationsOption: Boolean,
    val remoteButtonPushEnabled: Boolean,
)

enum class Server {
    Development,
    Production,
}

enum class InitialData {
    Demo,
    Empty,
}

enum class FetchOnViewModelInit {
    Yes,
    No,
}

//private val SERVER = Server.Development
private val SERVER = Server.Production

//private val INITIAL_DATA = InitialData.Demo
private val INITIAL_DATA = InitialData.Empty

//private val FETCH_ON_VIEW_MODEL_INIT = FetchOnViewModelInit.No
private val FETCH_ON_VIEW_MODEL_INIT = FetchOnViewModelInit.Yes

val APP_CONFIG = AppConfig(
    server = SERVER,
    baseUrl = when (SERVER) {
        Server.Development -> "http://10.0.2.2:5001/escape-echo/us-central1/"
        Server.Production -> "https://us-central1-escape-echo.cloudfunctions.net/"
    },
    initialData = INITIAL_DATA,
    fetchOnViewModelInit = FETCH_ON_VIEW_MODEL_INIT,
    recentEventCount = 30,
    serverConfigKey = BuildConfig.SERVER_CONFIG_KEY,
    snoozeNotificationsOption = false,
    remoteButtonPushEnabled = false,
)
