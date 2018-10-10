package org.videolan.vlc.gui.dialogs

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.videolan.vlc.databinding.SubtitleDownloaderDialogBinding
import org.videolan.vlc.viewmodels.SubtitlesModel

private const val MEDIA_PATH = "MEDIA_PATH"
class SubtitleDownloaderDialogFragment: DialogFragment() {
    lateinit var viewModel: SubtitlesModel
    private lateinit var adapter: ViewPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path: String? = arguments?.getString(MEDIA_PATH)
        viewModel = ViewModelProviders.of(this, SubtitlesModel.Factory(requireContext(), path.toString())).get(SubtitlesModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val binding = SubtitleDownloaderDialogBinding.inflate(inflater, container, false)
        adapter = ViewPagerAdapter(childFragmentManager)
        binding.pager.adapter = adapter
        binding.tabLayout.setupWithViewPager(binding.pager)
        return binding.root
    }

    companion object {
        lateinit var subtitleDownloaderDialogFragment: SubtitleDownloaderDialogFragment
        fun newInstance(path: String): SubtitleDownloaderDialogFragment {
            subtitleDownloaderDialogFragment = SubtitleDownloaderDialogFragment()

            val args = Bundle()
            args.putString(MEDIA_PATH, path)
            subtitleDownloaderDialogFragment.arguments = args
            return subtitleDownloaderDialogFragment
        }
    }

    class ViewPagerAdapter(fragmentManager: FragmentManager): FragmentPagerAdapter(fragmentManager) {
        private val tabTitles = arrayOf("Download", "History")

        override fun getPageTitle(position: Int): CharSequence? {
            return tabTitles[position]
        }

        override fun getItem(position: Int): Fragment {
            return when(position) {
                0 -> SubtitleDownloadFragment()
                else -> SubtitleHistoryFragment()
            }
        }
        override fun getCount(): Int  = 2
    }
}
