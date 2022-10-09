package org.videolan.vlc.gui.view

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.videolan.resources.util.HeaderProvider
import org.videolan.tools.Settings
import org.videolan.vlc.R

private const val TAG = "RecyclerSectionItemDecoration"

@SuppressLint("LongLogTag")
class RecyclerSectionItemGridDecoration(private val headerOffset: Int, private val space: Int, private val sideSpace: Int, private val sticky: Boolean, private val nbColumns: Int, private val provider: HeaderProvider) : RecyclerView.ItemDecoration() {

    private lateinit var headerView: View
    private lateinit var header: TextView
    var isList = false

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)

        if (isList && Settings.showHeaders) {
            val pos = parent.getChildAdapterPosition(view)
            if (provider.isFirstInSection(pos)) {
                outRect.top = headerOffset
            }
            if (provider.isLastInSection(pos)) {
                outRect.bottom = space * 2
            }
            return
        }
        if (isList) return

        val pos = parent.getChildAdapterPosition(view)
        val positionForSection = provider.getPositionForSection(pos)
        val isFirstInLine = positionForSection == pos || (pos - positionForSection) % nbColumns == 0
        val isLastInLine = (pos - positionForSection) % nbColumns == nbColumns - 1


        outRect.left = if (isFirstInLine && Settings.showHeaders) sideSpace else space / 2
        outRect.right = if (isLastInLine && Settings.showHeaders) sideSpace else space / 2
        outRect.top = space / 2
        outRect.bottom = space / 2

        if (Settings.showHeaders) for (i in 0 until nbColumns) {
            if ((pos - i) >= 0 && provider.isFirstInSection(pos - i)) {
                outRect.top = headerOffset + space * 2
            }
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)
        if (!Settings.showHeaders) return
        if (provider.liveHeaders.value?.isEmpty != false) {
            return
        }

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
            previousSectionPosition = sectionPosition

            val title = provider.getSectionforPosition(sectionPosition)
            header.text = title
            fixLayoutSize(headerView, parent)
            drawHeader(c, parent.getChildAt(0), headerView)
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
                fixLayoutSize(headerView, parent)
                drawHeader(c, child, headerView)
                drawnPositions.add(i)
            }
        }
    }

    private fun drawHeader(c: Canvas, child: View, headerView: View) {
        if (!Settings.showHeaders) return
        c.save()
        if (sticky) {
            c.translate(0f, (child.top - headerView.height - (space * 1.5).toInt()).coerceAtLeast(0).toFloat())
        } else {
            c.translate(0f, (child.top - headerView.height).toFloat())
        }
        headerView.draw(c)
        c.restore()
    }

    private fun inflateHeaderView(parent: RecyclerView): View {
        if (Settings.showTvUi) {
            return LayoutInflater.from(parent.context).inflate(R.layout.recycler_section_header_tv, parent, false)
        }
        return LayoutInflater.from(parent.context).inflate(R.layout.recycler_section_header, parent, false)
    }

    /**
     * Measures the header view to make sure its size is greater than 0 and will be drawn
     * https://yoda.entelect.co.za/view/9627/how-to-android-recyclerview-item-decorations
     */
    private fun fixLayoutSize(view: View, parent: ViewGroup) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

        val childWidth = ViewGroup.getChildMeasureSpec(widthSpec, 0, view.layoutParams.width)
        val childHeight = ViewGroup.getChildMeasureSpec(heightSpec, parent.paddingTop + parent.paddingBottom, view.layoutParams.height)

        view.measure(childWidth, childHeight)

        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    companion object {
        fun getItemSize(screenWidth: Int, nbColumns: Int, spacing: Int, sideSpacing:Int) = ((screenWidth - (spacing * (nbColumns - 1)) - 2 * sideSpacing).toFloat() / nbColumns).toInt()
    }
}
