package org.videolan.vlc.viewmodels

import android.arch.lifecycle.*
import android.content.Context
import android.databinding.Observable
import android.databinding.ObservableField
import android.databinding.ObservableBoolean
import kotlinx.coroutines.experimental.*
import org.videolan.vlc.api.NoConnectivityException
import org.videolan.vlc.api.OpenSubtitle
import org.videolan.vlc.gui.dialogs.State
import org.videolan.vlc.gui.dialogs.SubtitleItem
import org.videolan.vlc.repository.ExternalSubRepository
import org.videolan.vlc.repository.OpenSubtitleRepository
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.R
import org.videolan.vlc.util.Settings
import java.io.File
import java.util.*

private const val LAST_USED_LANGUAGE = "last_used_subtitle"

class SubtitlesModel(private val context: Context, private val mediaPath: String): ScopedModel() {
    val observableSearchName = ObservableField<String>()
    val observableSearchEpisode = ObservableField<String>()
    val observableSearchSeason = ObservableField<String>()
    val observableSearchLanguage = ObservableField<String>()
    val manualSearchEnabled = ObservableBoolean(false)

    val isApiLoading = ObservableBoolean(false)
    val observableMessage = ObservableField<String>()

    private val apiResultLiveData: MutableLiveData<List<OpenSubtitle>> = MutableLiveData()
    val downloadedLiveData = Transformations.map(ExternalSubRepository.getInstance(context).getDownloadedSubtitles(mediaPath)) {
        it.map { SubtitleItem(it.idSubtitle, mediaPath, it.subLanguageID, it.movieReleaseName, State.Downloaded, "") }
    }

    val downloadingLiveData =  ExternalSubRepository.getInstance(context).downloadingSubtitles

    val result: MediatorLiveData<List<SubtitleItem>> = MediatorLiveData()
    val history: MediatorLiveData<List<SubtitleItem>> = MediatorLiveData()

    var searchJob: Job? = null
    init {
        observableSearchLanguage.addOnPropertyChangedCallback(object: Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                saveLastUsedLanguage(observableSearchLanguage.get() ?: "")
                search(true)
            }

        })

        history.apply {
            addSource(downloadedLiveData) {
                launch {
                    value = merge(it, downloadingLiveData.value?.values?.filter { it.mediaPath == mediaPath })
                }
            }

            addSource(downloadingLiveData) {
                launch {
                    value = merge(downloadedLiveData.value, it?.values?.filter { it.mediaPath == mediaPath })
                }
            }
        }

        result.apply {
            addSource(apiResultLiveData) {
                launch {
                    value = updateListState(it, history.value)
                }

            }

            addSource(history) {
                launch {
                    value = updateListState(apiResultLiveData.value, it)
                }
            }
        }
    }

    private suspend fun merge(downloadedResult: List<SubtitleItem>?, downloadingResult: List<SubtitleItem>?): List<SubtitleItem> = withContext(Dispatchers.Default) {
        downloadedResult.orEmpty() + downloadingResult?.toList().orEmpty()
    }

    private suspend fun updateListState(apiResultLiveData: List<OpenSubtitle>?, history: List<SubtitleItem>?): MutableList<SubtitleItem>  = withContext(Dispatchers.Default) {
        val list = mutableListOf<SubtitleItem>()
        apiResultLiveData?.forEach { openSubtitle ->
            val exist = history?.find { it.idSubtitle == openSubtitle.idSubtitle }
            val state = exist?.state ?: State.NotDownloaded
            list.add(SubtitleItem(openSubtitle.idSubtitle, mediaPath, openSubtitle.subLanguageID, openSubtitle.movieReleaseName, state, openSubtitle.zipDownloadLink))
        }
        list
    }

    fun onCheckedChanged(isChecked: Boolean) {
        manualSearchEnabled.set(isChecked)
        isApiLoading.set(false)
        apiResultLiveData.postValue(listOf())
        searchJob?.cancel()

        if (!isChecked) search(true)
    }

    suspend fun getSubtitleByName(name: String, episode: Int?, season: Int?, languageId: String?): List<OpenSubtitle> {
        return OpenSubtitleRepository.getInstance().queryWithName(name ,episode, season, languageId)
    }

    suspend fun getSubtitleByHash(movieByteSize: Long, movieHash: String, languageId: String?): List<OpenSubtitle> {
        return OpenSubtitleRepository.getInstance().queryWithHash(movieByteSize, movieHash, languageId)
    }

    fun onRefresh() {
        search(!manualSearchEnabled.get())
    }

    fun search(byHash: Boolean) {
        searchJob?.cancel()
        isApiLoading.set(true)
        observableMessage.set("")
        apiResultLiveData.postValue(listOf())

        searchJob = launch {
            try {
                val subs = if (byHash) {
                    withContext(Dispatchers.IO) {
                        val videoFile = File(mediaPath)
                        val hash = FileUtils.computeHash(videoFile)
                        val fileLength = videoFile.length()

                        getSubtitleByHash(fileLength, hash, observableSearchLanguage.get())
                    }

                } else {
                    observableSearchName.get()?.let {
                        getSubtitleByName(it, observableSearchEpisode.get()?.toInt(), observableSearchSeason.get()?.toInt(), observableSearchLanguage.get())
                    } ?: listOf()
                }
                if (isActive)
                    apiResultLiveData.postValue(subs)
                if (subs.isEmpty())
                    observableMessage.set(context.getString(R.string.no_result))

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

    fun getLastUsedLanguage() = Settings.getInstance(context).getString(LAST_USED_LANGUAGE, Locale.getDefault().isO3Language).getCompliantLanguageID()

    fun saveLastUsedLanguage(lastUsedLang: String) = Settings.getInstance(context).edit().putString(LAST_USED_LANGUAGE, lastUsedLang).apply()

    class Factory(private val context: Context, private val mediaPath: String): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SubtitlesModel(context.applicationContext, mediaPath) as T
        }
    }

    // Locale ID Control, because of OpenSubtitle support of ISO639-2 codes
    // e.g. French ID can be 'fra' or 'fre', OpenSubtitles considers 'fre' but Android Java Locale provides 'fra'
    fun String.getCompliantLanguageID(): String {
        return when(this) {
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
}
