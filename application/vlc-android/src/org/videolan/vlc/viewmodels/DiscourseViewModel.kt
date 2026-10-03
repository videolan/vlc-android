package org.videolan.vlc.viewmodels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.videolan.vlc.discourse.Discourse
import org.videolan.vlc.discourse.DiscourseAudio
import org.videolan.vlc.discourse.DiscourseRepository
import org.videolan.vlc.discourse.PageResponse

class DiscourseViewModel(
    private val pageLoader: suspend (Int, Boolean, String?, String?) -> PageResponse<Discourse>,
    initialLanguage: String? = null,
    initialSort: String? = null,
    private val saveFilters: (String?, String?) -> Unit = { _, _ -> },
    private val saveRecentlyPlayed: (Discourse) -> Unit = {},
    private val trackLoader: suspend (String, Boolean) -> List<DiscourseAudio>
) : ViewModel() {
    enum class LanguageFilter(val query: String?) { ALL(null), HINDI("hindi"), ENGLISH("english") }
    enum class SortFilter(val query: String?) { DEFAULT(null), MOST_LIKED("most_liked") }

    sealed class State {
        object Idle : State()
        object Loading : State()
        data class Catalogue(val discourses: List<Discourse>) : State()
        data class Detail(val discourse: Discourse, val tracks: List<DiscourseAudio>? = null, val error: Boolean = false) : State()
        object Error : State()
    }

    private val mutableState = MutableLiveData<State>(State.Idle)
    val state: LiveData<State> = mutableState
    private var loadJob: Job? = null
    private var cachedCatalogue: State.Catalogue? = null
    private var nextPage = 1
    private var totalPages = 1
    private var forceRefreshPages = false
    var languageFilter = LanguageFilter.values().firstOrNull { it.query == initialLanguage } ?: LanguageFilter.ALL
        private set
    var sortFilter = SortFilter.values().firstOrNull { it.query == initialSort } ?: SortFilter.DEFAULT
        private set

    fun load() = load(refresh = false)

    fun refresh() = load(refresh = true)

    private fun load(refresh: Boolean, reset: Boolean = false) {
        val initialLoad = reset || mutableState.value is State.Idle || mutableState.value is State.Error
        if ((!refresh && !initialLoad) || loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            if (initialLoad) mutableState.value = State.Loading
            try {
                val response = pageLoader(1, refresh, languageFilter.query, sortFilter.query)
                forceRefreshPages = refresh
                nextPage = 2
                totalPages = response.meta.totalPages
                cachedCatalogue = State.Catalogue(response.data)
                mutableState.value = cachedCatalogue
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (initialLoad) mutableState.value = State.Error
            }
        }
    }

    fun loadMore() {
        val catalogue = cachedCatalogue ?: return
        if (nextPage > totalPages || loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            try {
                val response = pageLoader(nextPage, forceRefreshPages, languageFilter.query, sortFilter.query)
                nextPage++
                totalPages = response.meta.totalPages
                cachedCatalogue = State.Catalogue(catalogue.discourses + response.data)
                mutableState.value = cachedCatalogue
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Keep the current page visible; reaching the end retries on the next scroll.
            }
        }
    }

    fun setFilters(language: LanguageFilter, sort: SortFilter) {
        if (language == languageFilter && sort == sortFilter) return
        languageFilter = language
        sortFilter = sort
        saveFilters(language.query, sort.query)
        loadJob?.cancel()
        loadJob = null
        cachedCatalogue = null
        load(refresh = true, reset = true)
    }

    fun select(discourse: Discourse, forceRefresh: Boolean = false) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            mutableState.value = State.Detail(discourse)
            try {
                mutableState.value = State.Detail(discourse, trackLoader(discourse.id, forceRefresh))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = State.Detail(discourse, error = true)
            }
        }
    }

    fun openDiscourse(discourse: Discourse) {
        loadJob?.cancel()
        loadJob = null
        select(discourse)
    }

    fun retryDetail() = (mutableState.value as? State.Detail)?.discourse?.let { select(it, forceRefresh = true) }

    fun recordRecentlyPlayed(discourse: Discourse) = saveRecentlyPlayed(discourse)

    fun back() {
        val current = mutableState.value as? State.Detail ?: return
        loadJob?.cancel()
        mutableState.value = cachedCatalogue ?: State.Error
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val repository = DiscourseRepository(context.applicationContext)
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DiscourseViewModel(
                { page, forceRefresh, language, sort ->
                    repository.getDiscourses(page = page, language = language, sort = sort, forceRefresh = forceRefresh)
                },
                repository.catalogueLanguage,
                repository.catalogueSort,
                repository::saveCatalogueFilters,
                repository::recordRecentlyPlayed,
                { id, forceRefresh -> repository.getDiscourseAudios(id, forceRefresh).data }
            ) as T
        }
    }
}
