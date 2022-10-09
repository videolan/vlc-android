package org.videolan.television.ui

import android.content.Context
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable

class GenericCardPresenter @JvmOverloads constructor(context: Context, cardThemeResId: Int = R.style.VLCGenericCardView) : Presenter() {

    val context: ContextThemeWrapper = ContextThemeWrapper(context, cardThemeResId)
    val padding = context.resources.getDimension(org.videolan.vlc.R.dimen.tv_card_padding).toInt()


    init {
    }

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        Log.d(TAG, "onCreateViewHolder")

        val cardView = ImageCardView(context)

        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        return Presenter.ViewHolder(cardView)
    }


    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        if (item is GenericCardItem) {
            val cardView = viewHolder.view as ImageCardView
            val bgColor = ContextCompat.getColor(context, item.color)


            cardView.setInfoAreaBackgroundColor(bgColor)
            cardView.mainImageView.setImageBitmap(context.getBitmapFromDrawable(item.icon))
            cardView.mainImageView.setPadding(padding, padding, padding, padding)
            cardView.titleText = item.title
            cardView.contentText = item.content

            cardView.setBackgroundColor(bgColor)
            cardView.findViewById<View>(R.id.info_field).setBackgroundColor(bgColor)
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        Log.d(TAG, "onUnbindViewHolder")
        val cardView = viewHolder.view as ImageCardView
        // Remove references to images so that the garbage collector can free up memory
        cardView.badgeImage = null
        cardView.mainImage = null
    }

    companion object {
        private const val TAG = "VLC/CardPresenter"
    }
}

data class GenericCardItem(
        val id: Long,
        val title: String,
        val content: String,
        val icon: Int,
        val color: Int
)
