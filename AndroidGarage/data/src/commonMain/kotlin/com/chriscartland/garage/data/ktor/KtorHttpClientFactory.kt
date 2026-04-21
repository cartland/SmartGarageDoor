package com.chriscartland.garage.data.ktor

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Factory for fully configured [HttpClient] instances.
 *
 * The engine (OkHttp on Android, Darwin on iOS) is provided via
 * [createPlatformHttpEngine]; shared configuration is applied via
 * [configureSharedHttpClient].
 */
object KtorHttpClientFactory {
    fun create(
        baseUrl: String,
        debug: Boolean,
    ): HttpClient =
        HttpClient(createPlatformHttpEngine()) {
            configureSharedHttpClient(baseUrl, debug)
        }
}

/**
 * Configures an [HttpClient] with shared settings (JSON, logging, base URL).
 *
 * Platform-specific code provides the engine (OkHttp on Android, Darwin on iOS)
 * and calls this to apply the common configuration.
 */
fun <T : HttpClientConfig<*>> T.configureSharedHttpClient(
    baseUrl: String,
    debug: Boolean,
) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            },
        )
    }
    install(Logging) {
        level = if (debug) LogLevel.BODY else LogLevel.NONE
    }
    defaultRequest {
        url(baseUrl)
    }
}
