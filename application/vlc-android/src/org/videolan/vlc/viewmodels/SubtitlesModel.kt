package org.videolan.vlc.viewmodels

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.text.Html
import android.text.Spanned
import android.util.Log
import androidx.core.text.HtmlCompat
import androidx.core.text.toSpanned
import androidx.databinding.Observable
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import main.java.org.videolan.resources.opensubtitles.OpenSubtitlesLimit
import main.java.org.videolan.resources.opensubtitles.OpenSubtitlesUser
import main.java.org.videolan.resources.opensubtitles.OpenSubtitlesUtils
import org.videolan.resources.AppContextProvider
import org.videolan.resources.opensubtitles.Data
import org.videolan.resources.opensubtitles.OpenSubV1
import org.videolan.resources.opensubtitles.OpenSubtitleRepository
import org.videolan.resources.util.NoConnectivityException
import org.videolan.tools.CoroutineContextProvider
import org.videolan.tools.FileUtils
import org.videolan.tools.Settings
import org.videolan.tools.getContextWithLocale
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.State
import org.videolan.vlc.gui.dialogs.SubtitleItem
import org.videolan.vlc.repository.ExternalSubRepository
import org.videolan.vlc.util.TextUtils
import java.io.File
import java.util.Locale
import java.util.MissingResourceException
import kotlin.collections.HashMap
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.emptyList
import kotlin.collections.filter
import kotlin.collections.find
import kotlin.collections.first
import kotlin.collections.forEach
import kotlin.collections.get
import kotlin.collections.indices
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mutableListOf
import kotlin.collections.orEmpty
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.collections.setOf
import kotlin.collections.toList

private const val LAST_USED_LANGUAGES = "last_used_subtitles"

class SubtitlesModel(context: Context, private val mediaUri: Uri, private val name:String, val coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()) : AndroidViewModel(context.applicationContext as Application) {
    val observableSearchName = ObservableField<String>()
    val observableSearchEpisode = ObservableField<String>()
    val observableSearchSeason = ObservableField<String>()
    val observableSearchLanguage = ObservableField<List<String>>()
    val observableSearchHearingImpaired = ObservableField<Boolean>()
    val observableInEditMode = ObservableField<Boolean>()
    val observableUser = ObservableField<OpenSubtitlesUser>()
    val observableLimit = ObservableField<OpenSubtitlesLimit>()
    private var previousSearchLanguage: List<String>? = null
    val manualSearchEnabled = ObservableBoolean(false)

    val isApiLoading: MediatorLiveData<Boolean> = MediatorLiveData()
    val observableMessage = ObservableField<String>()
    val observableError = ObservableField<Boolean>()
    val observableHistoryEmpty = ObservableField<String>()
    val observableResultDescription = ObservableField<Spanned>()
    val observableResultDescriptionTalkback = ObservableField<String>()

    private var lastUsername: String = ""
    private var lastPassword: String = ""

    val oldLanguagesMigration by lazy {
        val newLangCodes =  context.resources.getStringArray(R.array.language_values)
        val oldLangCodes =  context.resources.getStringArray(R.array.old_language_values)
        val newLangEntries =  context.resources.getStringArray(R.array.language_entries)
        val oldLangEntries =  context.resources.getStringArray(R.array.old_language_entries)
        val mapping = HashMap<String, String>()
        for (i in oldLangCodes.indices) {
            for (j in newLangCodes.indices) {
                if (newLangEntries[j] == oldLangEntries[i]) {
                    mapping[oldLangCodes[i]] = newLangCodes[j]
                    break
                }
            }
        }
        mapping
    }

    private fun getContext() =
        getApplication<Application>().getContextWithLocale(AppContextProvider.locale)

    private val apiResultLiveData: MutableLiveData<List<Data>> = MutableLiveData()
    private val downloadedLiveData = ExternalSubRepository.getInstance(context).getDownloadedSubtitles(mediaUri).map { list ->
        list.map { SubtitleItem(it.idSubtitle, -1, mediaUri, it.subLanguageID, it.movieReleaseName, State.Downloaded, "", it.hearingImpaired, 0F, 0) }
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

    private suspend fun updateListState(apiResultLiveData: List<Data>?, history: List<SubtitleItem>?): MutableList<SubtitleItem> = withContext(coroutineContextProvider.Default) {
        val list = mutableListOf<SubtitleItem>()
        apiResultLiveData?.forEach { openSubtitle ->
            val exist = history?.find { it.idSubtitle == openSubtitle.attributes.subtitleId }
            val state = exist?.state ?: State.NotDownloaded
            if (openSubtitle.attributes.files.isNotEmpty()) {
                list.add(
                    SubtitleItem(
                        openSubtitle.attributes.subtitleId,
                        openSubtitle.attributes.files.first().fileId,
                        mediaUri,
                        openSubtitle.attributes.language,
                        openSubtitle.attributes.featureDetails.movieName,
                        state,
                        "",
                        openSubtitle.attributes.hearingImpaired,
                        openSubtitle.attributes.ratings,
                        openSubtitle.attributes.downloadCount
                    )
                )
            }
        }
        list
    }

    private suspend fun getSubtitleByName(name: String, episode: Int?, season: Int?, languageIds: List<String>?, hearingImpaired: Boolean): OpenSubV1 {
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Getting subs by name with $name")
        val builder = StringBuilder(getContext().getString(R.string.sub_result_by_name, "<i>$name</i>"))
        season?.let { builder.append(" ${TextUtils.SEPARATOR} ").append(getContext().getString(R.string.sub_result_by_name_season, "<i>$it</i>")) }
        episode?.let { builder.append(" ${TextUtils.SEPARATOR} ").append(getContext().getString(R.string.sub_result_by_name_episode, "<i>$it</i>")) }
        languageIds?.let { languages -> if (languageIds.isNotEmpty()) builder.append(" ${TextUtils.SEPARATOR} ").append("<i>${languages.joinToString(", "){ it.uppercase()} }</i>") }
        if (hearingImpaired) builder.append(" ${TextUtils.SEPARATOR} ").append(getContext().getString(R.string.sub_result_by_name_hearing_impaired))
        observableResultDescription.set(HtmlCompat.fromHtml(builder.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY))
        val talkbackBuilder = StringBuilder(getContext().getString(R.string.sub_result_by_name, name))
        season?.let { talkbackBuilder.append(". ").append(getContext().getString(R.string.sub_result_by_name_season, "$it")) }
        episode?.let { talkbackBuilder.append(". ").append(getContext().getString(R.string.sub_result_by_name_episode, "$it")) }
        val langEntries = getContext().resources.getStringArray(R.array.language_entries)
        val langValues = getContext().resources.getStringArray(R.array.language_values)
        languageIds?.let { languages -> if (languageIds.isNotEmpty()) talkbackBuilder.append(". ").append(
            languages.joinToString(", "){
                val index = langValues.indexOf(it)
                if (index != -1) langEntries[index] else it
            }) }
        if (hearingImpaired) talkbackBuilder.append(". ").append(getContext().getString(R.string.sub_result_by_name_hearing_impaired))
        observableResultDescriptionTalkback.set(talkbackBuilder.toString())
        manualSearchEnabled.set(true)
        return OpenSubtitleRepository.getInstance().queryWithName(name, episode, season, languageIds, hearingImpaired)
    }

    private suspend fun getSubtitleByHash(movieHash: String?, languageIds: List<String>?, hearingImpaired: Boolean): OpenSubV1 {
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Getting subs by hash with $movieHash")
        manualSearchEnabled.set(false)
        observableResultDescription.set(getContext().getString(R.string.sub_result_by_file).toSpanned())
        return OpenSubtitleRepository.getInstance().queryWithHash(movieHash, languageIds, hearingImpaired)
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
                            val hashSubs = getSubtitleByHash(hash, observableSearchLanguage.get(), observableSearchHearingImpaired.get() ?: false).data
                            // No result for hash. Falling back to name search
                            if (hashSubs.isEmpty()) getSubtitleByName(videoFile.name, null, null, observableSearchLanguage.get(), observableSearchHearingImpaired.get() ?: false).data else hashSubs
                        } else {
                            getSubtitleByName(name, null, null, observableSearchLanguage.get(), observableSearchHearingImpaired.get() ?: false).data
                        }

                    }
                } else {
                    observableSearchName.get()?.let {
                        val episode = try {
                            observableSearchEpisode.get()?.toIntOrNull()
                        } catch (e: NumberFormatException) {
                            null
                        }
                        val season = try {
                            observableSearchSeason.get()?.toIntOrNull()
                        } catch (e: NumberFormatException) {
                            null
                        }
                        getSubtitleByName(it, episode, season, observableSearchLanguage.get(), observableSearchHearingImpaired.get() ?: false).data
                    } ?: listOf()
                }
                if (isActive) apiResultLiveData.postValue(subs)
                if (subs.isEmpty()) {
                    observableMessage.set(getContext().getString(R.string.no_result))
                } else {
                    observableMessage.set("")
                }
                observableError.set(false)
            } catch (e: Exception) {
                Log.e("SubtitlesModel", e.message, e)
                observableError.set(true)
                if (e is NoConnectivityException)
                    observableMessage.set(getContext().getString(R.string.no_internet_connection))
                else
                    observableMessage.set(getContext().getString(R.string.open_subs_download_error))
            } finally {
                isApiLoading.postValue(false)
            }
        }
    }

    fun deleteSubtitle(mediaPath: String, idSubtitle: String) {
        ExternalSubRepository.getInstance(getContext()).deleteSubtitle(mediaPath, idSubtitle)
    }

    fun getLastUsedLanguage(): List<String> {
        val language = try {
            Locale.getDefault().language
        } catch (e: MissingResourceException) {
            "en"
        }
        return Settings.getInstance(getContext()).getStringSet(LAST_USED_LANGUAGES, setOf(language))?.map { if (it.length > 2) migrateFromOld(it) ?: it else it } ?: emptyList()
    }

    fun login(settings: SharedPreferences, username: String, password: String) {
        if (lastPassword == username && lastUsername == password) {
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val call = OpenSubtitleRepository.getInstance().login(username, password)
                    if (call.isSuccessful) {
                        val userResult = call.body()
                        if (userResult != null) {
                            val openSubtitlesUser =
                                OpenSubtitlesUser(true, userResult, username = username)
                            OpenSubtitlesUtils.saveUser(settings, openSubtitlesUser)
                            observableUser.set(openSubtitlesUser)
                            checkUserInfos(settings)
                            return@withContext
                        }
                    }
                    val code = call.code()
                    if (code == 401) {
                        lastPassword = password
                        lastUsername = username
                    }
                    observableUser.set(
                        OpenSubtitlesUser(
                            false,
                            null,
                            errorMessage = if (code == 401) getContext().getString(R.string.login_error) else getContext().getString(
                                R.string.unknown_error
                            )
                        )
                    )
                } catch (e: NoConnectivityException) {
                    observableUser.set(
                        OpenSubtitlesUser(
                            false,
                            null,
                            errorMessage = getContext().getString(R.string.no_internet_connection)
                        )
                    )
                }

            }
        }
    }

    fun checkUserInfos(settings: SharedPreferences) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val callInfo = OpenSubtitleRepository.getInstance().userInfo()
                if (callInfo.isSuccessful) {
                    val userInfo = callInfo.body()
                    if (userInfo != null) {
                        val limit = OpenSubtitlesUtils.getLimit(settings)
                        limit.max = userInfo.data.allowedDownloads
                        limit.requests = userInfo.data.downloadsCount
                        OpenSubtitlesUtils.saveLimit(settings, limit)
                        observableLimit.set(limit)
                    }
                }
            }
        }
    }


    fun logout(settings: SharedPreferences) {
        val user = OpenSubtitlesUser()
        OpenSubtitlesUtils.saveUser(settings, user)
        observableUser.set(user)
        val limit = OpenSubtitlesLimit()
        OpenSubtitlesUtils.saveLimit(settings, limit)
        observableLimit.set(limit)
    }

    private fun migrateFromOld(it: String?): String? {
        return oldLanguagesMigration[it]
    }

    fun saveLastUsedLanguage(lastUsedLanguages: List<String>) = Settings.getInstance(getContext()).putSingle(LAST_USED_LANGUAGES, lastUsedLanguages)
    fun clearCredentials() {
        lastPassword = ""
        lastUsername = ""
    }

    class Factory(private val context: Context, private val mediaUri: Uri, private val name: String) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SubtitlesModel(context.applicationContext, mediaUri, name) as T
        }
    }

}
