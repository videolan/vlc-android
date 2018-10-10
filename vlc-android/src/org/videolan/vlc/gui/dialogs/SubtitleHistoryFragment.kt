package org.videolan.vlc.gui.dialogs


import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.experimental.channels.actor
import org.videolan.tools.coroutineScope
import org.videolan.vlc.databinding.SubtitleHistoryFragmentBinding
import org.videolan.vlc.gui.helpers.UiTools.deleteSubtitleDialog
import org.videolan.vlc.viewmodels.SubtitlesModel

class SubtitleHistoryFragment : Fragment() {
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
        recyclerView.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(activity)
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
