package org.videolan.vlc.gui.browser

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.collection.SimpleArrayMap
import androidx.recyclerview.widget.RecyclerView
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.util.AndroidDevices

private val storages = SimpleArrayMap<String, String>()

class PathAdapter(val browser: PathAdapterListener, media: AbstractMediaWrapper) : RecyclerView.Adapter<PathAdapter.ViewHolder>() {

    init {
        //we save Base64 encoded strings to be used in substitutions to avoid false positives if a user directory is named as the media title
        // ie "SDCARD", "Internal Memory" and so on
        if (media.hasStateFlags(MediaLibraryItem.FLAG_STORAGE)) storages.put(Uri.decode(media.uri.path), makePathSafe(media.title))
        storages.put(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, makePathSafe(browser.currentContext().getString(R.string.internal_memory)))
    }

    private val browserTitle = browser.currentContext().getString(R.string.browser)
    private val otgDevice = browser.currentContext().getString(R.string.otg_device_title)
    private val segments = prepareSegments(media.uri)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.browser_path_item, parent, false) as TextView)
    }

    override fun getItemCount() = segments.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.root.text = when {
            //substitute a storage path to its name. See [replaceStoragePath]
            storages.containsKey(Uri.parse(segments[position]).path) -> retrieveSafePath(storages.valueAt(storages.indexOfKey(Uri.parse(segments[position]).path)))
            else -> Uri.parse(segments[position]).lastPathSegment
        }
    }

    inner class ViewHolder(val root: TextView) : RecyclerView.ViewHolder(root) {
        init {
            root.setOnClickListener {
                browser.backTo(adapterPosition.let {
                    when (it) {
                        0 -> "root"
                        else -> segments[it]
                    }
                })
            }
        }
    }

    /**
     * Splits an [Uri] in a list of string used as the adapter items
     * Each item is a string representing a valid path
     *
     * @param uri the [Uri] that has to be split
     * @return a list of strings representing the items
     */
    private fun prepareSegments(uri: Uri): MutableList<String> {
        val path = Uri.decode(uri.path)
        val isOtg = path.startsWith("/tree/")
        val string = when {
            isOtg -> if (path.endsWith(':')) "" else path.substringAfterLast(':')
            else -> replaceStoragePath(path)
        }
        val list: MutableList<String> = mutableListOf()
        if (isOtg) list.add(otgDevice)

        //list of all the path chunks
        val pathParts = string.split('/').filter { it.isNotEmpty() }
        for (index in pathParts.indices) {
            //start creating the Uri
            val currentPathUri = Uri.Builder().scheme(uri.scheme).authority(uri.authority)
            //append all the previous paths and the current one
            for (i in 0..index) appendPathToUri(pathParts[i], currentPathUri)
            list.add(currentPathUri.toString())
        }
        if (BuildConfig.DEBUG) list.forEach { Log.d(this::class.java.simpleName, "Added in breadcrumb: $it") }

        if (browser.showRoot()) list.add(0, browserTitle)
        return list
    }

    /**
     * Append a path to the Uri from a String
     * It takes care of substituting paths stored in [storages] and splitting if the substituted path contains file separators
     *
     * @param path the path to append
     * @param uri the uri the path should be appended to
     *
     */
    private fun appendPathToUri(path: String, uri: Uri.Builder) {
        var newPath = path
        for (i in 0..storages.size()) if (storages.valueAt(i) == newPath) newPath = storages.keyAt(i)
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
    private fun replaceStoragePath(path: String): String {
        try {
            if (storages.size() > 0) for (i in 0..storages.size()) if (path.startsWith(storages.keyAt(i))) return path.replace(storages.keyAt(i), storages.valueAt(i))
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
    private fun makePathSafe(path: String) = Base64.encodeToString(path.toByteArray(), Base64.DEFAULT)

    /**
     * Decodes a string previously encoded with [makePathSafe]
     *
     * @param encoded the encoded path string
     * @return the decoded path string
     */
    private fun retrieveSafePath(encoded: String) = String(Base64.decode(encoded, Base64.DEFAULT))
}

interface PathAdapterListener {
    fun backTo(tag: String)
    fun currentContext(): Context
    fun showRoot(): Boolean
}