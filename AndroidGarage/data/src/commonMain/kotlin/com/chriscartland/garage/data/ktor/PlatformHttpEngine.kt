package com.chriscartland.garage.data.ktor

import io.ktor.client.engine.HttpClientEngine

/**
 * Platform-specific HTTP engine.
 *
 * Android: OkHttp
 * iOS: Darwin (to be added)
 */
expect fun createPlatformHttpEngine(): HttpClientEngine
