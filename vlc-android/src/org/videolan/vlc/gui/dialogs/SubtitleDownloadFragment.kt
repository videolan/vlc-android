package org.videolan.vlc.gui.dialogs

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import kotlinx.coroutines.experimental.channels.actor
import org.videolan.tools.coroutineScope
import org.videolan.vlc.R
import org.videolan.vlc.databinding.SubtitleDownloadFragmentBinding
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.deleteSubtitleDialog
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.VLCDownloadManager
import org.videolan.vlc.viewmodels.SubtitlesModel

class SubtitleDownloadFragment : androidx.fragment.app.Fragment() {
    private lateinit var viewModel: SubtitlesModel
    private lateinit var adapter: SubtitlesAdapter
    lateinit var mediaPath: String

    private val listEventActor = coroutineScope.actor<SubtitleItem> {
        for (subtitleItem in channel)
            when (subtitleItem.state) {
                State.NotDownloaded -> {
                    VLCDownloadManager.download(context!!, subtitleItem)
                }
                State.Downloaded -> deleteSubtitleDialog(context,
                        { _, _ -> viewModel.deleteSubtitle(subtitleItem.mediaPath, subtitleItem.idSubtitle) }, { _, _ -> })
            }
    }

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

        adapter = SubtitlesAdapter(listEventActor)
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
        binding.languageListSpinner.setSelection(allValuesOfLanguages.indexOf(viewModel.getLastUsedLanguage()))
        binding.languageListSpinner.onItemSelectedListener = object :AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLanguage = allValuesOfLanguages[position]
                viewModel.observableSearchLanguage.set(selectedLanguage)
            }

        }
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
