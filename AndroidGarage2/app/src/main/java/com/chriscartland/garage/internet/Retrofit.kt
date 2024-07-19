package com.chriscartland.garage.internet

import com.chriscartland.garage.model.CurrentEventDataResponse
import com.squareup.moshi.Moshi
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

val moshi: Moshi = Moshi.Builder().build()

val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl("https://us-central1-escape-echo.cloudfunctions.net/")
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()

val service: GarageService = retrofit.create(GarageService::class.java)

interface GarageService {
    @GET("currentEventData")
    fun getCurrentEventData(
        @Query("buildTimestamp") buildTimestamp: String,
        @Query("session") session: String? = null,
    ): Call<CurrentEventDataResponse>
}
