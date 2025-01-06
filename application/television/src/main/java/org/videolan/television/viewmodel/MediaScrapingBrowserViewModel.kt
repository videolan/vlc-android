package org.videolan.television.viewmodel

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.moviepedia.database.models.MediaMetadataType
import org.videolan.moviepedia.database.models.MediaMetadataWithImages
import org.videolan.moviepedia.provider.MediaScrapingMovieProvider
import org.videolan.resources.HEADER_TV_SHOW
import org.videolan.vlc.viewmodels.CallBackDelegate
import org.videolan.vlc.viewmodels.ICallBackHandler
import org.videolan.vlc.viewmodels.SortableModel
import org.videolan.vlc.viewmodels.tv.TvBrowserModel

class MediaScrapingBrowserViewModel(context: Context, val category: Long) : SortableModel(context), TvBrowserModel<MediaMetadataWithImages>,
    ICallBackHandler by CallBackDelegate() {

    init {
        @Suppress("LeakingThis")
        viewModelScope.registerCallBacks { if (Medialibrary.getInstance().isStarted) refresh() }
    }

    override fun restore() {
    }

    override fun filter(query: String?) {
    }

    override fun refresh() {
        provider.refresh()
    }

    override fun isEmpty() = provider.pagedList.value?.isEmpty() != false

    override var currentItem: MediaMetadataWithImages? = null

    override var nbColumns = 0

    override val provider =
        MediaScrapingMovieProvider(context, if (category == HEADER_TV_SHOW) MediaMetadataType.TV_SHOW else MediaMetadataType.MOVIE)

    override fun sort(sort: Int) {
        provider.sort(sort)
    }

    override fun canSortByReleaseDate() = true
    //todo moviepedia add more sort options. See [MoviepediaProvider.sort] and [MovieDataSourceFactory] for sort implementation and [ModelsHelper.getHeaderMoviepedia] for header implementation

    class Factory(private val context: Context, private val category: Long) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MediaScrapingBrowserViewModel(context.applicationContext, category) as T
        }
    }
}

fun Fragment.getMoviepediaBrowserModel(category: Long) =
    ViewModelProvider(requireActivity(), MediaScrapingBrowserViewModel.Factory(requireContext(), category)).get(
        MediaScrapingBrowserViewModel::class.java
    )
