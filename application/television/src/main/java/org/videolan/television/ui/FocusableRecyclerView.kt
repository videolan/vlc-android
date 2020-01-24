package org.videolan.television.ui

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import org.videolan.resources.interfaces.FocusListener
import org.videolan.vlc.util.getScreenHeight

/**
 * Recyclerview that scrolls the right amount in order to have the focused item always in the middle of the screen
 */

class FocusableRecyclerView : RecyclerView {
    private var focusListener: FocusListener? = null

    private var screenHeight: Int = 0

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        init(context)
    }

    private fun init(context: Context) {
        screenHeight = (context as Activity).getScreenHeight()

        this.focusListener = object : FocusListener {
            override fun onFocusChanged(position: Int) {


                //Center current item on screen
                val originalPos = IntArray(2)

                val view = layoutManager!!.findViewByPosition(position)

                view?.let {
                    it.getLocationInWindow(originalPos)
                    val y = originalPos[1]

                    smoothScrollBy(0, y - screenHeight / 2 + view.height / 2)
                }


            }
        }
    }

    override fun setAdapter(adapter: Adapter<*>?) {

        if (adapter is TvItemAdapter) {
            adapter.setOnFocusChangeListener(focusListener)
        }

        super.setAdapter(adapter)
    }

}
