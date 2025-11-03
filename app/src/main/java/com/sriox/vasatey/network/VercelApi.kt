package com.sriox.vasatey.network

import com.sriox.vasatey.models.VercelNotificationRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface VercelApi {
    @POST("api/sendNotification")
    suspend fun sendNotification(
        @Body request: VercelNotificationRequest
    ): Response<ResponseBody>
}
