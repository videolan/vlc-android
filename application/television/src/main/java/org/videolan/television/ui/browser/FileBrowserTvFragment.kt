package org.videolan.television.ui.browser

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.libvlc.Dialog
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.CATEGORY
import org.videolan.resources.ITEM
import org.videolan.television.R
import org.videolan.television.ui.FileTvItemAdapter
import org.videolan.television.ui.TvItemAdapter
import org.videolan.television.ui.TvUtil
import org.videolan.tools.isStarted
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.browser.PathAdapter
import org.videolan.vlc.gui.browser.PathAdapterListener
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.gui.view.VLCDividerItemDecoration
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.providers.BrowserProvider
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.util.DialogDelegate
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.IDialogManager
import org.videolan.vlc.util.isSchemeSupported
import org.videolan.vlc.viewmodels.browser.*

private const val TAG = "FileBrowserTvFragment"

@OptIn(ObsoleteCoroutinesApi::class)
@ExperimentalCoroutinesApi
class FileBrowserTvFragment : BaseBrowserTvFragment<MediaLibraryItem>(), PathAdapterListener, IDialogManager {

    private var favExists: Boolean = false
    private var isRootLevel = false
    private lateinit var browserFavRepository: BrowserFavRepository
    private var item: MediaLibraryItem? = null
    override lateinit var adapter: TvItemAdapter
    private val dialogsDelegate by lazy(LazyThreadSafetyMode.NONE) { DialogDelegate() }

    var mrl: String? = null

    override fun getTitle() = when (getCategory()) {
        TYPE_FILE -> getString(R.string.directories)
        TYPE_NETWORK -> getString(R.string.network_browsing)
        else -> getString(R.string.video)
    }

    override fun getColumnNumber() = resources.getInteger(R.integer.tv_songs_col_count)

    override fun provideAdapter(eventsHandler: IEventsHandler<MediaLibraryItem>, itemSize: Int): TvItemAdapter {
        return FileTvItemAdapter(this, itemSize, isRootLevel && getCategory() == TYPE_NETWORK)
    }

    override fun getDisplayPrefId() = "display_tv_file_${getCategory()}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        item = if (savedInstanceState != null) savedInstanceState.getParcelable<Parcelable>(ITEM) as? MediaLibraryItem
        else arguments?.getParcelable(ITEM) as? MediaLibraryItem

        isRootLevel = arguments?.getBoolean("rootLevel") ?: false
        (item as? MediaWrapper)?.run { mrl = location }
        val category = arguments?.getLong(CATEGORY, TYPE_FILE) ?: TYPE_FILE
        viewModel = getBrowserModel(category = category, url = mrl, showHiddenFiles = false)

        viewModel.currentItem = item
        browserFavRepository = BrowserFavRepository.getInstance(requireContext())

        if (getCategory() == TYPE_NETWORK) {
            dialogsDelegate.observeDialogs(this, this)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (viewModel as BrowserModel).dataset.observe(viewLifecycleOwner, Observer { items ->
            if (items == null) return@Observer
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
            binding.emptyLoading.state = if (items.isEmpty()) EmptyLoadingState.EMPTY else EmptyLoadingState.NONE

            //headers
            val nbColumns = if ((viewModel as BrowserModel).sort == Medialibrary.SORT_ALPHA || (viewModel as BrowserModel).sort == Medialibrary.SORT_DEFAULT) 9 else 1

            binding.headerList.layoutManager = GridLayoutManager(requireActivity(), nbColumns)
            headerAdapter.sortType = (viewModel as BrowserModel).sort
        })

        viewModel.provider.liveHeaders.observe(viewLifecycleOwner, Observer {
            updateHeaders(it)
            binding.list.invalidateItemDecorations()
        })

        (viewModel.provider as BrowserProvider).loading.observe(viewLifecycleOwner, Observer {
            if (it) binding.emptyLoading.state = EmptyLoadingState.LOADING
        })

        (viewModel as BrowserModel).getDescriptionUpdate().observe(viewLifecycleOwner, Observer { pair ->
            if (pair != null) (adapter as RecyclerView.Adapter<*>).notifyItemChanged(pair.first, pair.second)
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
                val did = object : VLCDividerItemDecoration(requireActivity(), HORIZONTAL, VectorDrawableCompat.create(requireActivity().resources, R.drawable.ic_divider, requireActivity().theme)!!) {
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
                did.setDrawable(VectorDrawableCompat.create(requireActivity().resources, R.drawable.ic_divider, requireActivity().theme)!!)
                ariane.addItemDecoration(did)
            }
            ariane.scrollToPosition(ariane.adapter!!.itemCount - 1)
        } else ariane.visibility = View.GONE
        animationDelegate.setVisibility(binding.title, if (ariane.visibility == View.GONE) View.VISIBLE else View.GONE)
    }

    override fun backTo(tag: String) {
        if (tag == "root") {
            requireActivity().finish()
            return
        }
        val supportFragmentManager = requireActivity().supportFragmentManager
        var poped = false
        for (i in 0 until supportFragmentManager.backStackEntryCount) {
            if (tag == supportFragmentManager.getBackStackEntryAt(i).name) {
                supportFragmentManager.popBackStack(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                poped = true
                break
            }
        }
        if (!poped) {
            if (supportFragmentManager.backStackEntryCount == 0) browse(MLServiceLocator.getAbstractMediaWrapper(Uri.parse(tag)), false)
            else {
                (viewModel as IPathOperationDelegate).setDestination(MLServiceLocator.getAbstractMediaWrapper(Uri.parse(tag)))
                supportFragmentManager.popBackStack()
            }
        }
    }

    override fun currentContext(): Context = requireActivity()

    override fun showRoot(): Boolean = true

    override fun getPathOperationDelegate() = viewModel as IPathOperationDelegate

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        lifecycleScope.launch {
            animationDelegate.setVisibility(binding.favoriteButton, if (isRootLevel) View.GONE else View.VISIBLE)
            animationDelegate.setVisibility(binding.imageButtonFavorite, View.VISIBLE)
            animationDelegate.setVisibility(binding.favoriteDescription, View.VISIBLE)
            favExists = (item as? MediaWrapper)?.let { browserFavRepository.browserFavExists(it.uri) } ?: false
            binding.favoriteButton.setImageResource(if (favExists) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outline)
            binding.imageButtonFavorite.setImageResource(if (favExists) R.drawable.ic_fabtvmini_bookmark else R.drawable.ic_fabtvmini_bookmark_outline)
        }
        if (!isRootLevel) binding.favoriteButton.setOnClickListener(favoriteClickListener)
        binding.imageButtonFavorite.setOnClickListener(favoriteClickListener)
        binding.emptyLoading.showNoMedia = false
    }

    override fun onResume() {
        super.onResume()
        if (item == null) (viewModel.provider as BrowserProvider).browseRoot()
        else if (restarted) refresh()
        (viewModel as IPathOperationDelegate).getAndRemoveDestination()?.let {
            browse(it, true)
        }
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

    override fun getCategory() = (viewModel as BrowserModel).type

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        val mediaWrapper = item as MediaWrapper

        mediaWrapper.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
        if (mediaWrapper.type == MediaWrapper.TYPE_DIR) browse(mediaWrapper, true)
        else TvUtil.openMedia(requireActivity(), item, viewModel as BrowserModel)
    }

    fun browse(media: MediaWrapper, save: Boolean) {
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

    private val favoriteClickListener: (View) -> Unit = {
        lifecycleScope.launch {
            val mw = (item as MediaWrapper)
            when {
                browserFavRepository.browserFavExists(mw.uri) -> browserFavRepository.deleteBrowserFav(mw.uri)
                mw.uri.scheme == "file" -> browserFavRepository.addLocalFavItem(mw.uri, mw.title, mw.artworkURL)
                else -> browserFavRepository.addNetworkFavItem(mw.uri, mw.title, mw.artworkURL)
            }
            favExists = !favExists
            if (!isRootLevel) binding.favoriteButton.setImageResource(if (favExists) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outline)
            binding.imageButtonFavorite.setImageResource(if (favExists) R.drawable.ic_fabtvmini_bookmark else R.drawable.ic_fabtvmini_bookmark_outline)
        }
    }

    companion object {
        fun newInstance(type: Long, item: MediaLibraryItem?, root: Boolean = false) =
                FileBrowserTvFragment().apply {
                    arguments = Bundle().apply {
                        this.putLong(CATEGORY, type)
                        this.putParcelable(ITEM, item)
                        this.putBoolean("rootLevel", root)
                    }
                }
    }

    override fun fireDialog(dialog: Dialog) {
        DialogActivity.dialog = dialog
        startActivity(Intent(DialogActivity.KEY_DIALOG, null, activity, DialogActivity::class.java))
    }

    override fun dialogCanceled(dialog: Dialog?) {
        when(dialog) {
            is Dialog.LoginDialog -> goBack()
            is Dialog.ErrorMessage -> {
                view?.let { Snackbar.make(it, "${dialog.title}: ${dialog.text}", Snackbar.LENGTH_LONG).show() }
                goBack()
            }
        }
    }

    private fun goBack() {
        val activity = activity
        if (activity?.isStarted() != true) return
        if (tag == "root") {
            activity.finish()
        } else if  (!activity.isFinishing && !activity.isDestroyed) {
            activity.supportFragmentManager.popBackStack()
        }
    }
}
