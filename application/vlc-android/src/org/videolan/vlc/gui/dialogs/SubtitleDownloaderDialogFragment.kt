package org.videolan.vlc.gui.dialogs

import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import main.java.org.videolan.resources.opensubtitles.OpenSubtitlesLimit
import main.java.org.videolan.resources.opensubtitles.OpenSubtitlesUtils
import org.videolan.resources.opensubtitles.OpenSubtitleClient
import org.videolan.resources.opensubtitles.OpenSubtitleRepository
import org.videolan.resources.util.parcelable
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.databinding.SubtitleDownloaderDialogBinding
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.deleteSubtitleDialog
import org.videolan.vlc.gui.view.OnItemSelectListener
import org.videolan.vlc.util.VLCDownloadManager
import org.videolan.vlc.util.openLinkIfPossible
import org.videolan.vlc.viewmodels.SubtitlesModel

private const val MEDIA_PATHS = "MEDIA_PATHS"
private const val MEDIA_NAMES = "MEDIA_NAMES"

class SubtitleDownloaderDialogFragment : VLCBottomSheetDialogFragment() {

    override fun getDefaultState(): Int = BottomSheetBehavior.STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = true

    override fun initialFocusedView(): View = binding.movieName

    private lateinit var settings: SharedPreferences
    private lateinit var downloadAdapter: SubtitlesAdapter
    private lateinit var historyAdapter: SubtitlesAdapter
    private lateinit var binding: SubtitleDownloaderDialogBinding
    private lateinit var uris: Uri
    private lateinit var names: String
    private lateinit var viewModel: SubtitlesModel
    private lateinit var toast: Toast

    private var state: SubDownloadDialogState = SubDownloadDialogState.Download
        set(value) {
            field = value
            binding.state = value
        }

    @OptIn(ObsoleteCoroutinesApi::class)
    private val listEventActor = lifecycleScope.actor<SubtitleEvent> {
        for (subtitleEvent in channel) if (isActive) when (subtitleEvent) {
            is SubtitleClick -> when (subtitleEvent.item.state) {
                State.NotDownloaded -> {
                     withContext(Dispatchers.IO) {
                        val downloadLink = OpenSubtitleRepository.getInstance()
                            .getDownloadLink(subtitleEvent.item.fileId)
                         val openSubtitlesLimit = OpenSubtitlesLimit(
                             downloadLink.requests,
                             downloadLink.requests + downloadLink.remaining,
                             downloadLink.resetTimeUtc
                         )
                         OpenSubtitlesUtils.saveLimit(settings, openSubtitlesLimit)
                         viewModel.observableLimit.set(openSubtitlesLimit)
                         subtitleEvent.item.zipDownloadLink = downloadLink.link
                         subtitleEvent.item.fileName = downloadLink.fileName
                    }
                    VLCDownloadManager.download(requireActivity(), subtitleEvent.item, true)
                }
                State.Downloaded -> deleteSubtitleDialog(requireActivity(), DialogInterface.OnClickListener { _, _ ->
                    subtitleEvent.item.mediaUri.path?.let { viewModel.deleteSubtitle(it, subtitleEvent.item.idSubtitle) }
                }
                        , DialogInterface.OnClickListener { _, _ -> })
                else -> return@actor
            }
            is SubtitleLongClick -> {
                @StringRes val message = when (subtitleEvent.item.state) {
                    State.NotDownloaded -> R.string.download_the_selected
                    State.Downloaded -> R.string.delete_the_selected
                    // Todo else -> {"Cancel download"}
                    else -> return@actor
                }
                if (::toast.isInitialized) toast.cancel()
                toast = Toast.makeText(activity, message, Toast.LENGTH_SHORT)
                toast.setGravity(Gravity.TOP, 0, 100)
                toast.show()
            }
        } else channel.close()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uris = savedInstanceState?.parcelable<Uri>(MEDIA_PATHS)
                ?: arguments?.parcelable<Uri>(MEDIA_PATHS) ?: throw IllegalStateException("Missing uri")
        names = savedInstanceState?.getString(MEDIA_NAMES)
                ?: arguments?.getString(MEDIA_NAMES) ?: throw IllegalStateException("Missing name")

        viewModel = ViewModelProvider(requireActivity(), SubtitlesModel.Factory(requireContext(), uris, names))[uris.path!!, SubtitlesModel::class.java]
    }

    override fun onResume() {
        if (viewModel.isApiLoading.value == false) viewModel.onRefresh()
        super.onResume()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        settings = Settings.getInstance(requireContext())
        val user = OpenSubtitlesUtils.getUser(settings)
        val token = user.account?.token
        if (!token.isNullOrEmpty()) OpenSubtitleClient.authorizationToken = token
        OpenSubtitleClient.userDomain = user.account?.baseUrl
        binding = SubtitleDownloaderDialogBinding.inflate(inflater, container, false)
        binding.viewmodel = viewModel
       if (!token.isNullOrEmpty()) viewModel.checkUserInfos(settings)

        binding.subLogin.setOnClickListener {
            state = if (state == SubDownloadDialogState.Login)
                SubDownloadDialogState.Download
            else
                SubDownloadDialogState.Login
        }

        binding.loginButton.setOnClickListener {
            if (viewModel.observableUser.get()?.logged == true) {
                viewModel.logout(settings)
                viewModel.clearCredentials()
            } else {
                viewModel.login(
                    settings,
                    binding.username.text.toString(),
                    binding.password.text.toString()
                )
            }
        }

        binding.registerButton.setOnClickListener {
                requireActivity().openLinkIfPossible("https://www.opensubtitles.com/en/users/sign_up", 512)
        }

        state = SubDownloadDialogState.Download

        downloadAdapter = SubtitlesAdapter(listEventActor)
        binding.subsDownloadList.adapter = downloadAdapter
        binding.subsDownloadList.layoutManager = LinearLayoutManager(activity)

        historyAdapter = SubtitlesAdapter(listEventActor)
        val recyclerView = binding.subsHistoryList
        recyclerView.adapter = historyAdapter
        recyclerView.layoutManager = LinearLayoutManager(activity)



        binding.searchButton.setOnClickListener {
            UiTools.setKeyboardVisibility(it, false)
            viewModel.search(false)
            focusOnView(binding.scrollView)
            state = SubDownloadDialogState.Download
            viewModel.observableInEditMode.set(false)

        }
        binding.cancelButton.setOnClickListener {
            viewModel.observableInEditMode.set(false)
        }

        binding.resetButton.setOnClickListener {
            viewModel.observableInEditMode.set(false)
            viewModel.search(true)
        }

        binding.openSubEdit.setOnClickListener {
            if (viewModel.observableInEditMode.get() == false) {
                //fill form
                val name = when {
                    viewModel.observableSearchName.get().isNullOrBlank() -> names
                    else -> viewModel.observableSearchName.get()
                }
                binding.name.setText(name)
                binding.season.setText(viewModel.observableSearchSeason.get())
                binding.episode.setText(viewModel.observableSearchEpisode.get())
                binding.checkBox.isChecked = viewModel.observableSearchHearingImpaired.get() == true
            }
        viewModel.observableInEditMode.set(viewModel.observableInEditMode.get()?.not())
    }

        binding.subDownloadHistory.setOnClickListener {
            state = if (state == SubDownloadDialogState.History) SubDownloadDialogState.Download else SubDownloadDialogState.History
        }

        binding.languageListSpinner.setOnItemsSelectListener(object : OnItemSelectListener {
            override fun onItemSelect(selectedItems: List<Int>) {
                val selectedLanguages = if (selectedItems.size == binding.languageListSpinner.allValuesOfLanguages.size) listOf()
                else selectedItems.filter { it in binding.languageListSpinner.allValuesOfLanguages.indices }.map { binding.languageListSpinner.allValuesOfLanguages[it] }
                viewModel.observableSearchLanguage.set(selectedLanguages)
            }
        })
        viewModel.observableInEditMode.set(false)
        viewModel.observableSearchHearingImpaired.set(false)
        viewModel.observableUser.set(user)
        viewModel.observableLimit.set(OpenSubtitlesUtils.getLimit(settings))

        binding.retryButton.setOnClickListener {
            viewModel.onRefresh()
        }

        binding.languageListSpinner.setSelection(viewModel.getLastUsedLanguage().map { binding.languageListSpinner.allValuesOfLanguages.indexOf(it) })

        binding.episode.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || event.keyCode == KeyEvent.KEYCODE_ENTER) {
                binding.searchButton.callOnClick()
                 true
            } else false
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.result.observe(viewLifecycleOwner) {
            downloadAdapter.setList(it)
            if (it.isNotEmpty()) focusOnView(binding.scrollView)
        }
        viewModel.isApiLoading.observe(viewLifecycleOwner) {
            binding.subDownloadLoading.visibility = if (it) View.VISIBLE else View.GONE
        }

        viewModel.history.observe(this) {
            historyAdapter.setList(it)
            viewModel.observableHistoryEmpty.set(if (it.isEmpty()) getString(R.string.no_sub_history) else "")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        focusOnView(binding.scrollView)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(MEDIA_PATHS, uris)
        outState.putString(MEDIA_NAMES, names)
    }

    private fun focusOnView(scrollView: NestedScrollView) {
        scrollView.smoothScrollTo(0, 0)
    }

    companion object {
        fun newInstance(mediaUri: Uri, mediaTitles:String): SubtitleDownloaderDialogFragment {
            return SubtitleDownloaderDialogFragment().apply {
                arguments = bundleOf(MEDIA_PATHS to mediaUri, MEDIA_NAMES to mediaTitles)
            }
        }
    }
}

enum class SubDownloadDialogState {
    Download,
    History,
    Login
}
