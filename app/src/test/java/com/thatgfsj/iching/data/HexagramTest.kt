package com.thatgfsj.iching.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the Hexagram data model + dataset parsing.
 * These run as part of `gradlew test` (the `test` source set)
 * and don't need an Android device or emulator.
 */
class HexagramTest {

    private val parser = Json { ignoreUnknownKeys = true }

    @Test
    fun `lines are computed bottom-up from the binary encoding`() {
        // 乾 (乾为天) — all yang, binary 63 = 0b111111
        val qian = Hexagram(
            id = 1, name_zh = "乾", name_pinyin = "Qián", name_en = "The Creative",
            binary = 63, judgment = "", image = "",
        )
        assertEquals(6, qian.lines.size)
        // bottom-up: position 1 (bit 0) -> yang first
        qian.lines.forEachIndexed { i, line ->
            assertEquals(i + 1, line.position)
            assertEquals(LineKind.YANG, line.kind)
            assertEquals("━━━━━━━━━━", line.glyph)
        }

        // 坤 — all yin, binary 0
        val kun = Hexagram(
            id = 2, name_zh = "坤", name_pinyin = "Kūn", name_en = "The Receptive",
            binary = 0, judgment = "", image = "",
        )
        assertEquals(6, kun.lines.size)
        kun.lines.forEach { line ->
            assertEquals(LineKind.YIN, line.kind)
            assertEquals("━━    ━━", line.glyph)
        }
    }

    @Test
    fun `line kind toggles per bit`() {
        // Mixed: line 1 yang, line 2 yin, line 3 yang, line 4 yin,
        // line 5 yang, line 6 yin -> binary 0b010101 = 21
        val h = Hexagram(
            id = 99, name_zh = "测试", name_pinyin = "Test", name_en = "Test",
            binary = 21, judgment = "", image = "",
        )
        assertEquals(LineKind.YANG, h.lines[0].kind) // position 1, bit 0
        assertEquals(LineKind.YIN, h.lines[1].kind)  // position 2, bit 1
        assertEquals(LineKind.YANG, h.lines[2].kind)
        assertEquals(LineKind.YIN, h.lines[3].kind)
        assertEquals(LineKind.YANG, h.lines[4].kind)
        assertEquals(LineKind.YIN, h.lines[5].kind)
    }

    @Test
    fun `bundled hexagrams json parses cleanly`() {
        // Load the actual asset file shipped with the app.
        // `assets/` is on the classpath of the unit-test task
        // because we copy it into the test classpath via the
        // Gradle config (see app/build.gradle.kts `testOptions`).
        val json = this::class.java.classLoader!!
            .getResourceAsStream("hexagrams.json")!!
            .bufferedReader()
            .readText()

        val dataset = parser.decodeFromString(HexagramDataset.serializer(), json)
        assertEquals("周易 (King Wen sequence, canonical ordering)", dataset.source)
        assertEquals(64, dataset.hexagrams.size)

        // Spot-check the canonical ordering: id 1 is 乾, id 64 is 未济.
        assertEquals("乾", dataset.hexagrams.first().name_zh)
        assertEquals("未济", dataset.hexagrams.last().name_zh)

        // Binary uniqueness: King Wen sequence has a unique
        // 6-bit encoding for each of the 64 hexagrams.
        val binaries = dataset.hexagrams.map { it.binary }.toSet()
        assertEquals(64, binaries.size)

        // Every hexagram must have 6 lines computed from its binary.
        dataset.hexagrams.forEach { h ->
            assertEquals(6, h.lines.size)
        }
    }

    @Test
    fun `repository drawRandom picks within the dataset`() {
        // Build a small in-memory dataset for the test.
        val tiny = (1..3).map { i ->
            Hexagram(
                id = i,
                name_zh = "卦$i",
                name_pinyin = "Gua$i",
                name_en = "Hex $i",
                binary = (1 shl i) - 1, // 1, 3, 7
                judgment = "",
                image = "",
            )
        }
        val repo = HexagramRepository.forTesting(tiny)

        // Draw 50 times — we should hit at least 2 distinct IDs
        // (high probability of all 3). If RNG is degenerate we
        // might see only one; this is a soft assertion.
        val seen = (1..50).map { repo.drawRandom().id }.toSet()
        assertTrue("expected at least 2 distinct draws, got $seen", seen.size >= 2)
        assertTrue("every drawn id must be in the dataset", seen.all { it in 1..3 })
    }
}