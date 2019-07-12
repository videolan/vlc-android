package org.videolan.tools

import android.view.KeyEvent

object KeyHelper {
    fun manageModifiers(event: KeyEvent) {
        isShiftPressed = event.isShiftPressed
        isCtrlPressed = event.isCtrlPressed
        isAltPressed = event.isAltPressed
        isFunctionPressed = event.isFunctionPressed
    }

    var isShiftPressed = false
    var isCtrlPressed = false
    var isAltPressed = false
    var isFunctionPressed = false
}