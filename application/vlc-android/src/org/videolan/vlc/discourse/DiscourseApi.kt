package org.videolan.vlc.discourse

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.ConnectivityInterceptor
import org.videolan.vlc.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface DiscourseApi {
    @GET(".")
    suspend fun index(): ApiIndex

    @GET("discourses")
    suspend fun discourses(
        @Query("page") page: Int = 1,
        @Query("search") search: String? = null,
        @Query("is_audio_cleaned") isAudioCleaned: Boolean? = null
    ): PageResponse<Discourse>

    @GET("discourse-audios")
    suspend fun discourseAudios(
        @Query("page") page: Int = 1,
        @Query("search") search: String? = null,
        @Query("discourse_name") discourseName: String? = null,
        @Query("language") language: String? = null
    ): PageResponse<DiscourseAudio>

    @GET("discourse-audios")
    suspend fun discourseAudios(@Query("discourse_id") discourseId: String): DiscourseAudiosResponse

    @PUT("discourses/{id}/like")
    suspend fun likeDiscourse(@Path("id") id: String, @Body body: LikeRequest): LikeResponse

    @PUT("discourse-audios/{id}/like")
    suspend fun likeDiscourseAudio(@Path("id") id: String, @Body body: LikeRequest): LikeResponse
}

object DiscourseApiClient {
    val instance: DiscourseApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.OSHO_API_URL)
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(ConnectivityInterceptor(AppContextProvider.appContext))
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
