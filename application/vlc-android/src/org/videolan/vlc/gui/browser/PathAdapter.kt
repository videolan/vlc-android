package org.videolan.vlc.gui.browser

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AndroidDevices
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.viewmodels.browser.IPathOperationDelegate
import org.videolan.vlc.viewmodels.browser.PathOperationDelegate

class PathAdapter(val browser: PathAdapterListener, media: MediaWrapper) : RecyclerView.Adapter<PathAdapter.ViewHolder>() {

    private val pathOperationDelegate = browser.getPathOperationDelegate()

    init {
        //we save Base64 encoded strings to be used in substitutions to avoid false positives if a user directory is named as the media title
        // ie "SDCARD", "Internal Memory" and so on
        if (media.hasStateFlags(MediaLibraryItem.FLAG_STORAGE)) PathOperationDelegate.storages.put(Uri.decode(media.uri.path), pathOperationDelegate.makePathSafe(media.title))
        PathOperationDelegate.storages.put(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, pathOperationDelegate.makePathSafe(browser.currentContext().getString(R.string.internal_memory)))
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
            PathOperationDelegate.storages.containsKey(segments[position].toUri().path) -> pathOperationDelegate.retrieveSafePath(PathOperationDelegate.storages.valueAt(PathOperationDelegate.storages.indexOfKey(segments[position].toUri().path)))
            else -> segments[position].toUri().lastPathSegment
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
            else -> pathOperationDelegate.replaceStoragePath(path)
        }
        val list: MutableList<String> = mutableListOf()
        if (isOtg) list.add(otgDevice)

        //list of all the path chunks
        val pathParts = string.split('/').filter { it.isNotEmpty() }
        for (index in pathParts.indices) {
            //start creating the Uri
            val currentPathUri = Uri.Builder().scheme(uri.scheme).encodedAuthority(uri.authority)
            //append all the previous paths and the current one
            for (i in 0..index) pathOperationDelegate.appendPathToUri(pathParts[i], currentPathUri)
            list.add(currentPathUri.toString())
        }
        if (BuildConfig.DEBUG) list.forEach { Log.d(this::class.java.simpleName, "Added in breadcrumb: $it") }

        if (browser.showRoot()) list.add(0, browserTitle)
        return list
    }



}

interface PathAdapterListener {
    fun backTo(tag: String)
    fun currentContext(): Context
    fun showRoot(): Boolean
    fun getPathOperationDelegate(): IPathOperationDelegate
}