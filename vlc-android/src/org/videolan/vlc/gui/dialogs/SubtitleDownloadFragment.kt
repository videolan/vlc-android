package org.videolan.vlc.gui.dialogs

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.videolan.vlc.R
import org.videolan.vlc.databinding.SubtitleDownloadFragmentBinding
import org.videolan.vlc.gui.OnItemSelectListener
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.viewmodels.SubtitlesModel

class SubtitleDownloadFragment : androidx.fragment.app.Fragment() {
    private lateinit var viewModel: SubtitlesModel
    private lateinit var adapter: SubtitlesAdapter
    lateinit var mediaPath: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaPath = arguments?.getString(MEDIA_PATH, "") ?: ""
        viewModel = ViewModelProviders.of(requireActivity(), SubtitlesModel.Factory(requireContext(), mediaPath)).get(mediaPath, SubtitlesModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val binding = SubtitleDownloadFragmentBinding.inflate(inflater, container, false)
        binding.viewmodel = viewModel

        if (!AndroidDevices.isAndroidTv)
            //Prevent opening soft keyboard automatically
            binding.constraintLayout.isFocusableInTouchMode = true

        adapter = SubtitlesAdapter((parentFragment as SubtitleDownloaderDialogFragment).listEventActor)
        val recyclerView = binding.subtitleList
        recyclerView.addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(activity, androidx.recyclerview.widget.DividerItemDecoration.VERTICAL))
        recyclerView.adapter = adapter
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
        viewModel.result.observe(this, Observer {
            adapter.setList(it)
        })

        binding.searchButton.setOnClickListener {
            UiTools.setKeyboardVisibility(it, false)
            viewModel.search(false)
        }

        val allValuesOfLanguages = resources.getStringArray(R.array.language_values)
        val allEntriesOfLanguages = resources.getStringArray(R.array.language_entries)
        binding.languageListSpinner.setOnItemsSelectListener(object: OnItemSelectListener {
            override fun onItemSelect(selectedItems: List<Int>) {
                val selectedLanguages = if (selectedItems.size == allValuesOfLanguages.size) listOf<String>()
                else selectedItems.map { allValuesOfLanguages[it] }
                viewModel.observableSearchLanguage.set(selectedLanguages)
            }
        })

        binding.languageListSpinner.setItems(allEntriesOfLanguages.toList())
        binding.languageListSpinner.setSelection(viewModel.getLastUsedLanguage().map { allValuesOfLanguages.indexOf(it) })

        return binding.root
    }

    companion object {
        lateinit var subtitleDownloadFragment: SubtitleDownloadFragment
        fun newInstance(mediaPath: String): SubtitleDownloadFragment {
            subtitleDownloadFragment = SubtitleDownloadFragment()

            val args = Bundle()
            args.putString(MEDIA_PATH, mediaPath)
            subtitleDownloadFragment.arguments = args
            return subtitleDownloadFragment
        }
    }
}
