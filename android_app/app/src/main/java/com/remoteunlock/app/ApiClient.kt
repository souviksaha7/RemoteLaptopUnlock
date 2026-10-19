package com.remoteunlock.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Handles HTTP communication with the Windows Agent running Flask.
 * Dispatches requests over IO dispatchers with strict timeouts suitable for LAN usage.
 */
object ApiClient {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /**
     * Checks if the Windows Agent is reachable and healthy.
     */
    suspend fun ping(ip: String, port: Int): Result<String> = withContext(Dispatchers.IO) {
        val url = "http://$ip:$port/ping"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Result.success(body)
                } else {
                    Result.failure(IOException("Server returned HTTP ${response.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends the authenticated unlock command with Bearer token and optional PIN payload.
     */
    suspend fun unlock(ip: String, port: Int, token: String, pin: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val url = "http://$ip:$port/unlock"
        val jsonPayload = if (pin.isNotBlank()) {
            """{"pin": "$pin"}"""
        } else {
            ""
        }
        val requestBody = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> Result.success(body)
                    401 -> Result.failure(SecurityException("Unauthorized: Invalid Bearer Token configured"))
                    400 -> Result.failure(IllegalStateException(body))
                    else -> Result.failure(IOException("HTTP error ${response.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends the authenticated lock command with Bearer token.
     */
    suspend fun lock(ip: String, port: Int, token: String): Result<String> = withContext(Dispatchers.IO) {
        val url = "http://$ip:$port/lock"
        val emptyBody = "".toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(emptyBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> Result.success(body)
                    401 -> Result.failure(SecurityException("Unauthorized: Invalid Bearer Token"))
                    else -> Result.failure(IOException("HTTP error ${response.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
