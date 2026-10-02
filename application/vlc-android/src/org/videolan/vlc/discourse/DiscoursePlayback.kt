package org.videolan.vlc.discourse

import android.content.Context
import android.net.Uri
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.media.MediaUtils

fun DiscourseAudio.toMediaWrapper(): MediaWrapper = MLServiceLocator.getAbstractMediaWrapper(
    Uri.parse(audioUrl),
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
    discourseThumbnailUrl,
    -2,
    -2,
    trackNumber ?: 0,
    0,
    0L,
    0L,
    0L
).apply { tag = this@toMediaWrapper.id }

fun Context.playDiscourseAudio(audio: DiscourseAudio) = MediaUtils.openMedia(this, audio.toMediaWrapper())

fun Context.playDiscourseAudios(audios: List<DiscourseAudio>, position: Int = 0) =
    MediaUtils.openList(this, audios.map(DiscourseAudio::toMediaWrapper), position)
