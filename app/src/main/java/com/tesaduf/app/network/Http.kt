package com.tesaduf.app.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val JsonMediaType = "application/json; charset=utf-8".toMediaType()

val TesadufJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .callTimeout(20, TimeUnit.SECONDS)
    .pingInterval(25, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

data class HttpResult(val code: Int, val body: String)

/** Runs the call off the main thread (OkHttp dispatcher) and cancels it with the coroutine. */
suspend fun Call.await(): HttpResult = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            val result = response.use { r ->
                runCatching { HttpResult(r.code, r.body?.string().orEmpty()) }
            }
            result.fold(
                onSuccess = { cont.resume(it) },
                onFailure = { if (!cont.isCancelled) cont.resumeWithException(it) },
            )
        }
    })
}
