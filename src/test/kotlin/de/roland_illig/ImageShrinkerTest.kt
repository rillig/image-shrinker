package de.roland_illig

import org.assertj.core.api.JUnitSoftAssertions
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

internal class ImageShrinkerTest {

    @Rule
    @JvmField
    val softly = JUnitSoftAssertions()

    @Test
    internal fun testMerge() {

        class BlockAssertions(val ranges1: String, val ranges2: String) {

            infix fun isEqualTo(ranges: String) {
                softly.assertThat(merge(parse(ranges1), parse(ranges2), 1)).isEqualTo(parse(ranges))
            }

            private fun parse(ranges: String): List<Node> {
                return ranges.split(Regex(",\\s*")).map {
                    val ok = (Regex("""(\d+)-(\d+)(?:\((\d+)\))?""").matchEntire(it) ?: throw IllegalArgumentException(it)).groupValues
                    val start = ok[1].toInt()
                    val end = ok[2].toInt()
                    val len = if (ok[3] != "") ok[3].toInt() else end - start
                    Node(false, 0, start, end, len, 0x00000000)
                }
            }
        }

        fun assertMerged(ranges1: String, ranges2: String) = BlockAssertions(ranges1, ranges2)

        // Completely equal
        assertMerged("0-100", "0-100") isEqualTo "0-100"

        // Fully overlapping
        assertMerged("0-100", "0-50") isEqualTo "0-50"
        assertMerged("0-100", "25-75") isEqualTo "25-75"
        assertMerged("0-100", "50-100") isEqualTo "50-100"

        // Partly overlapping
        assertMerged("10-90", "0-50") isEqualTo "0-50"
        assertMerged("10-90", "50-100") isEqualTo "50-100"

        // Multiple ranges
        assertMerged("0-5,10-20,22-30", "0-30") isEqualTo "0-30(10)"
        assertMerged("0-20, 40-70", "10-60") isEqualTo "10-60(30)"
        assertMerged("10-60", "0-20,40-70") isEqualTo "0-20, 40-70"
        assertMerged("0-40, 30-70", "10-60") isEqualTo "10-60(40)"
        assertMerged("10-60", "0-40,30-70") isEqualTo "0-40, 30-70"
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
                "   xxxxxxxxxxxxx", // 01: shorten
                " xxxxxxxxxxxx   ", // 02: shift
                "xxxxx    xxxxxxx", // 03: split into 2
                "xxxxxx    xxxxxx", // 04:
                "xxxxxxx    xxxxx", // 05:
                "    xxxxxxxxxx  ", // 06: merge 2
                "xxxxxxxxxxxxxxxx", // 07: lengthen
                "xxx xxx xxx xxxx", // 08: split into 4
                "xxxx xxx xxx xxx", // 09:
                "xxxxxxxxxxxxxxxx", // 10: merge 4
                "  xxxxx   xxxxxx", // 11: split into 2
                "   xxx     xxx  ", // 12: dead end to the very left, only connected to the bottom
                "   xxx          ", // 13: the right side is no dead end since the xxx overlaps with the spaces
                "xxxxxxxxxxxxxxxx") // 14:
        val blocksInfo = findHLines(img, 3, 2)!!
        val graph = blocksInfo.toGraph(2, img)
        graph.nodes.forEach {
            println(it)
        }
        println("---")
        graph.reduce()
        graph.nodes.forEach {
            println(it)
        }

        ImageIO.write(img.toBufferedImage(), "png", File("testimage-0.png"))
        graph.nodes.filter { !it.fixed }.forEach { node ->
            for (x in node.start until node.end) {
                if (x - node.start < node.len) {
                    img[x, node.y] = 0xFF00FF00.toInt()
                } else {
                    img[x, node.y] = 0xFF80FF80.toInt()
                }
            }
        }
        ImageIO.write(img.toBufferedImage(), "png", File("testimage-1.png"))
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
