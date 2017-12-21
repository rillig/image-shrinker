package de.roland_illig

import org.assertj.core.api.JUnitSoftAssertions
import org.junit.Rule
import org.junit.Test

internal class ImageShrinkerTest {

    @Rule
    @JvmField
    val softly = JUnitSoftAssertions()

    @Test
    internal fun testMerge() {

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

    private fun assertMerged(ranges1: String, ranges2: String) = BlockAssertions(ranges1, ranges2)

    private inner class BlockAssertions(val ranges1: String, val ranges2: String) {

        infix fun isEqualTo(ranges: String) {
            softly.assertThat(merge(parse(ranges1), parse(ranges2))).isEqualTo(parse(ranges))
        }

        private fun parse(ranges: String): List<Block> {
            return ranges.split(Regex(",\\s*")).map {
                val ok = (Regex("""(\d+)-(\d+)(?:\((\d+)\))?""").matchEntire(it) ?: throw IllegalArgumentException(it)).groupValues
                val start = ok[1].toInt()
                val end = ok[2].toInt()
                val len = if (ok[3] != "") ok[3].toInt() else end - start
                Block(start, end, len)
            }
        }
    }
}
