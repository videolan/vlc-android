package org.videolan.vlc.gui.dialogs

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.databinding.SubtitleDownloadFragmentBinding
import org.videolan.vlc.gui.OnItemSelectListener
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.Settings
import org.videolan.vlc.viewmodels.SubtitlesModel

class SubtitleDownloadFragment : Fragment() {
    private lateinit var viewModel: SubtitlesModel
    private lateinit var adapter: SubtitlesAdapter
    lateinit var mediaUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaUri = arguments?.getParcelable(MEDIA_PATH) ?: Uri.EMPTY
        viewModel = ViewModelProviders.of(requireActivity(), SubtitlesModel.Factory(requireContext(), mediaUri)).get(mediaUri.path, SubtitlesModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val binding = SubtitleDownloadFragmentBinding.inflate(inflater, container, false)
        binding.viewmodel = viewModel

        if (!Settings.showTvUi && !AndroidDevices.isChromeBook) {
            //Prevent opening soft keyboard automatically
            binding.constraintLayout.isFocusableInTouchMode = true
        }

        adapter = SubtitlesAdapter((parentFragment as SubtitleDownloaderDialogFragment).listEventActor)
        val recyclerView = binding.subtitleList
        recyclerView.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(activity)
        viewModel.result.observe(viewLifecycleOwner, Observer {
            adapter.setList(it)
            if (it.isNotEmpty()) focusOnView(binding.scrollView, binding.swipeContainer)
        })

        binding.searchButton.setOnClickListener {
            UiTools.setKeyboardVisibility(it, false)
            viewModel.search(false)
            focusOnView(binding.scrollView, binding.swipeContainer)
        }


        val allValuesOfLanguages = resources.getStringArray(R.array.language_values)
        val allEntriesOfLanguages = resources.getStringArray(R.array.language_entries)
        binding.languageListSpinner.setOnItemsSelectListener(object: OnItemSelectListener {
            override fun onItemSelect(selectedItems: List<Int>) {
                val selectedLanguages = if (selectedItems.size == allValuesOfLanguages.size) listOf<String>()
                else selectedItems.filter { it in allValuesOfLanguages.indices }.map { allValuesOfLanguages[it] }
                viewModel.observableSearchLanguage.set(selectedLanguages)
            }
        })

        binding.languageListSpinner.setItems(allEntriesOfLanguages.toList())
        binding.languageListSpinner.setSelection(viewModel.getLastUsedLanguage().map { allValuesOfLanguages.indexOf(it) })

        return binding.root
    }

    private fun focusOnView(scrollView: NestedScrollView, view: View) {
        scrollView.smoothScrollTo(0, view.top)
    }

    companion object {
        fun newInstance(mediaUri: Uri): SubtitleDownloadFragment {
            val subtitleDownloadFragment = SubtitleDownloadFragment()
            subtitleDownloadFragment.arguments = Bundle(1).apply { putParcelable(MEDIA_PATH, mediaUri) }
            return subtitleDownloadFragment
        }
    }
}
