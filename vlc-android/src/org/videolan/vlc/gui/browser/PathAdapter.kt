package org.videolan.vlc.gui.browser

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import org.videolan.vlc.R
import org.videolan.vlc.util.AndroidDevices

class PathAdapter(val browser: BaseBrowserFragment, path: String) : RecyclerView.Adapter<PathAdapter.ViewHolder>() {

    private val memoryTitle = browser.getString(R.string.internal_memory)
    private val browserTitle = browser.getString(R.string.browser)
    private val otgDevice = browser.getString(R.string.otg_device_title)

    private val segments = prepareSegments(path)

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
                    else -> segments[it]
                }})
            }
        }
    }

    private fun prepareSegments(path: String) : MutableList<String>{
        val string = when {
            path.startsWith("/tree/") -> if (path.endsWith(':')) "" else path.substringAfterLast(':')
            else -> path.replace(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, memoryTitle)
        }
        val list = mutableListOf(browserTitle)
        if (path.startsWith("/tree/")) list.add(otgDevice)
        list.addAll(string.split('/').filter { !it.isEmpty() })
        return list
    }
}