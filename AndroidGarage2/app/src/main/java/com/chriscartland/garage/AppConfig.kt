package com.chriscartland.garage

data class AppConfig(
    val server: Server,
    val baseUrl: String,
)

enum class Server {
    Development,
    Production,
}

//private val SERVER = Server.Development
private val SERVER = Server.Production

val APP_CONFIG = AppConfig(
    server = SERVER,
    baseUrl = when (SERVER) {
        Server.Development -> "http://10.0.2.2:5001/escape-echo/us-central1/"
        Server.Production -> "https://us-central1-escape-echo.cloudfunctions.net/"
    },
)
