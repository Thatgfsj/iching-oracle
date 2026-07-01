package com.thatgfsj.iching.data

import kotlinx.serialization.Serializable

/**
 * One I Ching hexagram (one of 64).
 *
 * `binary` is the King Wen bottom-up encoding: bit 0 (LSB) is the
 * lowest line (line 1), bit 5 (MSB) is the top line (line 6). A
 * yang line is 1, yin is 0. So `binary = 63 = 0b111111` is 乾
 * (all yang), and `binary = 0 = 0b000000` is 坤 (all yin).
 *
 * Display order is always bottom-to-top: the first line the user
 * "draws" (line 1) is at the bottom of the visual stack. We
 * pre-compute the six [lines] at parse time so the UI doesn't
 * have to repeat the bit-twiddling on every render.
 */
@Serializable
data class Hexagram(
    val id: Int,
    val name_zh: String,
    val name_pinyin: String,
    val name_en: String,
    val binary: Int,
    val judgment: String,
    val image: String,
) {
    /**
     * Six lines, ordered bottom (position 1) to top (position 6).
     * [kind] is "yang" for solid, "yin" for broken; [glyph] is the
     * visual representation in plain text.
     */
    val lines: List<Line> by lazy {
        (0..5).map { bit ->
            val yang = (binary shr bit) and 1 == 1
            Line(
                position = bit + 1,
                kind = if (yang) LineKind.YANG else LineKind.YIN,
                glyph = if (yang) "━━━━━━━━━━" else "━━    ━━",
            )
        }
    }
}

@Serializable
enum class LineKind {
    @kotlinx.serialization.SerialName("yang") YANG,
    @kotlinx.serialization.SerialName("yin") YIN,
}

@Serializable
data class Line(
    val position: Int,
    val kind: LineKind,
    val glyph: String,
)

/**
 * Top-level shape of `assets/hexagrams.json`.
 *
 * The file is shared with the Flowntier Rust backend (see
 * `crates/pipe-server/src/hexagrams.json`); same schema, same
 * canonical King Wen ordering. Renaming / extending fields
 * here means updating the Rust handler too — keep them in sync.
 */
@Serializable
data class HexagramDataset(
    val schema_version: Int,
    val source: String,
    val note: String,
    val hexagrams: List<Hexagram>,
)