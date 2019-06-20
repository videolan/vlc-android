package org.videolan.vlc.gui.tv.details

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.leanback.app.BackgroundManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Playlist
import org.videolan.tools.dp
import org.videolan.vlc.R
import org.videolan.vlc.databinding.ActivityMediaListTvBinding
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.tv.TvUtil
import org.videolan.vlc.gui.tv.browser.BaseTvActivity
import org.videolan.vlc.interfaces.ITVEventsHandler
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.ITEM
import org.videolan.vlc.viewmodels.mobile.PlaylistViewModel


@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class MediaListActivity : BaseTvActivity(), ITVEventsHandler {


    override fun refresh() {

    }

    private lateinit var adapter: MediaListAdapter
    internal lateinit var binding: ActivityMediaListTvBinding
    private lateinit var item: MediaLibraryItem
    private lateinit var backgroundManager: BackgroundManager
    private lateinit var viewModel: PlaylistViewModel
    private var lateSelectedItem: MediaLibraryItem? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_media_list_tv)
        item = if (savedInstanceState != null)
            savedInstanceState.getParcelable<Parcelable>(ITEM) as MediaLibraryItem
        else
            intent.getParcelableExtra<Parcelable>(ITEM) as MediaLibraryItem
        binding.item = item

        backgroundManager = BackgroundManager.getInstance(this)
        if (!backgroundManager.isAttached) {
            backgroundManager.attachToView(binding.root)
        }

        val linearLayoutManager = LinearLayoutManager(this)
        val dividerItemDecoration = DividerItemDecoration(this, linearLayoutManager.orientation)
        dividerItemDecoration.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider_tv_card_content_dark)!!)
        adapter = MediaListAdapter(item.itemType, this)

        binding.mediaList.layoutManager = linearLayoutManager
        binding.mediaList.adapter = adapter
        adapter.update(item.tracks.toList())
        item.tracks[0].length

        binding.mediaList.addItemDecoration(dividerItemDecoration)

        binding.title = item.title
        if (item.description?.isNotEmpty() == true) {

            binding.subtitle = item.description
        } else {
            binding.albumSubtitle.visibility = View.GONE

        }

        binding.totalTime = Tools.millisToString(item.tracks.sumByDouble { it.length.toDouble() }.toLong())
        binding.imageWidth = 90.dp


        binding.play.setOnClickListener { TvUtil.playMedia(this, item.tracks.toMutableList()) }
        binding.append.setOnClickListener { MediaUtils.appendMedia(this, item.tracks) }
        binding.insertNext.setOnClickListener { MediaUtils.insertNext(this, item.tracks) }
        binding.addPlaylist.setOnClickListener { UiTools.addToPlaylist(this, item.tracks, SavePlaylistDialog.KEY_NEW_TRACKS) }
        binding.delete.setOnClickListener {
            (viewModel.playlist as Playlist).delete()
            finish()
        }

        if (item.itemType == MediaLibraryItem.TYPE_PLAYLIST) {
            viewModel = getViewModel(item)
            viewModel.tracksProvider.pagedList.observe(this, Observer { tracks ->
                if (tracks != null) {
                    adapter.update(tracks)
                }
            })
        } else {
            binding.delete.visibility = View.GONE
        }


    }

    override fun onResume() {
        super.onResume()
        TvUtil.updateBackground(backgroundManager, item)

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(ITEM, item)
    }

    override fun onClickPlay(v: View, position: Int) {
        TvUtil.playMedia(this, item.tracks[position])
    }

    override fun onClickPlayNext(v: View, position: Int) {
        MediaUtils.insertNext(this, item.tracks[position])
    }

    override fun onClickAppend(v: View, position: Int) {
        MediaUtils.appendMedia(this, item.tracks[position])
    }

    override fun onClickAddToPlaylist(v: View, position: Int) {
        UiTools.addToPlaylist(this, arrayOf(item.tracks[position]), SavePlaylistDialog.KEY_NEW_TRACKS)
    }

    override fun onClickMoveUp(v: View, position: Int) {

        (viewModel.playlist as Playlist).move(position, position - 1)

    }

    override fun onClickMoveDown(v: View, position: Int) {
        (viewModel.playlist as Playlist).move(position, position + 1)
    }

    override fun onClickRemove(v: View, position: Int) {
        (viewModel.playlist as Playlist).remove(position)
    }

    override fun onFocusChanged(item: MediaLibraryItem) {
        if (item != lateSelectedItem) TvUtil.updateBackground(backgroundManager, item)
        lateSelectedItem = item
    }
}

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
internal fun MediaListActivity.getViewModel(playlist: MediaLibraryItem) = ViewModelProviders.of(this, PlaylistViewModel.Factory(this, playlist)).get(PlaylistViewModel::class.java)