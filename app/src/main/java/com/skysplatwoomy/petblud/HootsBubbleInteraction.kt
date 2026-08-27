package com.skysplatwoomy.petblud

import android.view.WindowManager

object HootsBubbleInteraction {
    fun windowFlags(interactive: Boolean): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            if (interactive) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
}
