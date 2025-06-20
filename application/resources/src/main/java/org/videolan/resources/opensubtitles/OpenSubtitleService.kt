package org.videolan.resources.opensubtitles

import android.util.Log
import com.moczul.ok2curl.CurlInterceptor
import com.moczul.ok2curl.logger.Logger
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.videolan.resources.AppContextProvider
import org.videolan.resources.BuildConfig
import org.videolan.resources.util.ConnectivityInterceptor
import org.videolan.tools.forbiddenChars
import org.videolan.tools.substrlng
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Date
import java.util.concurrent.TimeUnit


private const val BASE_URL = "https://api.opensubtitles.com/api/v1/"
const val USER_AGENT = "VLSub v0.9"
private const val DEBUG = false

private fun buildClient() = Retrofit.Builder()
    .baseUrl(BASE_URL)
    .client(
        getOkHttpClient()
    )
    .addConverterFactory(
        MoshiConverterFactory.create(
            Moshi.Builder()
                .add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe())
                .build()
        )
    )
    .build()
    .create(IOpenSubtitleService::class.java)

fun getOSK() = "${BuildConfig.VLC_OPEN_SUBTITLES_API_KEY}${(-47).forbiddenChars()}Y"

private fun getOkHttpClient(): OkHttpClient {
    val builder = OkHttpClient.Builder()

        .addInterceptor(DomainInterceptor())
        .addInterceptor(UserAgentInterceptor(USER_AGENT))
        .addInterceptor(ConnectivityInterceptor(AppContextProvider.appContext))

        .readTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)

    if (DEBUG) {
        builder
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .addInterceptor(CurlInterceptor(object : Logger {
                override fun log(message: String) {
                    Log.v("Ok2Curl", message)
                }
            }))
    }
    return builder.build()
}

private class UserAgentInterceptor(val userAgent: String): Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val requestBuilder = request.newBuilder()
            .header("User-Agent", userAgent)
            .header("Api-Key", getOSK().substrlng(55))
            .header("Accept", "application/json")
        if (OpenSubtitleClient.authorizationToken.isNotEmpty())requestBuilder.header("Authorization", OpenSubtitleClient.authorizationToken)
        return chain.proceed(requestBuilder.build())
    }
}

class DomainInterceptor : Interceptor {

    @Throws(Exception::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val newBuilder = request.newBuilder()
        OpenSubtitleClient.userDomain?.let {
            newBuilder.url(
                    request.url.toString()
                        .replace("api.opensubtitles.com", it)
                        .toHttpUrlOrNull() ?: request.url
                )
        }
        return chain.proceed(
            newBuilder.build()
        )
    }
}

interface OpenSubtitleClient {
    companion object {
        val instance: IOpenSubtitleService by lazy { buildClient() }
        var authorizationToken:String = ""
        var userDomain:String? = null
    }
}
