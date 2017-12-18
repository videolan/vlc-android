package org.videolan.vlc.gui

import android.support.annotation.WorkerThread
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newSingleThreadContext
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.util.MediaItemDiffCallback
import java.util.*

abstract class DiffUtilAdapter<D : MediaLibraryItem, VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {

    protected var dataset: List<D> = listOf()
    @Volatile private var last = dataset
    private val diffCallback by lazy(LazyThreadSafetyMode.NONE) { createCB() }
    private val updateActor = actor<List<D>>(newSingleThreadContext("vlc-updater"), capacity = Channel.CONFLATED) {
        for (list in channel) internalUpdate(list)
    }
    protected abstract fun onUpdateFinished()

    fun update (list: List<D>) {
        last = list
        updateActor.offer(list)
    }

    @WorkerThread
    private suspend fun internalUpdate(list: List<D>) {
        val finalList = prepareList(list)
        val result = DiffUtil.calculateDiff(diffCallback.apply { this.update(dataset, finalList) }, detectMoves())
        launch(UI) {
            dataset = finalList
            result.dispatchUpdatesTo(this@DiffUtilAdapter)
            if (!updateActor.isFull) onUpdateFinished()
        }.join()
    }

    open protected fun prepareList(list: List<D>) : List<D> = ArrayList(list)

    fun peekLast() = last

    fun hasPendingUpdates() = last !== dataset

    open protected fun detectMoves() = false

    open protected fun createCB() = MediaItemDiffCallback<D>()
}