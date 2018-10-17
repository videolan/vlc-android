package org.videolan.vlc.gui.dialogs

import android.arch.lifecycle.Observer
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
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

class SubtitleDownloadFragment : Fragment() {
    private lateinit var viewModel: SubtitlesModel
    private lateinit var adapter: SubtitlesAdapter

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
        viewModel = (parentFragment as SubtitleDownloaderDialogFragment).viewModel
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
        recyclerView.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(activity)
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
}
