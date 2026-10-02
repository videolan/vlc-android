package org.videolan.vlc.discourse

import android.content.Context
import org.videolan.tools.Settings
import java.util.UUID

class DiscourseRepository(
    context: Context,
    private val api: DiscourseApi = DiscourseApiClient.instance
) {
    private val settings = Settings.getInstance(context)

    suspend fun apiIndex() = api.index()

    suspend fun getDiscourses(page: Int = 1, search: String? = null, isAudioCleaned: Boolean? = null, forceRefresh: Boolean = false) =
        api.discourses(page, search.cleanQuery(), isAudioCleaned, cacheControl(forceRefresh))

    suspend fun getDiscourseAudios(
        page: Int = 1,
        search: String? = null,
        discourseName: String? = null,
        language: String? = null,
        forceRefresh: Boolean = false
    ) = api.discourseAudios(page, search.cleanQuery(), discourseName.cleanQuery(), language.cleanQuery(), cacheControl(forceRefresh))

    suspend fun getDiscourseAudios(discourseId: String, forceRefresh: Boolean = false) =
        api.discourseAudios(discourseId, cacheControl(forceRefresh))

    suspend fun likeDiscourse(id: String): LikeData = api.likeDiscourse(id, LikeRequest(userId)).data.also {
        settings.edit().putStringSet(KEY_LIKED_DISCOURSES, likedDiscourses + id).apply()
    }

    suspend fun likeDiscourseAudio(id: String): LikeData = api.likeDiscourseAudio(id, LikeRequest(userId)).data.also {
        settings.edit().putStringSet(KEY_LIKED_AUDIOS, likedAudios + id).apply()
    }

    val likedDiscourses: Set<String>
        get() = settings.getStringSet(KEY_LIKED_DISCOURSES, emptySet()).orEmpty()

    val likedAudios: Set<String>
        get() = settings.getStringSet(KEY_LIKED_AUDIOS, emptySet()).orEmpty()

    private val userId: String
        get() = settings.getString(KEY_USER_ID, null) ?: UUID.randomUUID().toString().also {
            settings.edit().putString(KEY_USER_ID, it).apply()
        }

    private fun String?.cleanQuery() = this?.trim()?.takeIf(String::isNotEmpty)

    private companion object {
        const val KEY_USER_ID = "osho_api_user_id"
        const val KEY_LIKED_DISCOURSES = "osho_api_liked_discourses"
        const val KEY_LIKED_AUDIOS = "osho_api_liked_audios"
    }
}
