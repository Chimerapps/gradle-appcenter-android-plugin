package com.chimerapps.gradle.api

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.gradle.api.logging.Logger
import java.io.IOException
import java.util.concurrent.TimeoutException

class RetryInterceptor(private val maxRetries: Int, private val logger: Logger) : Interceptor {

    private companion object {
        private const val BACKOFF_TIMEOUT = 5000L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        var response = try {
            chain.proceed(originalRequest)
        } catch (e: TimeoutException) {
            logger.info("[AppCenter] Request ${originalRequest.url} timed out, optionally retry $maxRetries times")
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
                logger.info("[AppCenter] Retried request success! (${originalRequest.url})")
                return response
            }
        }
        if (response == null)
            throw IOException("Failed to retry request")
        return response
    }

    private fun retryRequest(request: Request, chain: Interceptor.Chain, retryCount: Int): Response? {
        logger.info("[AppCenter] Retry request ${request.url}")
        Thread.sleep(BACKOFF_TIMEOUT)
        //Try again
        return try {
            chain.proceed(request)
        } catch (e: TimeoutException) {
            logger.info("[AppCenter] Request ${request.url} timed out, retry $retryCount out of $maxRetries times")
            null
        }
    }

}