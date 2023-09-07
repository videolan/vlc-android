package org.videolan.vlc.viewmodels

import android.content.Context
import android.net.Uri
import android.text.Html
import android.text.Spanned
import android.util.Log
import androidx.core.text.toSpanned
import androidx.databinding.Observable
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.lifecycle.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.resources.opensubtitles.OpenSubtitle
import org.videolan.resources.opensubtitles.OpenSubtitleRepository
import org.videolan.resources.util.NoConnectivityException
import org.videolan.tools.CoroutineContextProvider
import org.videolan.tools.FileUtils
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.State
import org.videolan.vlc.gui.dialogs.SubtitleItem
import org.videolan.vlc.repository.ExternalSubRepository
import java.io.File
import java.util.*

private const val LAST_USED_LANGUAGES = "last_used_subtitles"

class SubtitlesModel(private val context: Context, private val mediaUri: Uri, private val name:String, val coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()) : ViewModel() {
    val observableSearchName = ObservableField<String>()
    val observableSearchEpisode = ObservableField<String>()
    val observableSearchSeason = ObservableField<String>()
    val observableSearchLanguage = ObservableField<List<String>>()
    private var previousSearchLanguage: List<String>? = null
    val manualSearchEnabled = ObservableBoolean(false)

    val isApiLoading: MediatorLiveData<Boolean> = MediatorLiveData()
    val observableMessage = ObservableField<String>()
    val observableError = ObservableField<Boolean>()
    val observableResultDescription = ObservableField<Spanned>()

    private val apiResultLiveData: MutableLiveData<List<OpenSubtitle>> = MutableLiveData()
    private val downloadedLiveData = ExternalSubRepository.getInstance(context).getDownloadedSubtitles(mediaUri).map { list ->
        list.map { SubtitleItem(it.idSubtitle, mediaUri, it.subLanguageID, it.movieReleaseName, State.Downloaded, "") }
    }

    private val downloadingLiveData = ExternalSubRepository.getInstance(context).downloadingSubtitles

    val result: MediatorLiveData<List<SubtitleItem>> = MediatorLiveData()
    val history: MediatorLiveData<List<SubtitleItem>> = MediatorLiveData()

    private var searchJob: Job? = null
    init {
        observableSearchLanguage.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
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

    private suspend fun updateListState(apiResultLiveData: List<OpenSubtitle>?, history: List<SubtitleItem>?): MutableList<SubtitleItem> = withContext(coroutineContextProvider.Default) {
        val list = mutableListOf<SubtitleItem>()
        apiResultLiveData?.forEach { openSubtitle ->
            val exist = history?.find { it.idSubtitle == openSubtitle.idSubtitle }
            val state = exist?.state ?: State.NotDownloaded
            list.add(SubtitleItem(openSubtitle.idSubtitle, mediaUri, openSubtitle.subLanguageID, openSubtitle.movieReleaseName, state, openSubtitle.zipDownloadLink))
        }
        list
    }

    private suspend fun getSubtitleByName(name: String, episode: Int?, season: Int?, languageIds: List<String>?): List<OpenSubtitle> {
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Getting subs by name with $name")
        val builder = StringBuilder(context.getString(R.string.sub_result_by_name, "<i>$name</i>"))
        season?.let { builder.append(" - ").append(context.getString(R.string.sub_result_by_name_season, "<i>$it</i>")) }
        episode?.let { builder.append(" - ").append(context.getString(R.string.sub_result_by_name_episode, "<i>$it</i>")) }
        observableResultDescription.set(Html.fromHtml(builder.toString()))
        manualSearchEnabled.set(true)
        return OpenSubtitleRepository.getInstance().queryWithName(name, episode, season, languageIds)
    }

    private suspend fun getSubtitleByHash(movieByteSize: Long, movieHash: String?, languageIds: List<String>?): List<OpenSubtitle> {
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Getting subs by hash with $movieHash")
        manualSearchEnabled.set(false)
        observableResultDescription.set(context.getString(R.string.sub_result_by_file).toSpanned())
        return OpenSubtitleRepository.getInstance().queryWithHash(movieByteSize, movieHash, languageIds)
    }

    fun onRefresh() {
        if (manualSearchEnabled.get() && observableSearchName.get().isNullOrEmpty()) {
            isApiLoading.postValue(false)
            return
        }

        search(!manualSearchEnabled.get())
    }

    fun search(byFile: Boolean) {
        searchJob?.cancel()
        isApiLoading.postValue(true)
        observableMessage.set("")
        observableError.set(false)
        apiResultLiveData.postValue(listOf())

        searchJob = viewModelScope.launch {
            try {
                val subs = if (byFile) {
                    withContext(coroutineContextProvider.IO) {
                        val videoFile = File(mediaUri.path)
                        if (videoFile.exists()) {
                            val hash = FileUtils.computeHash(videoFile)
                            val fileLength = videoFile.length()
                            val hashSubs = getSubtitleByHash(fileLength, hash, observableSearchLanguage.get())
                            // No result for hash. Falling back to name search
                            if (hashSubs.isEmpty()) getSubtitleByName(videoFile.name, null, null, observableSearchLanguage.get()) else hashSubs
                        } else {
                            getSubtitleByName(name, null, null, observableSearchLanguage.get())
                        }

                    }
                } else {
                    observableSearchName.get()?.let {
                        getSubtitleByName(it, observableSearchEpisode.get()?.toInt(), observableSearchSeason.get()?.toInt(), observableSearchLanguage.get())
                    } ?: listOf()
                }
                if (isActive) apiResultLiveData.postValue(subs)
                if (subs.isEmpty()) {
                    observableMessage.set(context.getString(R.string.no_result))
                } else {
                    observableMessage.set("")
                }
                observableError.set(false)
            } catch (e: Exception) {
                Log.e("SubtitlesModel", e.message, e)
                observableError.set(true)
                if (e is NoConnectivityException)
                    observableMessage.set(context.getString(R.string.no_internet_connection))
                else
                    observableMessage.set(context.getString(R.string.subs_download_error))
            } finally {
                isApiLoading.postValue(false)
            }
        }
    }

    fun deleteSubtitle(mediaPath: String, idSubtitle: String) {
        ExternalSubRepository.getInstance(context).deleteSubtitle(mediaPath, idSubtitle)
    }

    fun getLastUsedLanguage(): List<String> {
        val language = try {
            Locale.getDefault().isO3Language
        } catch (e: MissingResourceException) {
            "eng"
        }
        return Settings.getInstance(context).getStringSet(LAST_USED_LANGUAGES, setOf(language))?.map { it.getCompliantLanguageID() } ?: emptyList()
    }

    fun saveLastUsedLanguage(lastUsedLanguages: List<String>) = Settings.getInstance(context).putSingle(LAST_USED_LANGUAGES, lastUsedLanguages)

    class Factory(private val context: Context, private val mediaUri: Uri, private val name: String) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SubtitlesModel(context.applicationContext, mediaUri, name) as T
        }
    }

    // Locale ID Control, because of OpenSubtitle support of ISO639-2 codes
    // e.g. French ID can be 'fra' or 'fre', OpenSubtitles considers 'fre' but Android Java Locale provides 'fra'
    private fun String.getCompliantLanguageID() = when (this) {
        "fra" -> "fre"
        "deu" -> "ger"
        "zho" -> "chi"
        "ces" -> "cze"
        "fas" -> "per"
        "nld" -> "dut"
        "ron" -> "rum"
        "slk" -> "slo"
        else -> this
    }
}
