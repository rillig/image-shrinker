package de.roland_illig

import org.assertj.core.api.Assertions
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO

class ImageShrinkerTest {

    private val softly = SoftAssertions()

    @AfterEach
    fun tearDown() {
        softly.assertAll()
    }

    @Test
    fun testNodeOverlaps() {
        fun node(start: Int, end: Int, len: Int) = Node(false, 0, start, end, len, 0x00000000)

        softly.assertThat(node(140, 150, 10).overlaps(node(150, 160, 10), 1)).isFalse
        softly.assertThat(node(141, 151, 10).overlaps(node(150, 160, 10), 1)).isTrue
        softly.assertThat(node(141, 151, 10).overlaps(node(150, 160, 10), 2)).isFalse
        softly.assertThat(node(150, 160, 10).overlaps(node(150, 160, 10), 10)).isTrue
        softly.assertThat(node(150, 160, 10).overlaps(node(150, 160, 10), 11)).isFalse
    }

    @Test
    fun testBlocksInfoToGraph() {
        val img = parseRGBA(
                "xxxxxxxxxxxxxxxx", // 00:
                "  xxxxxxxxxxxxxx", // 01: shorten
                " xxxxxxxxxxxxxx ", // 02: shift
                "xxxxx    xxxxxxx", // 03: split into 3
                "xxxxxx    xxxxxx", // 04:
                "xxxxxxx    xxxxx", // 05:
                "    xxxxxxxxxx  ", // 06: merge
                "xxxxxxxxxxxxxxxx", // 07: lengthen
                "xxx xxx xxx xxxx", // 08: split into 4
                "xxxx xxx xxx xxx", // 09:
                "xxxxxxxxxxxxxxxx", // 10: merge 4
                "..xxxxx   xxxxxx", // 11: split into 3
                "...xxx     xxx  ", // 12: the dotted pixels are unreachable from the top
                "...xxx          ", // 13: the right side is reachable from the xxx above via a color-change
                "xxxxxxxxxxxxxxxx") // 14:
        val nodes = findNodes(img, 3)!!
        val graph = Graph(nodes.toMutableSet(), 2, false)

        graph.optimize()

        img.markRedundantPixels(graph)

        Assertions.assertThat(img.toCharacters()).isEqualTo("" +
                "///--------xxxxx\n" +
                "  ///----xxxxxxx\n" +
                " ///---xxxxxxxx \n" +
                "///--    xxxxxxx\n" +
                "///---    xxxxxx\n" +
                "xx///--    xxxxx\n" +
                "    ///--xxxxx  \n" +
                "xx///----xxxxxxx\n" +
                "xxx /// xxx xxxx\n" +
                "xxxx /// xxx xxx\n" +
                "xxx///---xxxxxxx\n" +
                "??///--   xxxxxx\n" +
                "???///     xxx  \n" +
                "???///          \n" +
                "x///----xxxxxxxx\n")
    }

    @Test
    fun testScreenshot() {
        val minLength = 20
        val minOverlap = 10
        val img = RGBA(ImageIO.read(File("screenshot.png")))
        val shrunk = shrink(img, minLength, minOverlap, false)!!
        ImageIO.write(shrunk.toBufferedImage(), "png", File("screenshot-marked.png"))
    }
}

fun parseRGBA(vararg pixels: String): RGBA {
    val img = RGBA(pixels[0].length, pixels.size)
    pixels.forEachIndexed { y, row ->
        row.forEachIndexed { x, pixel ->
            img[x, y] = 0xFF000000.toInt() + pixel.toInt() * 0x010101
        }
    }
    return img
}

fun RGBA.toCharacters(): String {
    val sb = StringBuilder()
    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = get(x, y)
            if (pixel == 0xFF787878.toInt()) {
                sb += 'x'
            } else if (pixel == 0xFF202020.toInt()) {
                sb += ' '
            } else if (pixel == 0xFF00FF00.toInt()) {
                sb += '/'
            } else if (pixel == 0xFF80FF80.toInt()) {
                sb += '-'
            } else {
                sb += '?'
            }
        }
        sb += '\n'
    }
    return sb.toString()
}

operator fun StringBuilder.plusAssign(c: Char) {
    append(c)
}

internal fun RGBA.markRedundantPixels(graph: Graph) {
    graph.nodes.filter { !it.fixed }.forEach { node ->
        for (x in node.start until node.end) {
            if (x - node.start < node.len) {
                this[x, node.y] = 0xFF00FF00.toInt()
            } else {
                this[x, node.y] = 0xFF80FF80.toInt()
            }
        }
    }
}
