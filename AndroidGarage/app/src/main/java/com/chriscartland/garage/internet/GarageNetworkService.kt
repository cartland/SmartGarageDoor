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

import androidx.annotation.Keep
import com.chriscartland.garage.config.APP_CONFIG
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import javax.inject.Singleton

interface GarageNetworkService {
    suspend fun getCurrentEventData(
        buildTimestamp: String,
        session: String? = null,
    ): Response<CurrentEventDataResponse>

    suspend fun getRecentEventData(
        buildTimestamp: String,
        session: String? = null,
        count: Int? = null,
    ): Response<RecentEventDataResponse>

    suspend fun getServerConfig(serverConfigKey: String): Response<ServerConfigResponse>

    suspend fun postRemoteButtonPush(
        remoteButtonBuildTimestamp: RemoteButtonBuildTimestamp,
        buttonAckToken: ButtonAckToken,
        remoteButtonPushKey: RemoteButtonPushKey,
        idToken: IdToken,
    ): Response<RemoteButtonPushResponse>

    suspend fun postSnoozeOpenDoorsNotifications(
        buildTimestamp: BuildTimestamp,
        remoteButtonPushKey: RemoteButtonPushKey,
        idToken: IdToken,
        snoozeDuration: SnoozeDurationParameter,
        snoozeEventTimestamp: SnoozeEventTimestampParameter,
    ): Response<SnoozeOpenDoorNotificationsResponse>

    suspend fun getSnooze(buildTimestamp: BuildTimestamp): Response<GetSnoozeResponse>
}

@JvmInline
value class BuildTimestamp(
    private val s: String,
)

@JvmInline
value class RemoteButtonBuildTimestamp(
    private val s: String,
)

@JvmInline
value class ButtonAckToken(
    private val s: String,
)

@JvmInline
value class RemoteButtonPushKey(
    private val s: String,
)

@JvmInline
value class IdToken(
    private val s: String,
)

@JvmInline
value class SnoozeDurationParameter(
    private val s: String,
)

@JvmInline
value class SnoozeEventTimestampParameter(
    private val s: Long,
)

@Keep
interface RetrofitGarageNetworkService : GarageNetworkService {
    @GET("currentEventData")
    override suspend fun getCurrentEventData(
        @Query("buildTimestamp") buildTimestamp: String,
        @Query("session") session: String?,
    ): Response<CurrentEventDataResponse>

    @GET("eventHistory")
    override suspend fun getRecentEventData(
        @Query("buildTimestamp") buildTimestamp: String,
        @Query("session") session: String?,
        @Query("eventHistoryMaxCount") count: Int?,
    ): Response<RecentEventDataResponse>

    @GET("serverConfig")
    override suspend fun getServerConfig(
        @Header("X-ServerConfigKey") serverConfigKey: String,
    ): Response<ServerConfigResponse>

    @POST("addRemoteButtonCommand")
    override suspend fun postRemoteButtonPush(
        @Query("buildTimestamp") remoteButtonBuildTimestamp: RemoteButtonBuildTimestamp,
        @Query("buttonAckToken") buttonAckToken: ButtonAckToken,
        @Header("X-RemoteButtonPushKey") remoteButtonPushKey: RemoteButtonPushKey,
        @Header("X-AuthTokenGoogle") idToken: IdToken,
    ): Response<RemoteButtonPushResponse>

    @POST("snoozeNotificationsRequest")
    override suspend fun postSnoozeOpenDoorsNotifications(
        @Query("buildTimestamp") buildTimestamp: BuildTimestamp,
        @Header("X-RemoteButtonPushKey") remoteButtonPushKey: RemoteButtonPushKey,
        @Header("X-AuthTokenGoogle") idToken: IdToken,
        @Query("snoozeDuration") snoozeDuration: SnoozeDurationParameter,
        @Query("snoozeEventTimestamp") snoozeEventTimestamp: SnoozeEventTimestampParameter,
    ): Response<SnoozeOpenDoorNotificationsResponse>

    @GET("snoozeNotificationsLatest")
    override suspend fun getSnooze(
        @Query("buildTimestamp") buildTimestamp: BuildTimestamp,
    ): Response<GetSnoozeResponse>
}

@Module
@InstallIn(SingletonComponent::class)
object RetrofitModule {
    @Provides
    @Singleton
    fun provideGarageService(): GarageNetworkService {
        val moshi: Moshi =
            Moshi
                .Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
        val logging =
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        val client: OkHttpClient =
            OkHttpClient
                .Builder()
                .addInterceptor(logging)
                .build()
        val retrofit: Retrofit =
            Retrofit
                .Builder()
                .baseUrl(APP_CONFIG.baseUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
        return retrofit.create(RetrofitGarageNetworkService::class.java)
    }
}
