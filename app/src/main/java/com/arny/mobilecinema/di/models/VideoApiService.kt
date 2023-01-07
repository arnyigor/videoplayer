package com.arny.mobilecinema.di.models

import okhttp3.ResponseBody
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Url

interface VideoApiService {
    @FormUrlEncoded
    @POST
    suspend fun postRequest(
        @Url url: String,
        @FieldMap fields: Map<String, String>,
        @HeaderMap headers: Map<String, String?> = emptyMap()
    ): ResponseBody

    @GET
    suspend fun getRequest(
        @Url url: String?,
        @HeaderMap headers: Map<String, String?> = emptyMap()
    ): ResponseBody
}
