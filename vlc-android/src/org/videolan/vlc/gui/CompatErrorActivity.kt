package org.videolan.vlc.gui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import org.videolan.libvlc.util.VLCUtil
import org.videolan.vlc.R

class CompatErrorActivity : Activity() {

    /**
     * Simple friendly activity to tell the user something's wrong.
     *
     * Intent parameters (all optional):
     * runtimeError (bool) - Set to true if you want to show a runtime error
     * (defaults to a compatibility error)
     * message (string) - the more detailed problem
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.not_compatible)

        var errorMsg = VLCUtil.getErrorMsg()
        if (intent.getBooleanExtra("runtimeError", false))
            if (intent.getStringExtra("message") != null) {
                errorMsg = intent.getStringExtra("message")
                val tvo = findViewById<View>(R.id.message) as TextView
                tvo.setText(R.string.error_problem)
            }

        val tv = findViewById<View>(R.id.errormsg) as TextView
        tv.text = resources.getString(R.string.error_message_is) + "\n" + errorMsg
    }

    companion object {
        const val TAG = "VLC/CompatErrorActivity"
    }
}
