package com.chimerapps.gradle.api

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.gradle.api.logging.Logger
import java.io.IOException
import java.util.*

class RetryInterceptor(private val maxRetries: Int, private val logger: Logger) : Interceptor {

    private companion object {
        private const val BACKOFF_TIMEOUT = 5000L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        var response = try {
            chain.proceed(originalRequest)
        } catch (e: IOException) {
            logger.info(
                "[AppCenter] - (${Date()}) - Request ${originalRequest.url} failed with error (${e.message}), optionally retry $maxRetries times",
                e
            )
            null
        }

        if (response != null && response.isSuccessful)
            return response

        for (i in 0 until maxRetries) {
            response = when (response?.code) {
                null -> {
                    retryRequest(request = originalRequest, chain = chain, retryCount = i + 1)
                }
                500, 503, 504 -> {
                    retryRequest(request = originalRequest, chain = chain, retryCount = i + 1)
                }
                else -> return response
            }
            if (response?.isSuccessful == true) {
                logger.info("[AppCenter] - (${Date()}) - Retried request success! (${originalRequest.url})")
                return response
            }
        }
        if (response == null)
            throw IOException("Failed to retry request")
        return response
    }

    private fun retryRequest(request: Request, chain: Interceptor.Chain, retryCount: Int): Response? {
        logger.info("[AppCenter] - (${Date()}) - Retry request ${request.url}")
        Thread.sleep(BACKOFF_TIMEOUT)
        //Try again
        return try {
            chain.proceed(request)
        } catch (e: IOException) {
            logger.info(
                "[AppCenter] - (${Date()}) - Request ${request.url} failed with error (${e.message}), retry $retryCount out of $maxRetries times",
                e
            )
            null
        }
    }

}