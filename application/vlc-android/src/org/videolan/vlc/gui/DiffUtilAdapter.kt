package org.videolan.vlc.gui

import androidx.annotation.MainThread
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor

abstract class DiffUtilAdapter<D, VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>(), CoroutineScope {
    override val coroutineContext = Dispatchers.Main.immediate + SupervisorJob()

    var dataset: List<D> = listOf()
    private set
    private val diffCallback by lazy(LazyThreadSafetyMode.NONE) { createCB() }

    @OptIn(ObsoleteCoroutinesApi::class)
    private val updateActor = actor<List<D>>(capacity = Channel.CONFLATED) {
        for (list in channel) internalUpdate(list)
    }
    protected open fun onUpdateFinished() {}

    @MainThread
    fun update (list: List<D>) {
        updateActor.trySend(list)
    }

    @MainThread
    private suspend fun internalUpdate(list: List<D>) {
        val (finalList, result) = withContext(Dispatchers.Default) {
            val finalList = prepareList(list)
            val result = DiffUtil.calculateDiff(diffCallback.apply { update(dataset, finalList) }, detectMoves())
            Pair(finalList, result)
        }
        dataset = finalList
        result.dispatchUpdatesTo(this@DiffUtilAdapter)
        onUpdateFinished()
    }

    protected open fun prepareList(list: List<D>) : List<D> = list.toList()

    @MainThread
    fun isEmpty() = dataset.isEmpty()

    open fun getItemByPosition(position: Int) = dataset[position]

    override fun getItemCount() = dataset.size

    protected open fun detectMoves() = false

    protected open fun createCB() = DiffCallback<D>()

    open class DiffCallback<D> : DiffUtil.Callback() {
        lateinit var oldList: List<D>
        lateinit var newList: List<D>

        fun update(oldList: List<D>, newList: List<D>) {
            this.oldList = oldList
            this.newList = newList
        }

        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areContentsTheSame(oldItemPosition : Int, newItemPosition : Int) = true

        override fun areItemsTheSame(oldItemPosition : Int, newItemPosition : Int) = oldList[oldItemPosition] == newList[newItemPosition]
    }
}