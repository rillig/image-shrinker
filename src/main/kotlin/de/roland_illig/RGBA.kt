package de.roland_illig

import java.awt.image.BufferedImage

class RGBA(val width: Int, val height: Int) {

    private val data: Array<IntArray> = Array(width) { IntArray(height) }

    constructor(img: BufferedImage) : this(img.width, img.height) {
        for (j in 0 until height) {
            for (i in 0 until width) {
                data[i][j] = img.getRGB(i, j)
            }
        }
    }

    fun regionEquals(x: Int, y: Int, sub: RGBA): Boolean {
        val width = sub.width
        val height = sub.height

        for (j in 0 until height) {
            for (i in 0 until width) {
                if (data[x + i][y + j] != sub.data[i][j] && !isTransparent(sub.data[i][j])) {
                    return false
                }
            }
        }
        return true
    }

    fun toBufferedImage(): BufferedImage {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (j in 0 until height) {
            for (i in 0 until width) {
                img.setRGB(i, j, data[i][j])
            }
        }
        return img
    }

    fun findAll(sub: RGBA): List<Rect> {
        val subWidth = sub.width
        val subHeight = sub.height

        if (subWidth == 0 || subHeight == 0) {
            return listOf()
        }

        val positions = mutableListOf<Rect>()
        val imax = 1 + width - subWidth
        val jmax = 1 + height - subHeight

        for (j in 0 until jmax) {
            for (i in 0 until imax) {
                val rect = Rect(i, j, i + subWidth, j + subHeight)
                if (!positions.any { it overlaps rect }) {
                    if (regionEquals(i, j, sub)) {
                        positions += rect
                    }
                }
            }
        }
        return positions
    }

    fun replace(from: RGBA, to: RGBA) {
        if (from.width == 0 || from.height == 0 || to.width == 0 || to.height == 0) {
            return
        }

        val positions = findAll(from)
        for ((x, y) in positions) {
            draw(x, y, to)
        }
    }

    fun draw(x: Int, y: Int, sub: RGBA) {
        val width = Math.min(sub.width, this.width - x)
        val height = Math.min(sub.height, this.height - y)

        for (j in 0 until height) {
            for (i in 0 until width) {
                data[x + i][y + j] = mix(data[x + i][y + j], sub.data[i][j])
            }
        }
    }

    operator fun get(x: Int, y: Int): Int = data[x][y]

    operator fun set(x: Int, y: Int, color: Int) {
        data[x][y] = color
    }

    private fun isTransparent(rgba: Int) = alpha(rgba) == 0
    private fun alpha(rgba: Int) = (rgba shr 24) and 0xFF

    private fun mixChannel(old: Int, new: Int, shift: Int, alpha: Int): Int {
        val scaledOld = ((old shr shift) and 0xFF) * (255 - alpha)
        val scaledNew = ((new shr shift) and 0xFF) * alpha
        return (scaledOld + scaledNew + 127) / 255
    }

    private fun mix(old: Int, new: Int): Int {
        val alpha = alpha(new)
        val a = mixChannel(old, new, 24, alpha)
        val r = mixChannel(old, new, 16, alpha)
        val g = mixChannel(old, new, 8, alpha)
        val b = mixChannel(old, new, 0, alpha)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    data class Rect(val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
        infix fun overlaps(other: Rect): Boolean {
            return (other.x1 in x1 until x2 || other.x2 in x1 until x2)
                    && (other.y1 in y1 until y2 || other.y2 in y1 until y2)
        }
    }
}
