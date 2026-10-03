package org.videolan.vlc.discourse

import android.content.Context
import android.net.Uri
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.media.MediaUtils

private const val DISCOURSE_TAG_PREFIX = "osho_discourse:"
private const val DISCOURSE_TAG_SEPARATOR = "|"

data class DiscoursePlaybackIds(val discourseId: String, val audioId: String)

fun DiscourseAudio.toMediaWrapper(context: Context): MediaWrapper = MLServiceLocator.getAbstractMediaWrapper(
    DiscourseDownloadStore(context).playbackUri(this) ?: Uri.EMPTY,
    0L,
    0f,
    durationSeconds?.times(1_000)?.toLong() ?: 0L,
    MediaWrapper.TYPE_AUDIO,
    null,
    title,
    0L,
    0L,
    "Osho",
    "Discourse",
    0L,
    discourseName,
    "Osho",
    0,
    0,
    resolveDiscourseUrl(discourseThumbnailUrl),
    -2,
    -2,
    trackNumber ?: 0,
    0,
    0L,
    0L,
    0L
).apply { tag = "$DISCOURSE_TAG_PREFIX${this@toMediaWrapper.discourseId}$DISCOURSE_TAG_SEPARATOR${this@toMediaWrapper.id}" }

fun MediaWrapper.discoursePlaybackIds(): DiscoursePlaybackIds? {
    val value = tag?.removePrefix(DISCOURSE_TAG_PREFIX) ?: return null
    if (value == tag) return null
    val ids = value.split(DISCOURSE_TAG_SEPARATOR, limit = 2)
    return if (ids.size == 2 && ids.all(String::isNotBlank)) DiscoursePlaybackIds(ids[0], ids[1]) else null
}

fun Context.playDiscourseAudio(audio: DiscourseAudio) = MediaUtils.openMedia(this, audio.toMediaWrapper(this))

fun Context.playDiscourseAudios(audios: List<DiscourseAudio>, position: Int = 0) =
    MediaUtils.openList(this, audios.map { it.toMediaWrapper(this) }, position)
