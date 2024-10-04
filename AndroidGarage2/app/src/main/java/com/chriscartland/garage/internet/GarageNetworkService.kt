package com.chriscartland.garage.internet

import com.chriscartland.garage.APP_CONFIG
import com.squareup.moshi.Moshi
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

    suspend fun getServerConfig(
        key: String,
    ): Response<ServerConfigResponse>

    suspend fun postRemoteButtonPush(
        remoteButtonBuildTimestamp: RemoteButtonBuildTimestamp,
        buttonAckToken: ButtonAckToken,
        remoteButtonPushKey: RemoteButtonPushKey,
        idToken: IdToken,
    ): Response<String>
}

@JvmInline
value class RemoteButtonBuildTimestamp(private val s: String)

@JvmInline
value class ButtonAckToken(private val s: String)

@JvmInline
value class RemoteButtonPushKey(private val s: String)

@JvmInline
value class IdToken(private val s: String)

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
        @Header("X-ServerConfigKey") serverConfigKey: String
    ): Response<ServerConfigResponse>

    @GET("addRemoteButtonCommand")
    override suspend fun postRemoteButtonPush(
        remoteButtonBuildTimestamp: RemoteButtonBuildTimestamp,
        buttonAckToken: ButtonAckToken,
        @Header("X-RemoteButtonPushKey") remoteButtonPushKey: RemoteButtonPushKey,
        @Header("X-AuthTokenGoogle") idToken: IdToken,
    ): Response<String>
}

@Module
@InstallIn(SingletonComponent::class)
object RetrofitModule {
    @Provides
    @Singleton
    fun provideGarageService(): GarageNetworkService {
        val moshi: Moshi = Moshi.Builder().build()
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(APP_CONFIG.baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(RetrofitGarageNetworkService::class.java)
    }
}
