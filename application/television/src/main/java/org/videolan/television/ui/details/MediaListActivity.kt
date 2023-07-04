package org.videolan.television.ui.details

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.leanback.app.BackgroundManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.ITEM
import org.videolan.resources.util.parcelable
import org.videolan.television.R
import org.videolan.television.databinding.ActivityMediaListTvBinding
import org.videolan.television.ui.TvUtil
import org.videolan.television.ui.browser.BaseTvActivity
import org.videolan.television.ui.dialogs.ConfirmationTvActivity
import org.videolan.television.ui.dialogs.ConfirmationTvActivity.Companion.CONFIRMATION_DIALOG_TEXT
import org.videolan.television.ui.dialogs.ConfirmationTvActivity.Companion.CONFIRMATION_DIALOG_TITLE
import org.videolan.television.ui.updateBackground
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.helpers.UiTools.showPinIfNeeded
import org.videolan.vlc.interfaces.ITVEventsHandler
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.viewmodels.mobile.PlaylistViewModel

class MediaListActivity : BaseTvActivity(), ITVEventsHandler {

    override fun refresh() {}

    private lateinit var adapter: MediaListAdapter
    internal lateinit var binding: ActivityMediaListTvBinding
    private lateinit var item: MediaLibraryItem
    private lateinit var backgroundManager: BackgroundManager
    private lateinit var viewModel: PlaylistViewModel
    private var lateSelectedItem: MediaLibraryItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_media_list_tv)
        item = (savedInstanceState?.parcelable(ITEM) ?: intent.parcelable<Parcelable>(ITEM)) as MediaLibraryItem
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

        binding.mediaList.addItemDecoration(dividerItemDecoration)

        binding.title = item.title
        if (item.description?.isNotEmpty() == true) {

            binding.subtitle = item.description
        } else {
            binding.albumSubtitle.visibility = View.GONE
        }

        binding.totalTime = Tools.millisToString(item.tracks.sumOf { it.length })


        binding.play.requestFocus()
        binding.play.setOnClickListener { if (item is Playlist) TvUtil.playPlaylist(this, item as Playlist)  else TvUtil.playMedia(this, item.tracks.toMutableList())}
        binding.append.setOnClickListener { MediaUtils.appendMedia(this, item.tracks) }
        binding.insertNext.setOnClickListener { MediaUtils.insertNext(this, item.tracks) }
        binding.addPlaylist.setOnClickListener { addToPlaylist(item.tracks, SavePlaylistDialog.KEY_NEW_TRACKS) }
        binding.delete.setOnClickListener {
            if (!showPinIfNeeded()) {
                val intent = Intent(this, ConfirmationTvActivity::class.java)
                intent.putExtra(CONFIRMATION_DIALOG_TITLE, getString(R.string.validation_delete_playlist))
                intent.putExtra(CONFIRMATION_DIALOG_TEXT, getString(R.string.validation_delete_playlist_text))
                startActivityForResult(intent, REQUEST_DELETE_PLAYLIST)
            }
        }

        if (item.itemType == MediaLibraryItem.TYPE_PLAYLIST) {
            viewModel = getViewModel(item)
            viewModel.tracksProvider.pagedList.observe(this) { tracks ->
                if (tracks != null) {
                    adapter.update(tracks)
                }
            }
        } else {
            binding.delete.visibility = View.GONE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_DELETE_PLAYLIST) {
            if (resultCode == ConfirmationTvActivity.ACTION_ID_POSITIVE) {
                (viewModel.playlist as Playlist).delete()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.updateBackground(this, backgroundManager, item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(ITEM, item)
    }

    override fun onClickPlay(v: View, position: Int) {
        if (item is Playlist) TvUtil.playPlaylist(this, item as Playlist, position)  else  TvUtil.playMedia(this, item.tracks.toList(), position)
    }

    override fun onClickPlayNext(v: View, position: Int) {
        MediaUtils.insertNext(this, item.tracks[position])
    }

    override fun onClickAppend(v: View, position: Int) {
        MediaUtils.appendMedia(this, item.tracks[position])
    }

    override fun onClickAddToPlaylist(v: View, position: Int) {
        addToPlaylist(arrayOf(item.tracks[position]), SavePlaylistDialog.KEY_NEW_TRACKS)
    }

    override fun onClickMoveUp(v: View, position: Int) {
        if (!showPinIfNeeded()) {
            (viewModel.playlist as Playlist).move(position, position - 1)
        }
    }

    override fun onClickMoveDown(v: View, position: Int) {
        if (!showPinIfNeeded()) {
            (viewModel.playlist as Playlist).move(position, position + 1)
        }
    }

    override fun onClickRemove(v: View, position: Int) {
        if (!showPinIfNeeded()) {
            (viewModel.playlist as Playlist).remove(position)
        }
    }

    override fun onFocusChanged(item: MediaLibraryItem) {
        if (item != lateSelectedItem) lifecycleScope.updateBackground(this, backgroundManager, item)
        lateSelectedItem = item
    }

    companion object {
        private const val REQUEST_DELETE_PLAYLIST = 1
    }
}

internal fun MediaListActivity.getViewModel(playlist: MediaLibraryItem) = ViewModelProvider(this, PlaylistViewModel.Factory(this, playlist)).get(PlaylistViewModel::class.java)