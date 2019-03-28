package org.videolan.vlc.gui.view

import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.viewmodels.paged.MLPagedModel

private const val TAG = "RecyclerSectionItemDecoration"

class RecyclerSectionItemGridDecoration(private val headerOffset: Int, private val space: Int, private val sticky: Boolean, private val nbColumns: Int, private val model: MLPagedModel<*>) : RecyclerView.ItemDecoration() {

    private lateinit var headerView: View
    private lateinit var header: TextView

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)

        outRect.left = space
        outRect.right = space
        outRect.top = space
        outRect.bottom = space


        val pos = parent.getChildAdapterPosition(view)
        for (i in 0..(nbColumns - 1)) {
            if ((pos - i) >= 0 && model.isFirstInSection(pos - i)) {
                outRect.top = headerOffset + space
            }
        }

        //determine if first or last in row
//        val sectionPos = model.getPositionForSection(pos)
//        val rangeInSection = pos - sectionPos
//        if (rangeInSection % nbColumns == 0) {
//            outRect.left = space * 2
//        } else if (rangeInSection % nbColumns == nbColumns-1) {
//            outRect.right = space * 2
//        }
//        if (BuildConfig.DEBUG) Log.d("ItemGridDecoration", "Margins for position $pos: ${outRect.left} - ${outRect.right}");


    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

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
            val sectionPosition = model.getPositionForSection(position)
            previousSectionPosition = sectionPosition

            val title = model.getSectionforPosition(sectionPosition)
            header.text = title
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

            val title = model.getSectionforPosition(position)
            header.text = title
            if (model.isFirstInSection(position)) {
                drawHeader(c, child, headerView)
                drawnPositions.add(i)
            }
        }


    }

    private fun drawHeader(c: Canvas, child: View, headerView: View) {
        c.save()
        if (sticky) {
            if (BuildConfig.DEBUG) Log.d("ItemGridDecoration", "Child top: ${child.top}")
            if (BuildConfig.DEBUG) Log.d("ItemGridDecoration", "canvas: ${c.clipBounds}")
            if (BuildConfig.DEBUG) Log.d("ItemGridDecoration", "y: ${Math.max(0, child.top - 0 - headerView.height - space * 2).toFloat()}")
            c.translate(0f, Math.max(0, child.top - headerView.height - (space * 1.5).toInt()).toFloat())
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
                0,
                view.layoutParams.width)
        val childHeight = ViewGroup.getChildMeasureSpec(heightSpec,
                parent.paddingTop + parent.paddingBottom,
                view.layoutParams.height)

        view.measure(childWidth, childHeight)

        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

}