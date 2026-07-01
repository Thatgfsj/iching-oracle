package com.thatgfsj.iching.data

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Loads the 64-hexagram dataset from `assets/hexagrams.json` once
 * per process and exposes [drawRandom] for the UI.
 *
 * Threading: `init` does file I/O, so [getInstance] must be called
 * from the main thread at startup (we call it from
 * `Application.onCreate`). Subsequent [drawRandom] calls are pure
 * CPU and safe from any thread.
 *
 * Why not DataStore / Room: the dataset is static and read-only.
 * Baking it into `assets/` (as a JSON file) keeps the APK small
 * and the build simple. If the dataset ever needs to be
 * per-user-editable, swap the loader here without touching the UI.
 */
class HexagramRepository private constructor(
    private val hexagrams: List<Hexagram>,
) {
    /**
     * Draw one hexagram uniformly at random.
     *
     * Uses `kotlin.random.Random.Default` which is backed by a
     * thread-local XorWow generator; not suitable for crypto but
     * perfectly fine for divination (the user isn't trusting the
     * RNG to be unpredictable — they're trusting the universe).
     */
    fun drawRandom(): Hexagram {
        val idx = Random.nextInt(hexagrams.size)
        return hexagrams[idx]
    }

    /**
     * All 64 hexagram names in canonical King Wen order. Used by the
     * home-screen word cloud; cheap because the list is already in
     * memory after the first `load()`.
     */
    fun allNames(): List<String> = hexagrams.map { it.name_zh }

    companion object {
        @Volatile
        private var INSTANCE: HexagramRepository? = null

        /** Get the singleton, loading the dataset on first call. */
        fun getInstance(context: Context): HexagramRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: load(context.applicationContext).also { INSTANCE = it }
            }
        }

        /**
         * Test-only: inject a pre-built dataset, bypassing the
         * asset loader. Useful for instrumented tests that don't
         * want to depend on `assets/hexagrams.json` being present.
         */
        internal fun forTesting(hexagrams: List<Hexagram>): HexagramRepository =
            HexagramRepository(hexagrams)

        private fun load(context: Context): HexagramRepository {
            val json = context.assets.open("hexagrams.json").use { stream ->
                stream.bufferedReader().readText()
            }
            val parser = Json { ignoreUnknownKeys = true }
            val dataset = parser.decodeFromString(HexagramDataset.serializer(), json)
            require(dataset.hexagrams.size == 64) {
                "hexagrams.json must contain 64 entries, found ${dataset.hexagrams.size}"
            }
            return HexagramRepository(dataset.hexagrams)
        }
    }
}

/**
 * Lazy thread-local RNG so tests can swap in a deterministic one
 * without changing the repository API. Kept package-private
 * intentionally — the UI shouldn't know about RNG internals.
 */
private val Random: kotlin.random.Random = kotlin.random.Random.Default