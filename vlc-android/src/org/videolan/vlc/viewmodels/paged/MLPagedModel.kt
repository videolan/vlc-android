package org.videolan.vlc.viewmodels.paged

import android.arch.lifecycle.MutableLiveData
import android.arch.paging.DataSource
import android.arch.paging.LivePagedListBuilder
import android.arch.paging.PagedList
import android.arch.paging.PositionalDataSource
import android.content.Context
import kotlinx.coroutines.experimental.launch
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.viewmodels.SortableModel

abstract class MLPagedModel<T : MediaLibraryItem>(context: Context) : SortableModel(context), Medialibrary.OnMedialibraryReadyListener {
    protected val medialibrary = Medialibrary.getInstance()
    protected var filter : String? = null
    val loading = MutableLiveData<Boolean>().apply { value = true }

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
        launch { refresh() }
    }

    override fun onMedialibraryIdle() {
        launch { refresh() }
    }

    override fun onCleared() {
        medialibrary.removeOnMedialibraryReadyListener(this)
        super.onCleared()
    }

    abstract fun getTotalCount() : Int
    abstract fun getPage(loadSize: Int, startposition: Int) : Array<T>
    abstract fun getAll() : Array<T>

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
        loading.postValue(true)
        pagedList.value?.dataSource?.invalidate()
        return true
    }

    inner class MLDataSource : PositionalDataSource<T>() {

        override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<T>) {
            val count = getTotalCount()
            callback.onResult(getPage(params.requestedLoadSize, params.requestedStartPosition).toList(), params.requestedStartPosition, count)
            loading.postValue(false)
        }
        override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<T>) {
            callback.onResult(getPage(params.loadSize, params.startPosition).toList())
        }
    }

    inner class MLDatasourceFactory : DataSource.Factory<Int, T>() {
        override fun create() = MLDataSource()
    }
}
