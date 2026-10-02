package org.videolan.vlc.gui.audio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.view.ActionMode
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.videolan.tools.HttpImageLoader
import org.videolan.medialibrary.Tools
import org.videolan.vlc.R
import org.videolan.vlc.discourse.Discourse
import org.videolan.vlc.discourse.DiscourseAudio
import org.videolan.vlc.discourse.playDiscourseAudios
import org.videolan.vlc.gui.BaseFragment
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.view.SwipeRefreshLayout
import org.videolan.vlc.viewmodels.DiscourseViewModel

class DiscourseFragment : BaseFragment() {
    private val model: DiscourseViewModel by viewModels { DiscourseViewModel.Factory(requireContext()) }
    private lateinit var grid: RecyclerView
    private lateinit var gridSwipe: SwipeRefreshLayout
    private lateinit var detail: View
    private lateinit var tracks: RecyclerView
    private lateinit var tracksSwipe: SwipeRefreshLayout
    private lateinit var state: View
    private lateinit var progress: ProgressBar
    private lateinit var message: TextView
    private lateinit var retry: Button
    private lateinit var backCallback: OnBackPressedCallback

    override fun getTitle() = getString(R.string.discourse)
    override fun onCreateActionMode(mode: ActionMode, menu: Menu) = false
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
    override fun onDestroyActionMode(mode: ActionMode) = Unit

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) =
        inflater.inflate(R.layout.discourse_fragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        grid = view.findViewById(R.id.discourse_grid)
        gridSwipe = view.findViewById(R.id.discourse_grid_swipe)
        detail = view.findViewById(R.id.discourse_detail)
        tracks = view.findViewById(R.id.discourse_tracks)
        tracksSwipe = view.findViewById(R.id.discourse_tracks_swipe)
        state = view.findViewById(R.id.discourse_state)
        progress = view.findViewById(R.id.discourse_progress)
        message = view.findViewById(R.id.discourse_message)
        retry = view.findViewById(R.id.discourse_retry)
        grid.layoutManager = LinearLayoutManager(requireContext())
        tracks.layoutManager = LinearLayoutManager(requireContext())
        gridSwipe.setOnRefreshListener { model.refresh() }
        tracksSwipe.setOnRefreshListener { model.retryDetail() }
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && !recyclerView.canScrollVertically(1)) model.loadMore()
            }
        })
        view.findViewById<ImageButton>(R.id.discourse_back).setOnClickListener { model.back() }
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = model.back()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        model.state.observe(viewLifecycleOwner, ::render)
    }

    override fun onResume() {
        super.onResume()
        model.load()
    }

    private fun render(value: DiscourseViewModel.State) {
        gridSwipe.isRefreshing = false
        tracksSwipe.isRefreshing = false
        retry.setOnClickListener(null)
        when (value) {
            DiscourseViewModel.State.Idle, DiscourseViewModel.State.Loading -> showState(null, loading = true)
            DiscourseViewModel.State.Error -> showState(getString(R.string.discourse_load_failed)) { model.load() }
            is DiscourseViewModel.State.Catalogue -> {
                backCallback.isEnabled = false
                detail.isVisible = false
                if (value.discourses.isEmpty()) showState(getString(R.string.discourse_empty))
                else {
                    state.isVisible = false
                    gridSwipe.isVisible = true
                    (grid.adapter as? DiscourseAdapter)?.update(value.discourses)
                        ?: run { grid.adapter = DiscourseAdapter(value.discourses, model::select) }
                }
            }
            is DiscourseViewModel.State.Detail -> {
                backCallback.isEnabled = true
                grid.isVisible = false
                detail.isVisible = true
                val image = detail.findViewById<ImageView>(R.id.discourse_detail_image)
                detail.findViewById<TextView>(R.id.discourse_detail_title).text = value.discourse.title
                loadImage(image, value.discourse.thumbnailUrl)
                when {
                    value.error -> showState(getString(R.string.discourse_tracks_failed)) { model.retryDetail() }
                    value.tracks == null -> showState(null, loading = true)
                    value.tracks.isEmpty() -> showState(getString(R.string.discourse_no_tracks))
                    else -> {
                        state.isVisible = false
                        tracks.adapter = TrackAdapter(value.tracks) { position ->
                            requireContext().playDiscourseAudios(value.tracks, position)
                        }
                    }
                }
            }
        }
    }

    private fun showState(text: String?, loading: Boolean = false, action: (() -> Unit)? = null) {
        state.isVisible = true
        progress.isVisible = loading
        message.isVisible = text != null
        message.text = text
        retry.isVisible = action != null
        retry.setOnClickListener { action?.invoke() }
        if (model.state.value !is DiscourseViewModel.State.Detail) {
            grid.isVisible = false
            gridSwipe.isVisible = false
            detail.isVisible = false
        }
    }

    private fun loadImage(image: ImageView, url: String?) {
        image.setImageDrawable(UiTools.getDefaultAudioDrawable(requireContext()))
        val imageUrl = url?.takeIf { it.startsWith("http") }
            ?: url?.let { IMAGE_BASE_URL.trimEnd('/') + "/" + it.trimStart('/') }
        image.tag = imageUrl
        if (imageUrl.isNullOrBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = HttpImageLoader.downloadBitmap(imageUrl)
            if (image.tag == imageUrl && bitmap != null) image.setImageBitmap(bitmap)
        }
    }

    private inner class DiscourseAdapter(
        items: List<Discourse>,
        private val click: (Discourse) -> Unit
    ) : RecyclerView.Adapter<DiscourseAdapter.Holder>() {
        private var items = items
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.discourse_image)
            val title: TextView = view.findViewById(R.id.discourse_title)
            val language: TextView = view.findViewById(R.id.discourse_language)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            layoutInflater.inflate(R.layout.discourse_card, parent, false)
        )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.language.text = item.language.replaceFirstChar(Char::uppercase)
            holder.itemView.contentDescription = listOf(item.title, holder.language.text).filter(CharSequence::isNotBlank).joinToString(". ")
            holder.itemView.setOnClickListener { click(item) }
            loadImage(holder.image, item.thumbnailUrl)
        }

        override fun getItemCount() = items.size

        fun update(items: List<Discourse>) {
            this.items = items
            notifyDataSetChanged()
        }
    }

    private inner class TrackAdapter(
        private val items: List<DiscourseAudio>,
        private val click: (Int) -> Unit
    ) : RecyclerView.Adapter<DiscourseTrackHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = DiscourseTrackHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.discourse_track, parent, false)
        )

        override fun onBindViewHolder(holder: DiscourseTrackHolder, position: Int) {
            val item = items[position]
            holder.number.text = (item.trackNumber ?: position + 1).toString()
            loadImage(holder.image, item.discourseThumbnailUrl)
            holder.title.text = item.title
            holder.meta.text = item.durationSeconds?.let { Tools.millisToString((it * 1000).toLong()) }.orEmpty()
            holder.itemView.contentDescription = "${holder.number.text}. ${item.title}. ${holder.meta.text}"
            holder.itemView.setOnClickListener { click(holder.bindingAdapterPosition) }
        }

        override fun getItemCount() = items.size
    }

    private companion object {
        const val IMAGE_BASE_URL = "https://osho.b-cdn.net/OSHO"
    }
}

private class DiscourseTrackHolder(view: View) : RecyclerView.ViewHolder(view) {
    val number: TextView = view.findViewById(R.id.discourse_track_number)
    val image: ImageView = view.findViewById(R.id.discourse_track_image)
    val title: TextView = view.findViewById(R.id.discourse_track_title)
    val meta: TextView = view.findViewById(R.id.discourse_track_meta)
}
