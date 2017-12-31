package de.roland_illig

import com.google.common.collect.HashBasedTable
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

class Args(parser: ArgParser) {
    val verbose by parser.flagging(help = "enable verbose mode")
    val output by parser.storing(help = "output file")
    val log by parser.storing(help = "log file")
    val minLength by parser.storing(help = "minimum length of same-color pixels", transform = String::toInt)
    val minOverlap by parser.storing(help = "minimum overlap between adjacent lines", transform = String::toInt).default(1)
    val input by parser.positional("SOURCE", help = "source image file")
}

fun main(args: Array<String>) {
    shrink(File("screenshot.png"), 5, 1, File("screenshot-marked.png"), File("screenshot-marked.log"))
    return

    val args = Args(ArgParser(args))
    shrink(File(args.input), args.minLength, args.minOverlap, File(args.output), File("ImageShrinker.log"))
}


fun shrinkRepeatedly(source: File, minLength: Int, minOverlap: Int) {
    var source = source
    var i = 0
    while (i < 100) {
        val target = File("tmp-$i.png")
        shrink(source, minLength, minOverlap, target, File("tmp-$i.log")) || return
        i++
        source = target
    }
}

fun shrink(source: File, minLength: Int, minOverlap: Int, target: File, log: File): Boolean {
    val img = RGBA(ImageIO.read(source))
    PrintWriter(OutputStreamWriter(FileOutputStream(log))).use { log ->
        val shrunk = markRedundantPixels(img, minLength, minOverlap, log) ?: return false
        ImageIO.write(shrunk.toBufferedImage(), "png", target)
        return true
    }
}

/**
 * Shrinks an image by removing duplicate columns.
 * Pixels are only removed if there are [minLength] of the same color in a row.
 */
fun shrink(img: RGBA, minLength: Int, minOverlap: Int, log: PrintWriter): RGBA? {
    val w = img.width
    val h = img.height

    val info = findHLines(img, minLength) ?: return null
    val starts = reducePossibilities(info.blocks, info.maxLen, minOverlap, log)

    val resw = w - info.maxLen
    val resh = h // TODO
    val res = RGBA(resw, resh)
    for (y in 0 until resh) {
        val start = starts[y]
        for (x in 0 until resw) {
            res[x, y] = img[if (x >= start) (x + info.maxLen) else x, y]
        }
    }
    return res
}

/**
 * Shrinks an image by removing duplicate columns.
 * Pixels are only removed if there are [minLength] of the same color in a row.
 */
fun markRedundantPixels(img: RGBA, minLength: Int, minOverlap: Int, log: PrintWriter): RGBA? {
    val w = img.width
    val h = img.height

    val info = findHLines(img, minLength) ?: return null
    val starts = reducePossibilities(info.blocks, info.maxLen, minOverlap, log)

    val resw = w
    val resh = h // TODO
    val res = RGBA(resw, resh)
    for (y in 0 until resh) {
        val start = starts[y]
        for (x in 0 until resw) {
            res[x, y] = if (x in start until start + info.maxLen) 0x00FF00 else img[x, y]
        }
    }
    return res
}

internal fun findHLines(img: RGBA, minLength: Int): BlocksInfo? {
    val h = img.height
    val hlines = mutableListOf<MutableList<Node>>()
    for (y in 0 until h) {
        val rowBlocks = findHLinesInRow(img, minLength, y)
        if (rowBlocks.isEmpty()) {
            return null
        }
        hlines += rowBlocks
    }
    return BlocksInfo(hlines)
}

private fun findHLinesInRow(img: RGBA, minLength: Int, y: Int): MutableList<Node> {
    val w = img.width
    var eq = 1
    val blocks = mutableListOf<Node>()
    for (x in 0 until w) {
        if (x + 1 < w && img[x, y] == img[x + 1, y]) {
            eq++
        } else {
            if (eq >= minLength) {
                val start = x - (eq - 1)
                blocks += Node(false, y, start, x + 1, eq, img[x, y], 0, 0)
            }
            eq = 1
        }
    }
    return blocks
}

internal fun BlocksInfo.toGraph(minOverlap: Int, img: RGBA): Graph {
    val width = img.width
    val root = Node(true, -1, 0, width, width, 0x00000000, 0, 0)
    val allNodes = mutableListOf(root)
    var prevRow = listOf(root)
    val surroundedBlocks = blocks.toMutableList().also {
        it.add(mutableListOf(Node(true, blocks.lastIndex + 1, 0, width, width, 0x00000000, 0, 0)))
    }

    surroundedBlocks.forEachIndexed { y, row ->
        val rowNodes = mutableListOf<Node>()
        for (block in row) {
            val color = if (y < img.height) img[block.start, y] else 0x00000000
            val node = Node(y == surroundedBlocks.lastIndex, y, block.start, block.end, block.len, color, 0, 0)
            for (prev in prevRow) {
                if (prev.overlaps(node, minOverlap)) {
                    node.prevs += prev
                    prev.nexts += node
                }
            }
            rowNodes += node
        }
        allNodes += rowNodes
        prevRow = rowNodes
    }
    return Graph(allNodes.toMutableSet(), minOverlap)
}

/**
 * A contiguous block of [len] pixels,
 * somewhere between [start] (inclusive) and [end] (exclusive).
 */
internal open class Node(
        val fixed: Boolean,
        val y: Int,
        var start: Int,
        var end: Int,
        var len: Int,
        val color: Int,
        var colorChanges: Int,
        var deltaX: Int
) {

    val prevs = mutableSetOf<Node>()
    val nexts = mutableSetOf<Node>()

    internal fun overlaps(other: Node, minOverlap: Int): Boolean {
        val left = max(start, other.start)
        val right = min(end, other.end)
        val overlap = min(right - left, min(len, other.len))
        return overlap >= minOverlap
    }

    override fun toString(): String {
        if (fixed) {
            return "($start,$y len $len until $end prev ${prevs.size} next ${nexts.size})"
        } else {
            return "($start,$y len $len until $end color ${String.format("%08x", color)} prev ${prevs.size}, next ${nexts.size})"
        }
    }
}

internal class Graph(val nodes: MutableSet<Node>, private val minOverlap: Int) {

    fun reduce() {
        while (removeDead() or adjustLengths() or adjustBoundsForMinOverlap() or removeColorChanges()) {
        }
    }

    private fun removeDead(): Boolean {
        var changed = false
        for (node in nodes.toList()) {
            if (!node.fixed && (node.prevs.none { it.overlaps(node, minOverlap) } || node.nexts.none { it.overlaps(node, minOverlap) })) {
                println("Removing $node")
                remove(node)
                changed = true
            }
        }
        return changed
    }

    private fun adjustLengths(): Boolean {
        var changed = false
        for (node in nodes) {
            val prevlen = node.prevs.map { it.len }.max() ?: continue
            val nextlen = node.nexts.map { it.len }.max() ?: continue
            val nlen = min(prevlen, nextlen)
            if (nlen < node.len) {
                println("Setting length for $node from ${node.len} to $nlen")
                node.len = nlen
                changed = true
            }
        }
        return changed
    }

    private fun adjustBoundsForMinOverlap(): Boolean {
        var changed = false
        for (node in nodes) {
            val minStart = node.start - minOverlap
            val maxEnd = node.end + minOverlap
            if (node.prevs.size == 1 && node.prevs.single().nexts.size == 1) {
                val prev = node.prevs.single()
                if (prev.start < minStart) {
                    println("Adjusting start of $prev to $minStart from $node")
                    prev.start = minStart
                    changed = true
                }
                if (prev.end > maxEnd) {
                    println("Adjusting end of $prev to $maxEnd from $node")
                    prev.end = maxEnd
                    changed = true
                }
            }
            if (node.nexts.size == 1 && node.nexts.single().prevs.size == 1) {
                val next = node.nexts.single()
                if (next.start < minStart) {
                    println("Adjusting start of $next to $minStart from $node")
                    next.start = minStart
                    changed = true
                }
                if (next.end > maxEnd) {
                    println("Adjusting end of $next to $maxEnd from $node")
                    next.end = maxEnd
                    changed = true
                }
            }
        }
        return changed
    }

    private fun removeColorChanges(): Boolean {

        class Path {
            var distance = 0
            val nodes = mutableListOf<Node>()

            fun copy() = Path().also { it.distance = distance; it.nodes += nodes }
        }

        var changed = false
        val distances = HashBasedTable.create<Node, Node, Int>()
        for (node in nodes) {
            for (prev in node.prevs) {
                val change = if (!node.fixed && !prev.fixed && node.color == prev.color) 0 else 1
                for (ancestor in distances.row(prev).keys) {
                    val prevDistance = node.prevs.flatMap { distances.row(it).filterKeys { it == ancestor }.values }.min() ?: 0
                    val curDist = distances.get(node, ancestor)
                    if (curDist == null || curDist > prevDistance + change) {
                        distances.put(node, ancestor, prevDistance + change)
                    }
                }
                distances.put(node, prev, change)
            }
        }
        distances.rowMap().forEach { node, ancestors ->
            ancestors.forEach { ancestor, distance ->
                println("$distance color changes from $node to $ancestor")
            }
        }
        return changed
    }

    private fun remove(n: Node) {
        n.prevs.forEach { it.nexts -= n }
        n.nexts.forEach { it.prevs -= n }
        n.prevs.clear()
        n.nexts.clear()
        nodes.remove(n)
    }
}

internal class BlocksInfo(val blocks: MutableList<MutableList<Node>>) {
    val maxLen = blocks.last().map { it.len }.max()!!
}

private fun reducePossibilities(all: MutableList<MutableList<Node>>, maxLen: Int, minOverlap: Int, log: PrintWriter): List<Int> {
    all.forEach { row -> row.removeIf { it.len < maxLen } }

    val starts = mutableListOf<Node>()
    for (row in all) {
        starts += row.first()
    }

    fun Node.isValidFor(row: Int): Boolean {
        return all[row].any { this.start in it.start until (it.end - maxLen) }
    }

    for (i in starts.indices.reversed()) {
        if (i > 0 && starts[i - 1].start < starts[i].start) {
            val shifted = Node(false, i, starts[i].start, starts[i].start + maxLen, maxLen, starts[i].color, 0, 0)
            if (starts[i].isValidFor(i - 1) && all[i - 1].any { it.overlaps(shifted, minOverlap) }) {
                starts[i - 1] = starts[i]
            }
        }
    }

    for (i in starts.indices) {
        if (i > 0 && starts[i].start < starts[i - 1].start) {
            val shifted = Node(false, i, starts[i - 1].start, starts[i - 1].start + maxLen, maxLen, starts[i - 1].color, 0, 0)
            if (starts[i - 1].isValidFor(i) && all[i].any { it.overlaps(shifted, minOverlap) }) {
                starts[i] = starts[i - 1]
            }
        }
    }
    starts.forEachIndexed { index, start -> log.println("$index: ${all[index]} -> ${start}") }
    return starts.map { it.start }
}

internal fun merge(olds: List<Node>, news: List<Node>, minOverlap: Int): MutableList<Node> {
    val res = mutableListOf<Node>()

    for (old in olds) {
        for (new in news) {
            if (old.overlaps(new, minOverlap)) {
                val block = Node(false, new.y, new.start, new.end, min(old.len, new.len), new.color, 0, 0)
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
    return res
}
