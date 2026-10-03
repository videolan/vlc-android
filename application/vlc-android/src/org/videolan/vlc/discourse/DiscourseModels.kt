package org.videolan.vlc.discourse

import com.squareup.moshi.Json

data class PaginationMeta(
    val page: Int,
    @Json(name = "page_size") val pageSize: Int,
    @Json(name = "total_records") val totalRecords: Int,
    @Json(name = "total_pages") val totalPages: Int
)

data class Discourse(
    val id: String,
    val title: String,
    @Json(name = "thumbnail_url") val thumbnailUrl: String?,
    @Json(name = "is_audio_cleaned") val isAudioCleaned: Boolean,
    val language: String,
    val slug: String?,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "total_tracks") val totalTracks: Int = 0,
    @Json(name = "total_likes") val totalLikes: Int = 0
)

data class DiscourseAudio(
    val id: String,
    @Json(name = "discourse_id") val discourseId: String,
    @Json(name = "discourse_name") val discourseName: String,
    @Json(name = "discourse_thumbnail_url") val discourseThumbnailUrl: String?,
    val language: String,
    val title: String,
    @Json(name = "audio_url") val audioUrl: String,
    @Json(name = "duration_seconds") val durationSeconds: Double?,
    @Json(name = "file_size") val fileSize: Long?,
    @Json(name = "mime_type") val mimeType: String?,
    @Json(name = "track_number") val trackNumber: Int?,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "total_likes") val totalLikes: Int = 0
)

data class PageResponse<T>(val data: List<T>, val meta: PaginationMeta)

data class DiscourseAudiosResponse(
    val data: List<DiscourseAudio>,
    val meta: TotalRecordsMeta
)

data class TotalRecordsMeta(@Json(name = "total_records") val totalRecords: Int)

data class ApiIndex(val message: String, val endpoints: Map<String, String>)

data class LikeRequest(@Json(name = "user_id") val userId: String)

data class LikeResponse(val data: LikeData)

data class LikeData(
    @Json(name = "discourse_id") val discourseId: String? = null,
    @Json(name = "discourse_audio_id") val discourseAudioId: String? = null,
    @Json(name = "liked_by_user_id") val likedByUserId: String,
    val liked: Boolean,
    @Json(name = "total_likes") val totalLikes: Int
)
