package com.banknotif.gateway.data.remote

import okhttp3.OkHttpClient
import java.time.Duration

object ApiClient {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(10))
        .writeTimeout(Duration.ofSeconds(10))
        .build()
}
