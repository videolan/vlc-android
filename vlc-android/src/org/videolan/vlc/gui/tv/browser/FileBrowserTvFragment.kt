package org.videolan.vlc.gui.tv.browser

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.song_browser.*
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.browser.PathAdapter
import org.videolan.vlc.gui.browser.PathAdapterListener
import org.videolan.vlc.gui.tv.FileTvItemAdapter
import org.videolan.vlc.gui.tv.TvItemAdapter
import org.videolan.vlc.gui.tv.TvUtil
import org.videolan.vlc.gui.view.VLCDividerItemDecoration
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.providers.BrowserProvider
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.util.CATEGORY
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.ITEM
import org.videolan.vlc.util.isSchemeSupported
import org.videolan.vlc.viewmodels.browser.BrowserModel
import org.videolan.vlc.viewmodels.browser.NetworkModel
import org.videolan.vlc.viewmodels.browser.TYPE_FILE
import org.videolan.vlc.viewmodels.browser.TYPE_NETWORK

private const val TAG = "FileBrowserTvFragment"
@UseExperimental(ObsoleteCoroutinesApi::class)
@ExperimentalCoroutinesApi
class FileBrowserTvFragment : BaseBrowserTvFragment(), PathAdapterListener {

    override fun backTo(tag: String) {
        if (tag == "root") {
            requireActivity().finish()
            return
        }
        requireActivity().supportFragmentManager.popBackStack(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    override fun currentContext(): Context = requireActivity()

    override fun showRoot(): Boolean = true

    private var favExists: Boolean = false
    private var isRootLevel = false
    private lateinit var browserFavRepository: BrowserFavRepository
    private var item: MediaLibraryItem? = null
    override lateinit var adapter: TvItemAdapter

    var mrl: String? = null

    override fun getTitle() = when (getCategory()) {
        TYPE_FILE -> getString(R.string.directories)
        TYPE_NETWORK -> getString(R.string.network_browsing)
        else -> getString(R.string.video)
    }

    override fun getColumnNumber() = resources.getInteger(R.integer.tv_songs_col_count)

    companion object {
        fun newInstance(type: Long, item: MediaLibraryItem?, root : Boolean = false) =
                FileBrowserTvFragment().apply {
                    arguments = Bundle().apply {
                        this.putLong(CATEGORY, type)
                        this.putParcelable(ITEM, item)
                        this.putBoolean("rootLevel", root)
                    }
                }
    }

    override fun provideAdapter(eventsHandler: IEventsHandler, itemSize: Int): TvItemAdapter {
        return FileTvItemAdapter(getCategory(), this, itemSize, isRootLevel)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        item = if (savedInstanceState != null) savedInstanceState.getParcelable<Parcelable>(ITEM) as? MediaLibraryItem
        else arguments?.getParcelable(ITEM) as? MediaLibraryItem

        isRootLevel = arguments?.getBoolean("rootLevel") ?: false
        (item as? MediaWrapper)?.run { mrl = location }
        viewModel = ViewModelProviders.of(this, NetworkModel.Factory(requireContext(), mrl, false)).get(NetworkModel::class.java)

        viewModel.currentItem = item
        browserFavRepository = BrowserFavRepository.getInstance(requireContext())

        (viewModel.provider as BrowserProvider).dataset.observe(this, Observer { items ->
            val lm = binding.list.layoutManager as LinearLayoutManager
            val selectedItem = lm.focusedChild
            submitList(items)
            binding.list.post {
                for (i in 0 until lm.childCount) {
                    if (lm.getChildAt(i) == selectedItem) {
                        lm.getChildAt(i)?.requestFocus()
                        lm.scrollToPosition(lm.getPosition(lm.getChildAt(i)!!))
                    }
                }
            }
            binding.empty = if (isRootLevel && getCategory() == TYPE_NETWORK) false else items.isEmpty()
            if (BuildConfig.DEBUG) Log.d(TAG, "Submit list of ${items.size} items")
            if (BuildConfig.DEBUG) Log.d(TAG, "header size: ${viewModel.provider.headers.size()}")

            //headers
            val nbColumns = if ((viewModel as BrowserModel).sort == AbstractMedialibrary.SORT_ALPHA || (viewModel as BrowserModel).sort == AbstractMedialibrary.SORT_DEFAULT) 9 else 1

            headerList.layoutManager = GridLayoutManager(requireActivity(), nbColumns)
            headerAdapter.sortType = (viewModel as BrowserModel).sort
            val headerItems = ArrayList<String>()
            viewModel.provider.headers.run {
                for (i in 0 until size()) {
                    headerItems.add(valueAt(i))
                }
            }
            headerAdapter.items = headerItems
            headerAdapter.notifyDataSetChanged()
        })

        (viewModel as BrowserModel).provider.liveHeaders.observe(this, Observer {
            headerAdapter.notifyDataSetChanged()
            if (BuildConfig.DEBUG) Log.d(TAG, "header size (observe): ${viewModel.provider.headers.size()}")
        })

        (viewModel as BrowserModel).getDescriptionUpdate().observe(this, Observer { pair ->
            if (BuildConfig.DEBUG) Log.d(TAG, "Description update: ${pair.first} ${pair.second}")
            if (BuildConfig.DEBUG) Log.d(TAG, "header size (desc): ${viewModel.provider.headers.size()}")
            if (pair != null) (adapter as RecyclerView.Adapter<*>).notifyItemChanged(pair.first)
        })

        (viewModel as BrowserModel).loading.observe(this, Observer {
            binding.loading = it
            animationDelegate.setVisibility(binding.loadingBar, if (it) View.VISIBLE else View.GONE)
        })
    }

    override fun onStart() {
        super.onStart()
        (viewModel.currentItem as? MediaWrapper).setBreadcrumb()
    }

    private fun MediaWrapper?.setBreadcrumb() {
        if (this == null) return
        val ariane = requireActivity().findViewById<RecyclerView>(R.id.ariane)
                ?: return
        if (isSchemeSupported(uri?.scheme)) {
            ariane.visibility = View.VISIBLE
            ariane.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            ariane.adapter = PathAdapter(this@FileBrowserTvFragment, this)
            if (ariane.itemDecorationCount == 0) {
                val did = object : VLCDividerItemDecoration(requireContext(), HORIZONTAL, ContextCompat.getDrawable(requireContext(), R.drawable.ic_divider)!!) {
                    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                        val position = parent.getChildAdapterPosition(view)
                        // hide the divider for the last child
                        if (position == parent.adapter?.itemCount ?: 0 - 1) {
                            outRect.setEmpty()
                        } else {
                            super.getItemOffsets(outRect, view, parent, state)
                        }
                    }
                }
                ariane.addItemDecoration(did)
            }
            ariane.scrollToPosition(ariane.adapter!!.itemCount - 1)
        } else ariane.visibility = View.GONE
        animationDelegate.setVisibility(binding.title, if (ariane.visibility == View.GONE) View.VISIBLE else View.GONE)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        launch {
            animationDelegate.setVisibility(binding.favoriteButton, if (isRootLevel) View.GONE else View.VISIBLE)
            animationDelegate.setVisibility(binding.imageButtonFavorite, View.VISIBLE)
            animationDelegate.setVisibility(binding.favoriteDescription, View.VISIBLE)
            favExists = withContext(Dispatchers.IO) {
                (item as? MediaWrapper)?.let { browserFavRepository.browserFavExists(it.uri) }
                        ?: false
            }
            binding.favoriteButton.setImageResource(if (favExists) R.drawable.ic_menu_fav_tv else R.drawable.ic_menu_not_fav_tv)
            binding.imageButtonFavorite.setImageResource(if (favExists) R.drawable.ic_menu_fav_tv_normal else R.drawable.ic_menu_not_fav_tv_normal)
        }
        val favoriteClickListener: (View) -> Unit = {
            launch {
                withContext(Dispatchers.IO) {
                    val mw = (item as MediaWrapper)
                    when {
                        browserFavRepository.browserFavExists(mw.uri) -> browserFavRepository.deleteBrowserFav(mw.uri)
                        mw.uri.scheme == "file" -> browserFavRepository.addLocalFavItem(mw.uri, mw.title, mw.artworkURL)
                        else -> browserFavRepository.addNetworkFavItem(mw.uri, mw.title, mw.artworkURL)
                    }
                    favExists = !favExists
                }
                if (!isRootLevel) binding.favoriteButton.setImageResource(if (favExists) R.drawable.ic_menu_fav_tv else R.drawable.ic_menu_not_fav_tv)
                binding.imageButtonFavorite.setImageResource(if (favExists) R.drawable.ic_menu_fav_tv_normal else R.drawable.ic_menu_not_fav_tv_normal)
            }
        }
        if (!isRootLevel) binding.favoriteButton.setOnClickListener(favoriteClickListener)
        binding.imageButtonFavorite.setOnClickListener(favoriteClickListener)
    }

    override fun onResume() {
        super.onResume()
        if (item == null) (viewModel.provider as BrowserProvider).browseRoot()
        else refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(ITEM, item)
        outState.putLong(CATEGORY, getCategory())
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        (viewModel as BrowserModel).stop()
    }

    override fun getCategory() = arguments?.getLong(CATEGORY, TYPE_FILE) ?: TYPE_FILE

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        val mediaWrapper = item as AbstractMediaWrapper

        mediaWrapper.removeFlags(AbstractMediaWrapper.MEDIA_FORCE_AUDIO)
        if (mediaWrapper.type == AbstractMediaWrapper.TYPE_DIR) browse(mediaWrapper, true)
        else TvUtil.openMedia(requireActivity(), item, viewModel as BrowserModel)
    }

    fun browse(media: AbstractMediaWrapper, save: Boolean) {
        val ctx = activity
        if (ctx == null || !isResumed || isRemoving) return
        val ft = ctx.supportFragmentManager.beginTransaction()
        val next = newInstance(getCategory(), media)
        (viewModel as BrowserModel).saveList(media)
        if (save) ft.addToBackStack(if (mrl == null) "root" else viewModel.currentItem?.title
                ?: FileUtils.getFileNameFromPath(mrl))
        ft.replace(R.id.tv_fragment_placeholder, next, media.title)
        ft.commit()
    }
}
