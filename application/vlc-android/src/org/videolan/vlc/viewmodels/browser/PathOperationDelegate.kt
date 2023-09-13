package org.videolan.vlc.viewmodels.browser

import android.net.Uri
import android.util.Base64
import androidx.collection.SimpleArrayMap
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem

interface IPathOperationDelegate {

    fun appendPathToUri(path: String, uri: Uri.Builder)

    fun replaceStoragePath(path: String): String
    fun makePathSafe(path: String): String
    fun retrieveSafePath(encoded: String): String
    fun setDestination(media: MediaWrapper?)
    fun getAndRemoveDestination(): MediaWrapper?
    fun setSource(currentItem: MediaLibraryItem?)
    fun getSource(): MediaLibraryItem?
    fun consumeSource()
}

class PathOperationDelegate : IPathOperationDelegate {
    override fun setDestination(media: MediaWrapper?) {
        privateDestination = media
    }

    override fun setSource(currentItem: MediaLibraryItem?) {
        privateSource = currentItem
    }

    override fun getAndRemoveDestination(): MediaWrapper? {
        val destination = privateDestination
        privateDestination = null
        return destination
    }

    override fun consumeSource() {
        privateSource = null
    }

    override fun getSource() = privateSource

    companion object {
        val storages = SimpleArrayMap<String, String>()
        private var privateDestination: MediaWrapper? = null
        private var privateSource: MediaLibraryItem? = null
    }


    /**
     * Append a path to the Uri from a String
     * It takes care of substituting paths stored in [storages] and splitting if the substituted path contains file separators
     *
     * @param path the path to append
     * @param uri the uri the path should be appended to
     *
     */
    override fun appendPathToUri(path: String, uri: Uri.Builder) {
        var newPath = path
        for (i in 0 until storages.size()) if (storages.valueAt(i) == newPath) newPath = storages.keyAt(i)
        newPath.split('/').forEach {
            uri.appendPath(it)
        }
    }

    /**
     * Substitutes the [storages]keys by the [storages] values
     *
     * @param path the real path string
     * @return the path string with substitutions
     */
    override fun replaceStoragePath(path: String): String {
        try {
            if (storages.size() > 0) for (i in 0 until storages.size()) if (path.startsWith(storages.keyAt(i))) return path.replace(storages.keyAt(i), storages.valueAt(i))
        } catch (e: IllegalStateException) {
        }
        return path
    }

    /**
     * Encodes a String to avoid false positive substitusions
     *
     * @param path the path to encode
     * @return the encoded path
     */
    override fun makePathSafe(path: String) = Base64.encodeToString(path.toByteArray(), Base64.DEFAULT)

    /**
     * Decodes a string previously encoded with [makePathSafe]
     *
     * @param encoded the encoded path string
     * @return the decoded path string
     */
    override fun retrieveSafePath(encoded: String) = String(Base64.decode(encoded, Base64.DEFAULT))
}