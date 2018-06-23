package org.videolan.vlc.gui.browser

import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import org.videolan.vlc.R
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.FileUtils

private val EXTERNAL_PUBLIC_DIRECTORY_TAG = FileUtils.getFileNameFromPath(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
class PathAdapter(val browser: BaseBrowserFragment, path: String) : RecyclerView.Adapter<PathAdapter.ViewHolder>() {

    init {
        Log.d("PathAdapter", path)
    }

    private val memoryTitle = browser.getString(R.string.internal_memory)

    private val segments = mutableListOf("browser").apply { addAll(path.replace(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, memoryTitle).split('/').filter { !it.isEmpty() } ) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.browser_path_item, parent, false) as TextView)
    }

    override fun getItemCount() = segments.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.root.text = segments[position]
    }

    inner class ViewHolder(val root : TextView) : RecyclerView.ViewHolder(root) {
        init {
            root.setOnClickListener {
                browser.backTo(adapterPosition.let { when (it) {
                    0 -> "root"
                    1 -> if (segments[1] == memoryTitle) EXTERNAL_PUBLIC_DIRECTORY_TAG else segments[1]
                    else -> segments[it]
                }})
            }
        }
    }
}