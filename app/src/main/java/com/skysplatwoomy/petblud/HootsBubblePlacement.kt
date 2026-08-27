package com.skysplatwoomy.petblud

data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int
        get() = left + (right - left) / 2
}

data class BubblePlacement(
    val x: Int,
    val y: Int,
    val tailAtTop: Boolean,
    val tailCenterX: Int,
)

object HootsBubblePlacement {
    fun place(
        pet: PixelRect,
        bubbleWidth: Int,
        bubbleHeight: Int,
        bounds: PixelBounds,
        gap: Int,
        tailInset: Int,
    ): BubblePlacement {
        val width = bubbleWidth.coerceAtLeast(1)
        val height = bubbleHeight.coerceAtLeast(1)
        val minX = bounds.left
        val maxX = (bounds.right - width).coerceAtLeast(minX)
        val centeredX = pet.centerX - width / 2
        val x = centeredX.coerceIn(minX, maxX)

        val aboveY = pet.top - gap - height
        val belowY = pet.bottom + gap
        val roomAbove = pet.top - gap - bounds.top
        val roomBelow = bounds.bottom - pet.bottom - gap
        val placeAbove = aboveY >= bounds.top || (belowY + height > bounds.bottom && roomAbove >= roomBelow)
        val minY = bounds.top
        val maxY = (bounds.bottom - height).coerceAtLeast(minY)
        val y = (if (placeAbove) aboveY else belowY).coerceIn(minY, maxY)
        val effectiveInset = tailInset.coerceAtMost(width / 2)
        val tailCenter = (pet.centerX - x).coerceIn(effectiveInset, width - effectiveInset)
        return BubblePlacement(
            x = x,
            y = y,
            tailAtTop = !placeAbove,
            tailCenterX = tailCenter,
        )
    }
}
