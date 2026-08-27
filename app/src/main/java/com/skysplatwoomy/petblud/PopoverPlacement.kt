package com.skysplatwoomy.petblud

data class PixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}

data class PixelPoint(val x: Int, val y: Int)

object PopoverPlacement {
    fun place(
        anchorX: Int,
        anchorY: Int,
        menuWidth: Int,
        menuHeight: Int,
        bounds: PixelBounds,
        gap: Int,
    ): PixelPoint {
        val width = menuWidth.coerceAtMost(bounds.width)
        val height = menuHeight.coerceAtMost(bounds.height)
        val leftSpace = anchorX - bounds.left
        val rightSpace = bounds.right - anchorX
        val aboveSpace = anchorY - bounds.top
        val belowSpace = bounds.bottom - anchorY

        val rawX: Int
        val rawY: Int
        if (maxOf(leftSpace, rightSpace) >= width + gap) {
            rawX = if (rightSpace >= leftSpace) anchorX + gap else anchorX - gap - width
            rawY = anchorY - height / 4
        } else {
            rawX = anchorX - width / 2
            rawY = if (belowSpace >= aboveSpace) anchorY + gap else anchorY - gap - height
        }

        return PixelPoint(
            x = rawX.coerceIn(bounds.left, (bounds.right - width).coerceAtLeast(bounds.left)),
            y = rawY.coerceIn(bounds.top, (bounds.bottom - height).coerceAtLeast(bounds.top)),
        )
    }
}
