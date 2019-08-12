package com.chimerapps.gradle.api

import okhttp3.Interceptor
import okhttp3.Response

class RetryInterceptor(private val maxRetries: Int) : Interceptor {

    private companion object {
        private const val BACKOFF_TIMEOUT = 5000L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        var response = chain.proceed(originalRequest)

        if (response.isSuccessful)
            return response

        for (i in 0 until maxRetries) {
            when (response.code) {
                500, 503, 504 -> {
                    Thread.sleep(BACKOFF_TIMEOUT)
                    //Try again
                    response = chain.proceed(originalRequest)
                    if (response.isSuccessful)
                        return response
                }
                else -> return response
            }
        }
        return response
    }

}