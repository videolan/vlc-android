package org.videolan.vlc.addon

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.AppScope
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.MediaUtils

/**
 * Lance la lecture d'un [ContentItem] en reprenant a la position
 * sauvegardee, et suit l'avancement pour la re-sauvegarder.
 */
object ContentPlayer {

    /**
     * Ouvre la lecture du contenu [item] via le [PlaybackService].
     *
     * La position de repli est injectee dans le [MediaWrapper] passe au
     * service : le PlaylistManager reprend alors automatiquement a
     * l'offset (en millisecondes).
     */
    fun play(context: Context, item: ContentItem) {
        val media = MediaWrapper(Uri.parse(item.uri)).apply {
            setTime(item.position * 1000L)
            addFlags(MediaWrapper.MEDIA_VIDEO)
        }
        PlaybackPositionTracker.track(context, item.id)
        MediaUtils.openMediaNoUi(context, media)
    }
}

/**
 * Ecoute la lecture en cours et sauvegarde la position sur l'API.
 *
 * Le suivi est strictement couple a [HomeActivity] / [DeepLinkActivity] :
 * on ne touche a aucun fichier VLC pour cela, on consomme simplement
 * le callback expose par [PlaybackService].
 */
object PlaybackPositionTracker : PlaybackService.Callback {

    private const val SAVE_EVERY_SECONDS = 5L
    private const val SAVE_EVERY_MS = SAVE_EVERY_SECONDS * 1000

    private var currentItemId: String? = null
    private var lastSavedTime = 0L
    private var callbackRegistered = false

    fun track(context: Context, itemId: String) {
        currentItemId = itemId
        lastSavedTime = 0L
        if (callbackRegistered) return
        callbackRegistered = true
        AppScope.launch {
            PlaybackService.start(context)
            PlaybackService.serviceFlow.filterNotNull().first().addCallback(this@PlaybackPositionTracker)
        }
    }

    override fun update() {
        val service = PlaybackService.instance ?: return
        val itemId = currentItemId ?: return
        val time = service.getTime()
        if (time <= 0L || time - lastSavedTime < SAVE_EVERY_MS) return
        lastSavedTime = time
        AppScope.launch {
            ContentApiProvider.get(service).savePosition(itemId, time / 1000L)
        }
    }

    override fun onMediaEvent(event: org.videolan.libvlc.interfaces.IMedia.Event) = Unit

    override fun onMediaPlayerEvent(event: org.videolan.libvlc.MediaPlayer.Event) {
        val itemId = currentItemId ?: return
        when (event.type) {
            org.videolan.libvlc.MediaPlayer.Event.Stopped,
            org.videolan.libvlc.MediaPlayer.Event.EndReached -> {
                val service = PlaybackService.instance ?: return
                val finalPosition = if (event.type == org.videolan.libvlc.MediaPlayer.Event.Stopped)
                    service.getTime() / 1000L else 0L
                AppScope.launch {
                    ContentApiProvider.get(service).savePosition(itemId, finalPosition)
                }
            }
            else -> Unit
        }
    }
}