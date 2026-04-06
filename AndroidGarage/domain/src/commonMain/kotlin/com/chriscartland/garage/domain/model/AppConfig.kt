package com.chriscartland.garage.domain.model

data class AppConfig(
    val server: Server,
    val baseUrl: String,
    val initialData: InitialData,
    val fetchOnViewModelInit: FetchOnViewModelInit,
    val recentEventCount: Int,
    val serverConfigKey: String,
    val snoozeNotificationsOption: Boolean,
    val remoteButtonPushEnabled: Boolean,
    val logSummary: Boolean,
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
