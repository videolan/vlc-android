package org.videolan.vlc.gui.view

import android.graphics.Canvas
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import kotlin.math.max

private const val TAG = "RecyclerSectionItemDecoration"

class RecyclerSectionItemDecoration(private val headerOffset: Int, private val sticky: Boolean, private val provider: MedialibraryProvider<*>) : RecyclerView.ItemDecoration() {

    private lateinit var headerView: View
    private lateinit var header: TextView

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)

        val pos = parent.getChildAdapterPosition(view)
        if (Settings.showHeaders && provider.isFirstInSection(pos)) {
            outRect.top = headerOffset
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)
        if (!Settings.showHeaders) return

        if (!::headerView.isInitialized) {
            headerView = inflateHeaderView(parent)
            header = headerView.findViewById(R.id.section_header) as TextView
            fixLayoutSize(headerView, parent)
        }


        //draw current header
        //look if previous header has been drawn
        var previousSectionPosition = 0

        val previousChild = parent.getChildAt(0)
        if (sticky && previousChild != null) {
            val position = parent.getChildAdapterPosition(previousChild)
            val sectionPosition = provider.getPositionForSection(position)
            if (provider.getHeaderForPostion(sectionPosition) != null) {
                previousSectionPosition = sectionPosition

                val title = provider.getSectionforPosition(sectionPosition)
                header.text = title
                drawHeader(c, parent.getChildAt(0), headerView)
            }
        }

        val drawnPositions = ArrayList<Int>()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == previousSectionPosition) {
                //prevent re-drawing the previous drawer
                continue
            }

            val title = provider.getSectionforPosition(position)
            header.text = title
            if (provider.isFirstInSection(position)) {
                drawHeader(c, child, headerView)
                drawnPositions.add(i)
            }
        }


    }

    private fun drawHeader(c: Canvas, child: View, headerView: View) {
        if (!Settings.showHeaders) return
        c.save()
        if (sticky) {
            c.translate(0f, max(0, child.top - headerView.height).toFloat())
        } else {
            c.translate(0f, (child.top - headerView.height).toFloat())
        }
        headerView.draw(c)
        c.restore()
    }

    private fun inflateHeaderView(parent: RecyclerView): View {
        return LayoutInflater.from(parent.context)
                .inflate(R.layout.recycler_section_header, parent, false)
    }

    /**
     * Measures the header view to make sure its size is greater than 0 and will be drawn
     * https://yoda.entelect.co.za/view/9627/how-to-android-recyclerview-item-decorations
     */
    private fun fixLayoutSize(view: View, parent: ViewGroup) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width,
                View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height,
                View.MeasureSpec.UNSPECIFIED)

        val childWidth = ViewGroup.getChildMeasureSpec(widthSpec,
                parent.paddingLeft + parent.paddingRight,
                view.layoutParams.width)
        val childHeight = ViewGroup.getChildMeasureSpec(heightSpec,
                parent.paddingTop + parent.paddingBottom,
                view.layoutParams.height)

        view.measure(childWidth, childHeight)

        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

}