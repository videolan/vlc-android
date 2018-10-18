package org.videolan.vlc.gui.dialogs

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.videolan.vlc.R
import org.videolan.vlc.databinding.SubtitleDownloaderDialogBinding
import org.videolan.vlc.media.MediaUtils

private const val MEDIA_PATHS = "MEDIA_PATHS"
const val MEDIA_PATH = "MEDIA_PATH"

class SubtitleDownloaderDialogFragment: androidx.fragment.app.DialogFragment() {
    private lateinit var adapter: ViewPagerAdapter
    lateinit var paths: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        paths = savedInstanceState?.getStringArrayList(MEDIA_PATHS)?.toList() ?: arguments?.getStringArrayList(MEDIA_PATHS)?.toList() ?: listOf()
        if (paths.isEmpty()) dismiss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val binding = SubtitleDownloaderDialogBinding.inflate(inflater, container, false)
        adapter = ViewPagerAdapter(requireContext(), childFragmentManager, paths)
        binding.pager.adapter = adapter
        binding.tabLayout.setupWithViewPager(binding.pager)

        if(paths.size < 2) binding.nextButton.visibility = View.GONE

        binding.nextButton.setOnClickListener {
            if (paths.size > 1)
                MediaUtils.showSubtitleDownloaderDialogFragment(requireActivity(), paths.takeLast(paths.size - 1))
            dismiss()
        }

        binding.movieName.text = Uri.parse(paths[0]).lastPathSegment

        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(MEDIA_PATHS, ArrayList(paths))
    }

    companion object {
        fun newInstance(mediaPaths: List<String>): SubtitleDownloaderDialogFragment {
            val subtitleDownloaderDialogFragment = SubtitleDownloaderDialogFragment()

            val args = Bundle().apply { putStringArrayList(MEDIA_PATHS, ArrayList(mediaPaths)) }
            subtitleDownloaderDialogFragment.arguments = args
            return subtitleDownloaderDialogFragment
        }
    }

    class ViewPagerAdapter(context: Context, fragmentManager: androidx.fragment.app.FragmentManager, private val paths: List<String>): androidx.fragment.app.FragmentPagerAdapter(fragmentManager) {
        private val tabTitles = arrayOf(context.getString(R.string.download), context.getString(R.string.history))

        override fun getPageTitle(position: Int): String? = tabTitles[position]

        override fun getItem(position: Int) = when(position) {
            0 -> SubtitleDownloadFragment.newInstance(paths[0])
            else -> SubtitleHistoryFragment.newInstance(paths[0])
        }

        override fun getCount() = 2
    }
}
