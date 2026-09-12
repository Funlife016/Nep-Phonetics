package com.nepalime.keyboard

import android.content.Context
import org.json.JSONObject

/**
 * A single glyph entry from the JSON map: one or more Latin trigger
 * sequences that all produce the same Devanagari value.
 */
data class MapEntry(
    val triggers: List<String>,
    val value: String,
    val type: String
)

/**
 * Loads and indexes nepali_map.json (or a swapped-in variant with the same
 * schema) from assets. This class is purely data — it doesn't know anything
 * about IME/InputConnection, so it's easy to unit test or reuse for another
 * script by dropping in a different JSON file with the same shape.
 *
 * To use a different mapping file (e.g. for a different language/script),
 * just change assetFileName below or pass a different one in when
 * constructing this from NepaliImeService.
 */
class MappingTable(context: Context, assetFileName: String = "nepali_map.json") {

    val consonants = mutableListOf<MapEntry>()
    val independentVowels = mutableListOf<MapEntry>()
    val matras = mutableListOf<MapEntry>()
    val modifiers = mutableListOf<MapEntry>()
    val digits = mutableListOf<MapEntry>()
    val punctuation = mutableListOf<MapEntry>()

    // trigger string -> list of (category name, entry) that use it.
    // A trigger could theoretically appear in more than one category
    // (e.g. "a" is both an independent vowel and a matra) which is why
    // this is a list, not a single value.
    private val triggerIndex = mutableMapOf<String, MutableList<Pair<String, MapEntry>>>()

    // Every prefix of every trigger, e.g. for "chh" this contains "c", "ch", "chh".
    // Used to decide whether the engine should keep waiting for more keys.
    private val prefixSet = mutableSetOf<String>()

    var maxSequenceLength: Int = 4
        private set

    var matchTimeoutMs: Long = 300L
        private set

    init {
        val text = context.assets.open(assetFileName).bufferedReader().use { it.readText() }
        val root = JSONObject(text)

        root.optJSONObject("meta")?.let { meta ->
            maxSequenceLength = meta.optInt("maxSequenceLength", 4)
            matchTimeoutMs = meta.optLong("matchTimeoutMs", 300L)
        }

        loadCategory(root, "consonants", consonants, "consonant")
        loadCategory(root, "independentVowels", independentVowels, "independentVowel")
        loadCategory(root, "matras", matras, "matra")
        loadCategory(root, "modifiers", modifiers, "modifier")
        loadCategory(root, "digits", digits, "digit")
        loadCategory(root, "punctuation", punctuation, "punctuation")
    }

    private fun loadCategory(
        root: JSONObject,
        key: String,
        into: MutableList<MapEntry>,
        defaultType: String
    ) {
        val arr = root.optJSONArray(key) ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val triggersArr = obj.getJSONArray("triggers")
            val triggers = (0 until triggersArr.length()).map { triggersArr.getString(it) }
            val value = obj.getString("value")
            val type = obj.optString("type", defaultType)
            val entry = MapEntry(triggers, value, type)
            into.add(entry)
            for (t in triggers) {
                triggerIndex.getOrPut(t) { mutableListOf() }.add(key to entry)
                for (len in 1..t.length) {
                    prefixSet.add(t.substring(0, len))
                }
            }
        }
    }

    /** True if [seq] is an exact trigger OR could still become one with more keys. */
    fun isPossiblePrefix(seq: String): Boolean = prefixSet.contains(seq)

    /** Exact match within a specific category, e.g. "a" as a matra vs. as an independent vowel. */
    fun findMatch(seq: String, category: String): MapEntry? =
        triggerIndex[seq]?.firstOrNull { it.first == category }?.second

    /** Exact match in any category, first one wins — used as a fallback. */
    fun findAnyMatch(seq: String): Pair<String, MapEntry>? =
        triggerIndex[seq]?.firstOrNull()
}
