package org.videolan.vlc.addon

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation bidon de l'API, locale, pour developper sans serveur.
 *
 * Les positions sont persiste dans des SharedPreferences ; la base d'URL
 * est un simple placeholder qui attend la vraie API.
 */
class FakeContentApi(private val context: Context) : ContentApi {

    // TODO: URL de la vraie API Xplayon
    override val baseUrl: String = "https://api.xplayon.invalid/v1"

    private val prefs = context.getSharedPreferences("xplayon_addon", Context.MODE_PRIVATE)

    private val catalog = listOf(
        ContentItem(
            id = "1",
            title = "Big Buck Bunny (sample)",
            position = 0,
            uri = "https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4"
        ),
        ContentItem(
            id = "2",
            title = "Sintel (sample)",
            position = 0,
            uri = "https://download.blender.org/durian/trailer/sintel_trailer-480p.mp4"
        )
    )

    private fun prefKey(id: String) = "content_$id"

    override suspend fun getItems(): List<ContentItem> = withContext(Dispatchers.IO) {
        catalog.map { it.copy(position = prefs.getLong(prefKey(it.id), 0L)) }
    }

    override suspend fun getItem(id: String): ContentItem? = withContext(Dispatchers.IO) {
        catalog.find { it.id == id }?.copy(position = prefs.getLong(prefKey(id), 0L))
    }

    override suspend fun savePosition(id: String, positionSeconds: Long) {
        withContext(Dispatchers.IO) {
            prefs.edit().putLong(prefKey(id), positionSeconds).apply()
        }
    }
}