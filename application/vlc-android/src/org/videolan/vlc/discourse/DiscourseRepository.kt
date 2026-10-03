package org.videolan.vlc.discourse

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.videolan.tools.Settings

class DiscourseRepository(
    context: Context,
    private val api: DiscourseApi = DiscourseApiClient.instance
) {
    private val appContext = context.applicationContext
    private val settings = Settings.getInstance(context)
    private val statsStore = DiscourseStatsStore(appContext)
    private val recentlyPlayedAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<Discourse>>(Types.newParameterizedType(List::class.java, Discourse::class.java))
    private val statsDiscoursesAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<Discourse>>(Types.newParameterizedType(List::class.java, Discourse::class.java))
    private val statsAudiosAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<DiscourseAudio>>(Types.newParameterizedType(List::class.java, DiscourseAudio::class.java))
    private val recentlyPlayedAudiosAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<DiscourseAudio>>(Types.newParameterizedType(List::class.java, DiscourseAudio::class.java))

    suspend fun apiIndex() = api.index()

    suspend fun getDiscourses(
        page: Int = 1,
        search: String? = null,
        isAudioCleaned: Boolean? = null,
        language: String? = null,
        sort: String? = null,
        forceRefresh: Boolean = false
    ) = api.discourses(
        page,
        search.cleanQuery(),
        isAudioCleaned,
        language.cleanQuery(),
        sort.cleanQuery(),
        cacheControl(forceRefresh)
    )

    val catalogueLanguage: String?
        get() = settings.getString(KEY_CATALOGUE_LANGUAGE, null)

    val catalogueSort: String?
        get() = settings.getString(KEY_CATALOGUE_SORT, null)

    fun saveCatalogueFilters(language: String?, sort: String?) = settings.edit()
        .putString(KEY_CATALOGUE_LANGUAGE, language)
        .putString(KEY_CATALOGUE_SORT, sort)
        .apply()

    suspend fun getDiscourseAudios(
        page: Int = 1,
        search: String? = null,
        discourseName: String? = null,
        language: String? = null,
        forceRefresh: Boolean = false
    ) = api.discourseAudios(page, search.cleanQuery(), discourseName.cleanQuery(), language.cleanQuery(), cacheControl(forceRefresh))

    suspend fun getDiscourseAudios(discourseId: String, forceRefresh: Boolean = false) =
        api.discourseAudios(discourseId, cacheControl(forceRefresh))

    suspend fun getWeeklyDiscourseStats(forceRefresh: Boolean = false): List<Discourse> =
        getWeeklyStats(KEY_STATS_DISCOURSES, statsDiscoursesAdapter, forceRefresh) {
            api.discourseStats(cacheControl = cacheControl(forceRefresh)).data
        }

    suspend fun getWeeklyAudioStats(forceRefresh: Boolean = false): List<DiscourseAudio> =
        getWeeklyStats(KEY_STATS_AUDIOS, statsAudiosAdapter, forceRefresh) {
            api.discourseAudioStats(cacheControl = cacheControl(forceRefresh)).data
        }

    suspend fun likeDiscourse(id: String): LikeData = api.likeDiscourse(id, LikeRequest(userId)).data.also {
        settings.edit().putStringSet(KEY_LIKED_DISCOURSES, likedDiscourses + id).apply()
    }

    suspend fun likeDiscourseAudio(id: String): LikeData = api.likeDiscourseAudio(id, LikeRequest(userId)).data.also {
        settings.edit().putStringSet(KEY_LIKED_AUDIOS, likedAudios + id).apply()
    }

    suspend fun recordListeningStats(discourseId: String, audioId: String) {
        if (!statsStore.shouldSend(discourseId, audioId)) return
        api.recordStats(StatsRequest(userId, discourseId, audioId))
        statsStore.markSent(discourseId, audioId)
    }

    val likedDiscourses: Set<String>
        get() = settings.getStringSet(KEY_LIKED_DISCOURSES, emptySet()).orEmpty()

    val likedAudios: Set<String>
        get() = settings.getStringSet(KEY_LIKED_AUDIOS, emptySet()).orEmpty()

    val recentlyPlayedDiscourses: List<Discourse>
        get() = settings.getString(KEY_RECENTLY_PLAYED_DISCOURSES, null)?.let { json ->
            runCatching { recentlyPlayedAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
        } ?: emptyList()

    fun recordRecentlyPlayed(discourse: Discourse) {
        val recent = recentlyPlayedDiscourses.filterNot { it.id == discourse.id }
        settings.edit()
            .putString(KEY_RECENTLY_PLAYED_DISCOURSES, recentlyPlayedAdapter.toJson((listOf(discourse) + recent).take(MAX_RECENTLY_PLAYED)))
            .apply()
    }

    val recentlyPlayedAudios: List<DiscourseAudio>
        get() = settings.getString(KEY_RECENTLY_PLAYED_AUDIOS, null)?.let { json ->
            runCatching { recentlyPlayedAudiosAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
        } ?: emptyList()

    fun recordRecentlyPlayed(audio: DiscourseAudio) {
        val recent = recentlyPlayedAudios.filterNot { it.id == audio.id }
        settings.edit()
            .putString(KEY_RECENTLY_PLAYED_AUDIOS, recentlyPlayedAudiosAdapter.toJson((listOf(audio) + recent).take(MAX_RECENTLY_PLAYED)))
            .apply()
    }

    private suspend fun <T> getWeeklyStats(
        key: String,
        adapter: com.squareup.moshi.JsonAdapter<List<T>>,
        forceRefresh: Boolean,
        request: suspend () -> List<T>
    ): List<T> {
        val cached = settings.getString(key, null)?.let { json ->
            runCatching { adapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
        }.orEmpty()
        val cachedAt = settings.getLong("$key.time", 0L)
        if (!forceRefresh && cachedAt > System.currentTimeMillis() - STATS_CACHE_TTL) return cached
        return try {
            request().also { saveStats(key, adapter, it) }
        } catch (_: Exception) {
            cached
        }
    }

    private fun <T> saveStats(key: String, adapter: com.squareup.moshi.JsonAdapter<List<T>>, value: List<T>) {
        settings.edit()
            .putString(key, adapter.toJson(value))
            .putLong("$key.time", System.currentTimeMillis())
            .apply()
    }

    private val userId: String
        get() = OshoUserIdentity.ensure(appContext)

    private fun String?.cleanQuery() = this?.trim()?.takeIf(String::isNotEmpty)

    private companion object {
        const val KEY_LIKED_DISCOURSES = "osho_api_liked_discourses"
        const val KEY_LIKED_AUDIOS = "osho_api_liked_audios"
        const val KEY_RECENTLY_PLAYED_DISCOURSES = "osho_api_recently_played_discourses"
        const val KEY_RECENTLY_PLAYED_AUDIOS = "osho_api_recently_played_audios"
        const val KEY_CATALOGUE_LANGUAGE = "osho_api_catalogue_language"
        const val KEY_CATALOGUE_SORT = "osho_api_catalogue_sort"
        const val MAX_RECENTLY_PLAYED = 24
        const val KEY_STATS_DISCOURSES = "osho_api_stats_discourses"
        const val KEY_STATS_AUDIOS = "osho_api_stats_audios"
        const val STATS_CACHE_TTL = 3L * 60L * 60L * 1000L
    }
}
