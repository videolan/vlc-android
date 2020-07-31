package org.videolan.resources.opensubtitles

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.ConnectivityInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit


private const val BASE_URL = "https://rest.opensubtitles.org/search/"
private const val USER_AGENT = "VLSub 0.9"

private fun buildClient() = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(OkHttpClient.Builder()
                .addInterceptor(UserAgentInterceptor(USER_AGENT))
                .addInterceptor(ConnectivityInterceptor(AppContextProvider.appContext))
                .readTimeout(10, TimeUnit.SECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .build())
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(IOpenSubtitleService::class.java)

private class UserAgentInterceptor(val userAgent: String): Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val userAgentRequest: Request = request.newBuilder().header("User-Agent", userAgent).build()
        return chain.proceed(userAgentRequest)
    }
}

interface OpenSubtitleClient {
    companion object { val instance: IOpenSubtitleService by lazy { buildClient() } }
}
