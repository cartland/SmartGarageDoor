/*
 * Copyright 2024 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.chriscartland.garage.internet

import com.chriscartland.garage.BuildConfig
import com.chriscartland.garage.config.APP_CONFIG
import com.chriscartland.garage.data.ktor.configureSharedHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android-specific HTTP client using OkHttp engine.
 *
 * Shared configuration (JSON, logging, base URL) is applied via
 * [configureSharedHttpClient]. Only the engine choice is platform-specific.
 */
fun provideKtorHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        configureSharedHttpClient(
            baseUrl = APP_CONFIG.baseUrl,
            debug = BuildConfig.DEBUG,
        )
    }
