package org.videolan.resources.util

import androidx.annotation.MainThread
import androidx.collection.SparseArrayCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

typealias HeadersIndex = SparseArrayCompat<String>

open class HeaderProvider {
    val liveHeaders: LiveData<HeadersIndex> = MutableLiveData()
    protected val privateHeaders = HeadersIndex()
    var headers = HeadersIndex()

    init {
        liveHeaders.observeForever { headers = it }
    }

    @MainThread
    fun getSectionforPosition(position: Int): String {
        for (pos in headers.size() - 1 downTo 0) if (position >= headers.keyAt(pos)) return headers.valueAt(pos)
        return ""
    }


    @MainThread
    fun isFirstInSection(position: Int): Boolean {
        return headers.containsKey(position)
    }

    @MainThread
    fun isLastInSection(position: Int): Boolean {
        return headers.containsKey(position + 1)
    }

    @MainThread
    fun getPositionForSection(position: Int): Int {
        for (pos in headers.size() - 1 downTo 0) if (position >= headers.keyAt(pos)) return headers.keyAt(pos)
        return 0
    }

    @MainThread
    fun getPositionForSectionByName(header: String): Int {
        for (pos in headers.size() - 1 downTo 0) if (headers.valueAt(pos) == header) return headers.keyAt(pos)
        return 0
    }

    @MainThread
    fun getHeaderForPostion(position: Int) = headers.get(position)
}