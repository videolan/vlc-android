package org.videolan.vlc.interfaces


interface Filterable {
    fun getFilterQuery(): String?
    fun enableSearchOption(): Boolean
    fun filter(query: String)
    fun restoreList()
    fun setSearchVisibility(visible: Boolean)

    fun allowedToExpand(): Boolean
}
