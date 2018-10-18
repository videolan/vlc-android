package org.videolan.vlc.gui.dialogs

import android.net.Uri
import android.os.Bundle
import android.support.v4.app.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.videolan.vlc.databinding.SubtitleDownloaderDialogBinding
import org.videolan.vlc.media.MediaUtils

private const val MEDIA_PATHS = "MEDIA_PATHS"
const val MEDIA_PATH = "MEDIA_PATH"
class SubtitleDownloaderDialogFragment: DialogFragment() {
    private lateinit var adapter: ViewPagerAdapter
    lateinit var paths: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        paths = if (savedInstanceState != null) savedInstanceState?.getStringArrayList(MEDIA_PATHS)?.toList() ?: listOf()
        else arguments?.getStringArrayList(MEDIA_PATHS)?.toList() ?: listOf()

        if (paths.isEmpty())
            dismiss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val binding = SubtitleDownloaderDialogBinding.inflate(inflater, container, false)
        adapter = ViewPagerAdapter(childFragmentManager, paths)
        binding.pager.adapter = adapter
        binding.tabLayout.setupWithViewPager(binding.pager)

        if(paths.size < 2) binding.nextButton.visibility = View.INVISIBLE

        binding.nextButton.setOnClickListener {
            if (paths.size > 1)
                MediaUtils.showSubtitleDownloaderDialogFragment(activity!!, paths.takeLast(paths.size - 1))
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
        lateinit var subtitleDownloaderDialogFragment: SubtitleDownloaderDialogFragment
        fun newInstance(mediaPaths: List<String>): SubtitleDownloaderDialogFragment {
            subtitleDownloaderDialogFragment = SubtitleDownloaderDialogFragment()

            val args = Bundle()
            args.putStringArrayList(MEDIA_PATHS, ArrayList(mediaPaths))
            subtitleDownloaderDialogFragment.arguments = args
            return subtitleDownloaderDialogFragment
        }
    }

    class ViewPagerAdapter(fragmentManager: FragmentManager, private val paths: List<String>): FragmentPagerAdapter(fragmentManager) {
        private val tabTitles = arrayOf("Download", "History")

        override fun getPageTitle(position: Int): CharSequence? {
            return tabTitles[position]
        }

        override fun getItem(position: Int): Fragment {
            return when(position) {
                0 -> SubtitleDownloadFragment.newInstance(paths[0])
                else -> SubtitleHistoryFragment.newInstance(paths[0])
            }
        }
        override fun getCount(): Int  = 2
    }
}
