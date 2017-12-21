package de.roland_illig

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Shrinks an image by removing duplicate rows or columns.
 * Keeps at least _min_ equal rows or columns.
 */
fun shrink(img: BufferedImage, min: Int): BufferedImage {
    val w = img.width
    val h = img.height
    val rows = mutableListOf(0)
    val cols = mutableListOf(0)

    var eq = 1
    for (x in 1 until w) {
        if ((0 until h).all { y -> img.getRGB(x, y) == img.getRGB(x - 1, y) }) {
            eq++
        } else {
            eq = 1
        }
        if (eq <= min) {
            cols += x
        }
    }

    eq = 1
    for (y in 1 until h) {
        if ((0 until w).all { x -> img.getRGB(x, y) == img.getRGB(x, y - 1) }) {
            eq++
        } else {
            eq = 1
        }
        if (eq < min) {
            rows += y
        }
    }

    val res = BufferedImage(cols.size, rows.size, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until rows.size) {
        for (x in 0 until cols.size) {
            res.setRGB(x, y, img.getRGB(cols[x], rows[y]))
        }
    }
    return res
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage: ImageShrinker <source-image> [<min-keep>] [<target-image>]")
        System.exit(1)
    }

    val source = File(args[0])
    val min = args.getOrElse(1, { "5" }).toInt()
    val target = File(args.getOrElse(2, { args[0] }))

    val img = ImageIO.read(source)
    val shrunk = shrink(img, min)
    ImageIO.write(shrunk, "png", target)
}
