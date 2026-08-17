package org.videolan.vlc.addon

import android.content.Context

/**
 * Fournit l'implementation de [ContentApi] au reste de l'addon.
 *
 * Garde une seule instance par process pour que les positions
 * sauvegardees par l'ecran de lecture soient bien relues par l'accueil.
 */
object ContentApiProvider {
    @Volatile
    private var api: ContentApi? = null

    fun get(context: Context): ContentApi =
        api ?: synchronized(this) {
            api ?: FakeContentApi(context.applicationContext).also { api = it }
        }
}