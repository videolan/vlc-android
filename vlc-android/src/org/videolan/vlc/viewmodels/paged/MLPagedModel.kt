package org.videolan.vlc.viewmodels.paged

import android.arch.paging.DataSource
import android.arch.paging.LivePagedListBuilder
import android.arch.paging.PagedList
import android.arch.paging.PositionalDataSource
import android.content.Context
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.viewmodels.SortableModel


abstract class MLPagedModel<T : MediaLibraryItem>(context: Context) : SortableModel(context), Medialibrary.OnMedialibraryReadyListener {
    protected val medialibrary = Medialibrary.getInstance()
    protected var filter : String? = null
    private val pagingConfig = PagedList.Config.Builder()
            .setPageSize(100)
            .setPrefetchDistance(100)
            .setEnablePlaceholders(true)
            .build()!!

    val pagedList = LivePagedListBuilder(MLDatasourceFactory(), pagingConfig)
            .build()

    init {
        @Suppress("LeakingThis")
        medialibrary.addOnMedialibraryReadyListener(this)
        if (medialibrary.isStarted) onMedialibraryReady()
    }

    override fun onMedialibraryReady() {
        launch(UI.immediate) { refresh() }
    }

    override fun onMedialibraryIdle() {
        launch(UI.immediate) { refresh() }
    }

    override fun onCleared() {
        super.onCleared()
        medialibrary.removeOnMedialibraryReadyListener(this)
    }

    abstract fun getTotalCount() : Int
    abstract fun getPage(loadSize: Int, startposition: Int) : Array<T>

    override fun sort(sort: Int) {
        if (this.sort != sort) {
            this.sort = sort
            desc = false
        } else desc = !desc
        refresh()
    }

    fun isFiltering() = filter != null

    override fun filter(query: String?) {
        filter = query
        refresh()
    }

    override fun restore() {
        if (filter != null) {
            filter = null
            refresh()
        }
    }

    override fun refresh(): Boolean {
        pagedList.value?.dataSource?.invalidate()
        return true
    }

    fun remove(item: MediaLibraryItem) {} //TODO

    inner class MLDataSource : PositionalDataSource<T>() {
        override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<T>) {
            callback.onResult(getPage(params.loadSize, params.startPosition).toList())
        }

        override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<T>) {
            callback.onResult(getPage(params.requestedLoadSize, params.requestedStartPosition).toList(), params.requestedStartPosition, getTotalCount())
        }
    }

    inner class MLDatasourceFactory : DataSource.Factory<Int, T>() {
        override fun create() = MLDataSource()
    }
}
