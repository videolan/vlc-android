package org.videolan.vlc.gui.dialogs

import android.content.DialogInterface
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.isActive
import org.videolan.resources.util.parcelableList
import org.videolan.vlc.R
import org.videolan.vlc.databinding.SubtitleDownloaderDialogBinding
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.deleteSubtitleDialog
import org.videolan.vlc.gui.view.OnItemSelectListener
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.VLCDownloadManager
import org.videolan.vlc.viewmodels.SubtitlesModel

private const val MEDIA_PATHS = "MEDIA_PATHS"
private const val MEDIA_NAMES = "MEDIA_NAMES"

class SubtitleDownloaderDialogFragment : VLCBottomSheetDialogFragment() {

    override fun getDefaultState(): Int = BottomSheetBehavior.STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = true

    override fun initialFocusedView(): View = binding.movieName

    private lateinit var downloadAdapter: SubtitlesAdapter
    private lateinit var historyAdapter: SubtitlesAdapter
    private lateinit var binding: SubtitleDownloaderDialogBinding
    private lateinit var uris: List<Uri>
    private lateinit var names: List<String>
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
                State.NotDownloaded -> VLCDownloadManager.download(requireActivity(), subtitleEvent.item)
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

        uris = savedInstanceState?.parcelableList<Uri>(MEDIA_PATHS)?.toList()
                ?: arguments?.parcelableList<Uri>(MEDIA_PATHS)?.toList() ?: listOf()
        names = savedInstanceState?.getStringArrayList(MEDIA_NAMES)?.toList()
                ?: arguments?.getStringArrayList(MEDIA_NAMES)?.toList() ?: listOf()

        viewModel = ViewModelProvider(requireActivity(), SubtitlesModel.Factory(requireContext(), uris[0], names[0]))[uris[0].path!!, SubtitlesModel::class.java]
        if (uris.isEmpty()) dismiss()
    }

    override fun onResume() {
        if (viewModel.isApiLoading.value == false) viewModel.onRefresh()
        super.onResume()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = SubtitleDownloaderDialogBinding.inflate(inflater, container, false)
        binding.viewmodel = viewModel

        if (uris.size < 2) {
            binding.subDownloadNext.visibility = View.GONE
        }

        binding.subDownloadNext.setOnClickListener {
            if (uris.size > 1)
                MediaUtils.showSubtitleDownloaderDialogFragment(requireActivity(), uris.takeLast(uris.size - 1), names.takeLast(names.size - 1))
            dismiss()
        }

        binding.movieName.text = names.firstOrNull() ?: uris[0].lastPathSegment
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

        }
        binding.cancelButton.setOnClickListener {
            state = SubDownloadDialogState.Download
        }

        binding.subDownloadSearch.setOnClickListener {
            UiTools.setKeyboardVisibility(binding.name, true)
            binding.name.requestFocus()
            state = SubDownloadDialogState.Search
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
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        focusOnView(binding.scrollView)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(MEDIA_PATHS, ArrayList(uris))
        outState.putStringArrayList(MEDIA_NAMES, ArrayList(names))
    }

    private fun focusOnView(scrollView: NestedScrollView) {
        scrollView.smoothScrollTo(0, 0)
    }

    companion object {
        fun newInstance(mediaUris: List<Uri>, mediaTitles:List<String>): SubtitleDownloaderDialogFragment {
            return SubtitleDownloaderDialogFragment().apply {
                arguments = bundleOf(MEDIA_PATHS to ArrayList(mediaUris), MEDIA_NAMES to mediaTitles)
            }
        }
    }
}

enum class SubDownloadDialogState {
    Download,
    History,
    Search
}
