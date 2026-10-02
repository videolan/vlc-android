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
    private val pageLoader: suspend (Int) -> PageResponse<Discourse>,
    private val audioPageLoader: suspend (Int) -> PageResponse<DiscourseAudio>,
    private val trackLoader: suspend (String) -> List<DiscourseAudio>
) : ViewModel() {
    sealed class State {
        object Idle : State()
        object Loading : State()
        data class Catalogue(val discourses: List<Discourse>, val languages: Map<String, List<String>>) : State()
        data class Detail(val discourse: Discourse, val tracks: List<DiscourseAudio>? = null, val error: Boolean = false) : State()
        object Error : State()
    }

    private val mutableState = MutableLiveData<State>(State.Idle)
    val state: LiveData<State> = mutableState
    private var loadJob: Job? = null
    private var cachedCatalogue: State.Catalogue? = null

    fun load() {
        if (mutableState.value !is State.Idle && mutableState.value !is State.Error || loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            mutableState.value = State.Loading
            try {
                val result = mutableListOf<Discourse>()
                var page = 1
                var totalPages: Int
                do {
                    val response = pageLoader(page++)
                    result += response.data
                    totalPages = response.meta.totalPages
                } while (page <= totalPages)
                val languages = mutableMapOf<String, MutableSet<String>>()
                page = 1
                do {
                    val response = audioPageLoader(page++)
                    response.data.forEach { audio -> languages.getOrPut(audio.discourseId) { mutableSetOf() }.add(audio.language) }
                    totalPages = response.meta.totalPages
                } while (page <= totalPages)
                cachedCatalogue = State.Catalogue(
                    result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }),
                    languages.mapValues { it.value.sorted() }
                )
                mutableState.value = cachedCatalogue
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = State.Error
            }
        }
    }

    fun select(discourse: Discourse) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            mutableState.value = State.Detail(discourse)
            try {
                mutableState.value = State.Detail(discourse, trackLoader(discourse.id))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = State.Detail(discourse, error = true)
            }
        }
    }

    fun retryDetail() = (mutableState.value as? State.Detail)?.discourse?.let(::select)

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
                { repository.getDiscourses(page = it) },
                { repository.getDiscourseAudios(page = it) },
                { repository.getDiscourseAudios(it).data }
            ) as T
        }
    }
}
