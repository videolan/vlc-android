package org.videolan.vlc.util

import androidx.lifecycle.MutableLiveData
import android.util.Log
import org.videolan.vlc.gui.dialogs.SubtitleItem


class LiveDataMap<K, V> : MutableLiveData<MutableMap<K, V>>() {

    private val emptyMap = mutableMapOf<K, V>()

    override fun getValue(): MutableMap<K, V> {
        return super.getValue() ?: emptyMap
    }

    fun clear() {
        value = value.apply { clear() }
    }

    fun add(key: K, item: V) {
        value = value.apply { put(key, item) }
    }

    fun remove(key: K) {
        value = value.apply { remove(key) }
    }

    fun get(key: K): V? = value[key]
}
