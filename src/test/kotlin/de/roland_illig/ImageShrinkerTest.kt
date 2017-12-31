package de.roland_illig

import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO

class ImageShrinkerTest {

    val softly = SoftAssertions()

    @AfterEach
    fun tearDown() {
        softly.assertAll()
    }

    @Test
    fun testNodeOverlaps() {
        fun node(start: Int, end: Int, len: Int) = Node(false, 0, start, end, len, 0x00000000, 0, 0)

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
                "...xxx     xxx  ", // 12: the dotted pixels are a dead end, only connected to the bottom
                "...xxx          ", // 13: the right side is no dead end since the xxx overlaps with the spaces
                "xxxxxxxxxxxxxxxx") // 14:
        val nodes = findNodes(img, 3)!!
        val graph = Graph(nodes.toMutableSet(), 2)

        graph.optimize()

        ImageIO.write(img.toBufferedImage(), "png", File("testimage-0.png"))
        img.markRedundantPixels(graph)
        ImageIO.write(img.toBufferedImage(), "png", File("testimage-1.png"))
    }

    @Test
    fun testScreenshot() {
        val minLength = 20
        val minOverlap = 10
        val img = RGBA(ImageIO.read(File("screenshot.png")))
        val shrunk = shrink(img, minLength, minOverlap)!!
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
