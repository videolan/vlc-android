package org.videolan.vlc.gui.dialogs

import android.arch.lifecycle.ViewModelProviders
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.videolan.vlc.databinding.SubtitleDownloaderDialogBinding
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.viewmodels.SubtitlesModel
import retrofit2.http.Url

private const val MEDIA_PATHS = "MEDIA_PATHS"
class SubtitleDownloaderDialogFragment: DialogFragment() {
    lateinit var viewModel: SubtitlesModel
    private lateinit var adapter: ViewPagerAdapter
    lateinit var paths: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paths = arguments?.getStringArrayList(MEDIA_PATHS)?.toList() ?: listOf()
        if (paths.isEmpty())
            dismiss()

        viewModel = ViewModelProviders.of(this, SubtitlesModel.Factory(requireContext(), paths[0])).get(SubtitlesModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val binding = SubtitleDownloaderDialogBinding.inflate(inflater, container, false)
        adapter = ViewPagerAdapter(childFragmentManager)
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
