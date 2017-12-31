package de.roland_illig

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

class Args(parser: ArgParser) {
    val verbose by parser.flagging(help = "enable verbose mode")
    val output by parser.storing(help = "output file")
    val minLength by parser.storing(help = "minimum length of same-color pixels", transform = String::toInt).default(10)
    val minOverlap by parser.storing(help = "minimum overlap between adjacent lines", transform = String::toInt).default(1)
    val input by parser.positional("SOURCE", help = "source image file")
}

fun main(args: Array<String>) {
    val args = Args(ArgParser(args))
    if (!shrink(File(args.input), args.minLength, args.minOverlap, File(args.output))) {
        System.err.println("No shrinkable area found.")
        System.exit(1)
    }
}

fun shrink(source: File, minLength: Int, minOverlap: Int, target: File): Boolean {
    val img = RGBA(ImageIO.read(source))
    val shrunk = shrink(img, minLength, minOverlap) ?: return false
    ImageIO.write(shrunk.toBufferedImage(), "png", target)
    return true
}

/**
 * Shrinks an image by removing duplicate columns.
 * Pixels are only removed if there are [minLength] of the same color in a row.
 */
fun shrink(img: RGBA, minLength: Int, minOverlap: Int): RGBA? {
    val nodes = findNodes(img, minLength)!!
    val graph = Graph(nodes.toMutableSet(), minOverlap)

    graph.optimize()
    if (graph.nodes.size <= 2) {
        return img
    }

    val byY = graph.nodes.groupBy { it.y }
    val maxLen = byY[img.height - 1]!!.first().len - minLength
    val shrunk = RGBA(img.width - maxLen, img.height)
    for (y in 0 until shrunk.height) {
        val node = byY[y]!!.first()
        for (x in 0 until shrunk.width) {
            shrunk[x, y] = img[if (x >= node.start) (x + maxLen) else x, y]
        }
    }
    return shrunk
}

internal fun findNodes(img: RGBA, minLength: Int): Set<Node>? {
    val nodes = mutableSetOf(Node(true, -1, 0, img.width, img.width, 0x00000000, 0, 0))

    fun addNodesFromRow(y: Int) {
        var eq = 1
        for (x in 0 until img.width) {
            if (x + 1 < img.width && img[x, y] == img[x + 1, y]) {
                eq++
            } else {
                if (eq >= minLength) {
                    val start = x - (eq - 1)
                    nodes += Node(false, y, start, x + 1, eq, img[x, y], 0, 0)
                }
                eq = 1
            }
        }
    }

    for (y in 0 until img.height) {
        addNodesFromRow(y)
        if (nodes.last().y != y) {
            return null
        }
    }

    nodes += Node(true, img.height, 0, img.width, img.width, 0x00000000, 0, 0)

    return nodes.toSet()
}

/**
 * A contiguous block of [len] pixels,
 * somewhere between [start] (inclusive) and [end] (exclusive).
 */
internal class Node(
        val fixed: Boolean,
        val y: Int,
        var start: Int,
        var end: Int,
        var len: Int,
        val color: Int,
        var deltaColor: Int,
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
            return "(%2d,%2d len %2d until %2d prev %d next %d deltaColor %d)"
                    .format(start, y, len, end, prevs.size, nexts.size, deltaColor)
        } else {
            return "(%2d,%2d len %2d until %2d color %08x prev %d next %d deltaColor %d)"
                    .format(start, y, len, end, color, prevs.size, nexts.size, deltaColor)
        }
    }
}

internal class Graph(val nodes: MutableSet<Node>, private val minOverlap: Int) {

    fun optimize() {
        connectAdjacent()
        computeDeltaColor()
        while (removeDead() or adjustLengths() or removeSmall() or adjustBoundsForMinOverlap()) {
        }
        nodes.forEach(::println)
    }

    private fun connectAdjacent() {
        val byY = nodes.groupBy { it.y }
        byY.filterKeys { it >= 0 }.forEach { y, row ->
            for (node in row) {
                for (prev in byY[y - 1]!!) {
                    if (prev.overlaps(node, minOverlap)) {
                        node.prevs += prev
                        prev.nexts += node
                    }
                }
            }
        }
    }

    private fun computeDeltaColor() {
        for (node in nodes.drop(1)) {
            val delta = mutableMapOf<Node, Int>()
            for (it in node.prevs) {
                delta[it] = it.deltaColor + if (it.fixed || node.fixed || it.color != node.color) 1 else 0
            }
            val minDelta = delta.values.min() ?: 2_000_000_000
            delta.filterValues { it != minDelta }.keys.forEach { disconnect(node, it) }
            node.deltaColor = minDelta
            println("Updated deltaColor of $node")
        }
    }

    private fun removeSmall(): Boolean {
        var changed = false
        val minLength = nodes.filter { !it.fixed }.last().len
        for (node in nodes.toList()) {
            if (node.len < minLength) {
                remove(node)
                changed = true
            }
        }
        return changed
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

    private fun disconnect(a: Node, b: Node) {
        a.prevs -= b
        a.nexts -= b
        b.prevs -= a
        b.nexts -= a
    }

    private fun remove(n: Node) {
        n.prevs.forEach { it.nexts -= n }
        n.nexts.forEach { it.prevs -= n }
        n.prevs.clear()
        n.nexts.clear()
        nodes.remove(n)
    }
}
