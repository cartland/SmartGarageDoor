package com.chriscartland.garage.domain.model

data class ServerConfig(
    val buildTimestamp: String,
    val remoteButtonBuildTimestamp: String,
    val remoteButtonPushKey: String,
)
