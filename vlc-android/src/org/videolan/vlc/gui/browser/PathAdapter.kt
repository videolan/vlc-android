package org.videolan.vlc.gui.browser

import android.net.Uri
import androidx.collection.SimpleArrayMap
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.util.AndroidDevices

private val storages = androidx.collection.SimpleArrayMap<String, String>()

class PathAdapter(val browser: BaseBrowserFragment, media: MediaWrapper) : androidx.recyclerview.widget.RecyclerView.Adapter<PathAdapter.ViewHolder>() {

    init {
        if (media.hasStateFlags(MediaLibraryItem.FLAG_STORAGE)) storages.put(Uri.decode(media.uri.path), media.title)
    }

    private val memoryTitle = browser.getString(R.string.internal_memory)
    private val browserTitle = browser.getString(R.string.browser)
    private val otgDevice = browser.getString(R.string.otg_device_title)
    private val segments = prepareSegments(Uri.decode(media.uri.path))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.browser_path_item, parent, false) as TextView)
    }

    override fun getItemCount() = segments.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.root.text = segments[position]
    }

    inner class ViewHolder(val root : TextView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(root) {
        init {
            root.setOnClickListener {
                browser.backTo(adapterPosition.let { when (it) {
                    0 -> "root"
                    else -> segments[it]
                }})
            }
        }
    }

    private fun prepareSegments(path: String) : MutableList<String> {
        val isOtg = path.startsWith("/tree/")
        val string = when {
            isOtg -> if (path.endsWith(':')) "" else path.substringAfterLast(':')
            path.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY) -> path.replace(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, memoryTitle)
            else -> replaceStoragePath(path)
        }
        val list = mutableListOf(browserTitle)
        if (isOtg) list.add(otgDevice)
        list.addAll(string.split('/').filter { !it.isEmpty() })
        return list
    }

    private fun replaceStoragePath(path: String): String {
        if (storages.size() > 0) for (i in 0..storages.size()) if (path.startsWith(storages.keyAt(i))) return path.replace(storages.keyAt(i), storages.valueAt(i))
        return path
    }
}