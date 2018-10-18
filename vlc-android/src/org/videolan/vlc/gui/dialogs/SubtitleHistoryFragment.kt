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
import kotlinx.coroutines.experimental.channels.actor
import org.videolan.tools.coroutineScope
import org.videolan.vlc.databinding.SubtitleHistoryFragmentBinding
import org.videolan.vlc.gui.helpers.UiTools.deleteSubtitleDialog
import org.videolan.vlc.viewmodels.SubtitlesModel

class SubtitleHistoryFragment : androidx.fragment.app.Fragment() {
    private lateinit var viewModel: SubtitlesModel
    private lateinit var adapter: SubtitlesAdapter
    lateinit var mediaPath: String

    private val listEventActor = coroutineScope.actor<SubtitleItem> {
        for (event in channel)
            if (event.state == State.Downloaded) {
                deleteSubtitleDialog(context,
                        { _, _ -> viewModel.deleteSubtitle(event.mediaPath, event.idSubtitle)
                        }, { _, _ -> })
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaPath = arguments?.getString(MEDIA_PATH, "") ?: ""
        viewModel = ViewModelProviders.of(requireActivity(), SubtitlesModel.Factory(requireContext(), mediaPath)).get(mediaPath, SubtitlesModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val binding = SubtitleHistoryFragmentBinding.inflate(inflater, container, false)

        adapter = SubtitlesAdapter(listEventActor)
        val recyclerView = binding.subtitleList
        recyclerView.addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(activity, androidx.recyclerview.widget.DividerItemDecoration.VERTICAL))
        recyclerView.adapter = adapter
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
        viewModel.history.observe(this, Observer {
            adapter.setList(it)
        })

        return binding.root
    }

    companion object {
        fun newInstance(mediaPath: String): SubtitleHistoryFragment {
            val subtitleHistoryFragment = SubtitleHistoryFragment()
            subtitleHistoryFragment.arguments = Bundle(1).apply { putString(MEDIA_PATH, mediaPath) }
            return subtitleHistoryFragment
        }
    }


}
