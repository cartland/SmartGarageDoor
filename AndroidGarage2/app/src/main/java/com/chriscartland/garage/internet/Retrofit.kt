package com.chriscartland.garage.internet

import com.chriscartland.garage.APP_CONFIG
import com.chriscartland.garage.model.CurrentEventDataResponse
import com.chriscartland.garage.model.RecentEventDataResponse
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
import retrofit2.http.Query
import javax.inject.Singleton

interface GarageService {
    @GET("currentEventData")
    suspend fun getCurrentEventData(
        @Query("buildTimestamp") buildTimestamp: String,
        @Query("session") session: String? = null,
    ): Response<CurrentEventDataResponse>

    @GET("eventHistory")
    suspend fun getRecentEventData(
        @Query("buildTimestamp") buildTimestamp: String,
        @Query("session") session: String? = null,
    ): Response<RecentEventDataResponse>
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideGarageService(): GarageService {

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
        return retrofit.create(GarageService::class.java)
    }
}