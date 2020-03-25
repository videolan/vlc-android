package org.videolan.vlc.viewmodels

import android.content.Context
import android.net.Uri
import androidx.databinding.Observable
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.lifecycle.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.FileUtils
import org.videolan.resources.util.NoConnectivityException
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.resources.opensubtitles.OpenSubtitle
import org.videolan.vlc.gui.dialogs.State
import org.videolan.vlc.gui.dialogs.SubtitleItem
import org.videolan.vlc.repository.ExternalSubRepository
import org.videolan.resources.opensubtitles.OpenSubtitleRepository
import org.videolan.tools.CoroutineContextProvider
import org.videolan.tools.putSingle
import java.io.File
import java.util.*

private const val LAST_USED_LANGUAGES = "last_used_subtitles"

class SubtitlesModel(private val context: Context, private val mediaUri: Uri, val coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()): ViewModel() {
    val observableSearchName = ObservableField<String>()
    val observableSearchEpisode = ObservableField<String>()
    val observableSearchSeason = ObservableField<String>()
    val observableSearchLanguage = ObservableField<List<String>>()
    private var previousSearchLanguage: List<String>? = null
    val manualSearchEnabled = ObservableBoolean(false)

    val isApiLoading = ObservableBoolean(false)
    val observableMessage = ObservableField<String>()

    private val apiResultLiveData: MutableLiveData<List<OpenSubtitle>> = MutableLiveData()
    private val downloadedLiveData = Transformations.map(ExternalSubRepository.getInstance(context).getDownloadedSubtitles(mediaUri)) {
        it.map { SubtitleItem(it.idSubtitle, mediaUri, it.subLanguageID, it.movieReleaseName, State.Downloaded, "") }
    }

    private val downloadingLiveData =  ExternalSubRepository.getInstance(context).downloadingSubtitles

    val result: MediatorLiveData<List<SubtitleItem>> = MediatorLiveData()
    val history: MediatorLiveData<List<SubtitleItem>> = MediatorLiveData()

    private var searchJob: Job? = null
    init {
        observableSearchLanguage.addOnPropertyChangedCallback(object: Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                if (observableSearchLanguage.get() != previousSearchLanguage) {
                    previousSearchLanguage = observableSearchLanguage.get()
                    saveLastUsedLanguage(observableSearchLanguage.get() ?: listOf())
                    search(!manualSearchEnabled.get())
                }
            }
        })

        history.apply {
            addSource(downloadedLiveData) {
                viewModelScope.launch {
                    value = merge(it, downloadingLiveData.value?.values?.filter { it.mediaUri == mediaUri })
                }
            }

            addSource(downloadingLiveData) {
                viewModelScope.launch {
                    value = merge(downloadedLiveData.value, it?.values?.filter { it.mediaUri == mediaUri })
                }
            }
        }

        result.apply {
            addSource(apiResultLiveData) {
                viewModelScope.launch {
                    value = updateListState(it, history.value)
                }

            }

            addSource(history) {
                viewModelScope.launch {
                    value = updateListState(apiResultLiveData.value, it)
                }
            }
        }
    }

    private suspend fun merge(downloadedResult: List<SubtitleItem>?, downloadingResult: List<SubtitleItem>?): List<SubtitleItem> = withContext(coroutineContextProvider.Default) {
        downloadedResult.orEmpty() + downloadingResult?.toList().orEmpty()
    }

    private suspend fun updateListState(apiResultLiveData: List<OpenSubtitle>?, history: List<SubtitleItem>?): MutableList<SubtitleItem>  = withContext(coroutineContextProvider.Default) {
        val list = mutableListOf<SubtitleItem>()
        apiResultLiveData?.forEach { openSubtitle ->
            val exist = history?.find { it.idSubtitle == openSubtitle.idSubtitle }
            val state = exist?.state ?: State.NotDownloaded
            list.add(SubtitleItem(openSubtitle.idSubtitle, mediaUri, openSubtitle.subLanguageID, openSubtitle.movieReleaseName, state, openSubtitle.zipDownloadLink))
        }
        list
    }

    fun onCheckedChanged(isChecked: Boolean) {
        if (manualSearchEnabled.get() == isChecked) return
        manualSearchEnabled.set(isChecked)
        isApiLoading.set(false)
        apiResultLiveData.postValue(listOf())
        searchJob?.cancel()

        observableMessage.set("")
        if (!isChecked) search(true)
    }

    private suspend fun getSubtitleByName(name: String, episode: Int?, season: Int?, languageIds: List<String>?): List<OpenSubtitle> {
        return OpenSubtitleRepository.getInstance().queryWithName(name ,episode, season, languageIds)
    }

    private suspend fun getSubtitleByHash(movieByteSize: Long, movieHash: String?, languageIds: List<String>?): List<OpenSubtitle> {
        return OpenSubtitleRepository.getInstance().queryWithHash(movieByteSize, movieHash, languageIds)
    }

    fun onRefresh() {
        if (manualSearchEnabled.get() && observableSearchName.get().isNullOrEmpty()) {
            isApiLoading.set(false)
            // As it's already false we need to notify it to
            // disable refreshing animation
            isApiLoading.notifyChange()
            return
        }

        search(!manualSearchEnabled.get())
    }

    fun search(byHash: Boolean) {
        searchJob?.cancel()
        isApiLoading.set(true)
        observableMessage.set("")
        apiResultLiveData.postValue(listOf())

        searchJob = viewModelScope.launch {
            try {
                val subs = if (byHash) {
                    withContext(coroutineContextProvider.IO) {
                        val videoFile = File(mediaUri.path)
                        val hash = FileUtils.computeHash(videoFile)
                        val fileLength = videoFile.length()

                        getSubtitleByHash(fileLength, hash, observableSearchLanguage.get())
                    }
                } else {
                    observableSearchName.get()?.let {
                        getSubtitleByName(it, observableSearchEpisode.get()?.toInt(), observableSearchSeason.get()?.toInt(), observableSearchLanguage.get())
                    } ?: listOf()
                }
                if (isActive) apiResultLiveData.postValue(subs)
                if (subs.isEmpty()) observableMessage.set(context.getString(R.string.no_result))
            } catch (e: Exception) {
                if (e is NoConnectivityException)
                    observableMessage.set(context.getString(R.string.no_internet_connection))
                else
                    observableMessage.set(context.getString(R.string.some_error_occurred))
            } finally {
                isApiLoading.set(false)
            }
        }
    }

    fun deleteSubtitle(mediaPath: String, idSubtitle: String) {
        ExternalSubRepository.getInstance(context).deleteSubtitle(mediaPath, idSubtitle)
    }

    fun getLastUsedLanguage() : List<String> {
        val language = try {
            Locale.getDefault().isO3Language
        } catch (e: MissingResourceException) {
            "eng"
        }
        return Settings.getInstance(context).getStringSet(LAST_USED_LANGUAGES, setOf(language))?.map { it.getCompliantLanguageID() } ?: emptyList()
    }

    fun saveLastUsedLanguage(lastUsedLanguages: List<String>) = Settings.getInstance(context).putSingle(LAST_USED_LANGUAGES, lastUsedLanguages)

    class Factory(private val context: Context, private val mediaUri: Uri): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SubtitlesModel(context.applicationContext, mediaUri) as T
        }
    }

    // Locale ID Control, because of OpenSubtitle support of ISO639-2 codes
    // e.g. French ID can be 'fra' or 'fre', OpenSubtitles considers 'fre' but Android Java Locale provides 'fra'
    private fun String.getCompliantLanguageID() = when(this) {
        "fra" -> "fre"
        "deu" -> "ger"
        "zho" -> "chi"
        "ces" -> "cze"
        "fas" -> "per"
        "nld" -> "dut"
        "ron" -> "rum"
        "slk" -> "slo"
        else  -> this
    }
}
