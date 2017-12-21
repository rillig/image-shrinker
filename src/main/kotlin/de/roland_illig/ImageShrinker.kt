package de.roland_illig

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage: ImageShrinker <source-image> [<min-keep>] [<target-image>]")
        System.exit(1)
    }

    val source = File(args[0])
    val min = args.getOrElse(1, { "5" }).toInt()
    val target = File(args.getOrElse(2, { args[0] }))

    var i = 0
    var img = ImageIO.read(source)
    while (true) {
        val shrunk = shrink(img, min)
        if (shrunk === img) {
            break
        }
        ImageIO.write(shrunk, "png", File("tmp-$i.png"))
        i++
        img = shrunk
    }
}

/**
 * Shrinks an image by removing duplicate rows or columns.
 * Keeps at least _min_ equal rows or columns.
 */
fun shrink(img: BufferedImage, min: Int): BufferedImage {
    val w = img.width
    val h = img.height

    val all = mutableListOf<List<Block>>()
    var shrinkableRows = listOf(Block(0, w, w))
    for (y in 0 until h) {
        var eq = 1
        val nextShrinkableRows = mutableListOf<Block>()
        for (x in 0 until w + 1) {
            if (x + 1 < w && img.getRGB(x, y) == img.getRGB(x + 1, y)) {
                eq++
            } else {
                if (eq >= min) {
                    val start = x - (eq - 1)
                    nextShrinkableRows += Block(start, x + 1, eq)
                }
                eq = 1
            }
        }

        shrinkableRows = merge(shrinkableRows, nextShrinkableRows)
        all.add(shrinkableRows)
    }

    val maxRem = shrinkableRows.maxBy { it.len } ?: return img
    all.replaceAll { row -> row.filter { it.len >= maxRem.len } }

    for (i in 0 until all.size step 10) {
        println("$i: ${all[i]}")
    }
    println(shrinkableRows)

    val resw = w - maxRem.len
    val resh = h // TODO
    val res = BufferedImage(resw, resh, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until resh) {
        val block = all[y].last()
        for (x in 0 until resw) {
            res.setRGB(x, y, img.getRGB(if (x >= block.start) (x + maxRem.len) else x, y))
        }
    }
    return res
}

fun merge(olds: List<Block>, news: List<Block>, minOverlap: Int = 1): List<Block> {
    val res = mutableListOf<Block>()

    for (old in olds) {
        for (new in news) {
            if (min(old.end, new.end) - max(old.start, new.start) >= minOverlap) {
                val block = Block(new.start, new.end, min(old.len, new.len))
                var found = false
                res.forEachIndexed { index, it ->
                    if (it.start == block.start && it.end == block.end) {
                        found = true
                        if (it.len < block.len) {
                            res[index] = block
                        }
                    }
                }
                if (!found) {
                    res += block
                }
            }
        }
    }
    return res.toList()
}

/**
 * A contiguous block of pixels with the given length,
 * somewhere between [start] (inclusive) and [end] (exclusive).
 */
data class Block(val start: Int, val end: Int, val len: Int) {
    init {
        assert(len <= end - start)
    }

    override fun toString() = "$start-$end($len)"
}
