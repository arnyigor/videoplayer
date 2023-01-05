package com.arny.mobilecinema.di.models

import okhttp3.ResponseBody
import retrofit2.http.*

interface VideoApiService {
    @FormUrlEncoded
    @POST
    suspend fun postRequest(
        @Url url: String,
        @FieldMap fields: Map<String, String>,
        @HeaderMap headers: Map<String, String?>? = null
    ): ResponseBody

    @GET
    suspend fun getRequest(
        @Url url: String?,
        @HeaderMap headers: Map<String, String?>? = null
    ): ResponseBody
}
