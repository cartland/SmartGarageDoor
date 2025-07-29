package com.chriscartland.garage.config.model

data class ServerConfig(
    val buildTimestamp: String,
    val remoteButtonBuildTimestamp: String,
    val remoteButtonPushKey: String,
)
