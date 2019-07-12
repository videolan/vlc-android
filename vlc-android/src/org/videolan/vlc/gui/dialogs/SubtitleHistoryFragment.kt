package org.videolan.vlc.gui.dialogs

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import org.videolan.vlc.databinding.SubtitleHistoryFragmentBinding
import org.videolan.vlc.viewmodels.SubtitlesModel

class SubtitleHistoryFragment : Fragment() {
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
        val binding = SubtitleHistoryFragmentBinding.inflate(inflater, container, false)

        adapter = SubtitlesAdapter((parentFragment as SubtitleDownloaderDialogFragment).listEventActor)
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
        fun newInstance(mediaUri: Uri): SubtitleHistoryFragment {
            val subtitleHistoryFragment = SubtitleHistoryFragment()
            subtitleHistoryFragment.arguments = Bundle(1).apply { putParcelable(MEDIA_PATH, mediaUri) }
            return subtitleHistoryFragment
        }
    }
}
