package org.videolan.vlc.gui.dialogs

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.isActive
import org.videolan.tools.coroutineScope
import org.videolan.vlc.R
import org.videolan.vlc.databinding.SubtitleDownloaderDialogBinding
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.helpers.UiTools.deleteSubtitleDialog
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.VLCDownloadManager
import org.videolan.vlc.viewmodels.SubtitlesModel

private const val MEDIA_PATHS = "MEDIA_PATHS"
const val MEDIA_PATH = "MEDIA_PATH"

class SubtitleDownloaderDialogFragment: androidx.fragment.app.DialogFragment() {
    private lateinit var adapter: ViewPagerAdapter
    private lateinit var paths: List<String>
    private lateinit var viewModel: SubtitlesModel
    private lateinit var toast: Toast

    val listEventActor = coroutineScope.actor<SubtitleEvent> {
        for (subtitleEvent in channel) if(isActive) when (subtitleEvent) {
            is Click -> when(subtitleEvent.item.state) {
                State.NotDownloaded -> VLCDownloadManager.download(context!!, subtitleEvent.item)
                State.Downloaded -> deleteSubtitleDialog(context,
                        { _, _ -> viewModel.deleteSubtitle(subtitleEvent.item.mediaPath, subtitleEvent.item.idSubtitle) }, { _, _ -> })
            }
            is LongClick -> {
                @StringRes val message = when(subtitleEvent.item.state) {
                    State.NotDownloaded -> {R.string.download_the_selected}
                    State.Downloaded -> {R.string.delete_the_selected}
                    // Todo else -> {"Cancel download"}
                    else -> return@actor
                }

                if (::toast.isInitialized) toast.cancel()
                toast = Toast.makeText(activity, message, Toast.LENGTH_SHORT)
                toast.setGravity(Gravity.TOP,0,100)
                toast.show()
            }
        } else channel.close()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        paths = savedInstanceState?.getStringArrayList(MEDIA_PATHS)?.toList() ?: arguments?.getStringArrayList(MEDIA_PATHS)?.toList() ?: listOf()
        if (paths.isEmpty()) dismiss()

        viewModel = ViewModelProviders.of(requireActivity(), SubtitlesModel.Factory(requireContext(), paths[0])).get(paths[0], SubtitlesModel::class.java)
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

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        // In manifest for VideoPlayerActivity defined
        // android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"
        // so dialog size breaks on orientation
        if (requireActivity() is VideoPlayerActivity) {
            MediaUtils.showSubtitleDownloaderDialogFragment(requireActivity(), paths)
            dismiss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(MEDIA_PATHS, ArrayList(paths))
    }

    override fun onDestroy() {
        super.onDestroy()
        (activity as? DialogActivity)?.finish()
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
