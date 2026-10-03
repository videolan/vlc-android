package org.videolan.vlc.discourse

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.ConnectivityInterceptor
import org.videolan.vlc.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

interface DiscourseApi {
    @GET(".")
    suspend fun index(@Header("Cache-Control") cacheControl: String? = null): ApiIndex

    @GET("discourses")
    suspend fun discourses(
        @Query("page") page: Int = 1,
        @Query("search") search: String? = null,
        @Query("is_audio_cleaned") isAudioCleaned: Boolean? = null,
        @Query("language") language: String? = null,
        @Query("sort") sort: String? = null,
        @Header("Cache-Control") cacheControl: String? = null
    ): PageResponse<Discourse>

    @GET("discourse-audios")
    suspend fun discourseAudios(
        @Query("page") page: Int = 1,
        @Query("search") search: String? = null,
        @Query("discourse_name") discourseName: String? = null,
        @Query("language") language: String? = null,
        @Header("Cache-Control") cacheControl: String? = null
    ): PageResponse<DiscourseAudio>

    @GET("discourse-audios")
    suspend fun discourseAudios(
        @Query("discourse_id") discourseId: String,
        @Header("Cache-Control") cacheControl: String? = null
    ): DiscourseAudiosResponse

    @PUT("discourses/{id}/like")
    suspend fun likeDiscourse(@Path("id") id: String, @Body body: LikeRequest): LikeResponse

    @PUT("discourse-audios/{id}/like")
    suspend fun likeDiscourseAudio(@Path("id") id: String, @Body body: LikeRequest): LikeResponse

    @POST("stats")
    suspend fun recordStats(@Body body: StatsRequest): StatsResponse

    @GET("stats")
    suspend fun discourseStats(
        @Query("by") by: String = "discourse",
        @Query("time") time: String = "7_days",
        @Header("Cache-Control") cacheControl: String? = null
    ): DiscourseStatsResponse

    @GET("stats")
    suspend fun discourseAudioStats(
        @Query("by") by: String = "discourse_audio",
        @Query("time") time: String = "7_days",
        @Header("Cache-Control") cacheControl: String? = null
    ): DiscourseAudioStatsResponse
}

object DiscourseApiClient {
    val instance: DiscourseApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.OSHO_API_URL)
            .client(
                OkHttpClient.Builder()
                    .cache(Cache(File(AppContextProvider.appContext.cacheDir, "osho-api"), 10L * 1024 * 1024))
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("x-api-key", BuildConfig.OSHO_API_KEY)
                                .build()
                        )
                    }
                    .addInterceptor(StaleCacheInterceptor())
                    .addNetworkInterceptor(ConnectivityInterceptor(AppContextProvider.appContext))
                    .addNetworkInterceptor(CacheResponseInterceptor())
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(
                MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build())
            )
            .build()
            .create(DiscourseApi::class.java)
    }
}

internal class CacheResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request()).let { response ->
        if (response.request.method == "GET" && response.isSuccessful && !response.cacheControl.noStore)
            response.newBuilder().header("Cache-Control", "public, max-age=$CACHE_SECONDS").build()
        else response
    }
}

internal class StaleCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)
        return try {
            val response = chain.proceed(request)
            if (response.code < 500) response else cached(chain, request)?.also { response.close() } ?: response
        } catch (error: IOException) {
            cached(chain, request) ?: throw error
        }
    }

    private fun cached(chain: Interceptor.Chain, request: Request): Response? {
        val response = chain.proceed(
            request.newBuilder().header("Cache-Control", "only-if-cached, max-stale=$MAX_STALE_SECONDS").build()
        )
        return if (response.code == 504) null.also { response.close() } else response
    }
}

private const val CACHE_SECONDS = 24 * 60 * 60
private const val MAX_STALE_SECONDS = Int.MAX_VALUE
internal const val FORCE_REFRESH = "no-cache"
internal fun cacheControl(forceRefresh: Boolean) = if (forceRefresh) FORCE_REFRESH else null
