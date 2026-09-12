package com.nepalime.keyboard

import android.os.Handler
import android.os.Looper

/**
 * Consumes raw Latin characters one at a time (as they come off the
 * physical keyboard), buffers them to allow multi-key sequences like
 * "t" + "h" -> थ, and calls back with composing/commit updates.
 *
 * Deliberately has no dependency on InputMethodService or InputConnection
 * so it can be unit tested in plain JVM tests.
 *
 * KNOWN LIMITATION (TODO): general consonant clusters via virama-stacking
 * (e.g. "sth" -> स्त + ... ) are NOT yet implemented — right now, typing
 * two consonants in a row just commits the first with its inherent vowel,
 * then starts the second fresh. Explicit conjuncts (क्ष, ज्ञ, त्र, श्र) work
 * because they're listed directly as consonant entries with type "conjunct"
 * in the JSON map. Fixing general clustering means: when a second consonant
 * follows a just-committed bare consonant within the buffer, replace the
 * trailing inherent vowel with a virama instead of committing it outright.
 * Left as a follow-up once the basic flow is verified on-device.
 */
class TransliterationEngine(
    private val table: MappingTable,
    private val onUpdateComposing: (String) -> Unit,
    private val onCommit: (String) -> Unit
) {
    private val timeoutMs = table.matchTimeoutMs
    private val handler = Handler(Looper.getMainLooper())
    private var pendingBuffer = StringBuilder()
    private var lastCommittedWasConsonant = false

    private val timeoutRunnable = Runnable { resolvePending() }

    /** Call for every printable Latin letter the physical keyboard produces. */
    fun onLatinChar(c: Char) {
        handler.removeCallbacks(timeoutRunnable)
        val candidate = pendingBuffer.toString() + c

        if (table.isPossiblePrefix(candidate)) {
            // Could still extend into a longer trigger (e.g. "t" -> "th").
            // Show the best current guess and wait briefly for another key.
            pendingBuffer.append(c)
            renderComposingPreview()
            handler.postDelayed(timeoutRunnable, timeoutMs)
        } else {
            // This char doesn't extend the current buffer into any known
            // trigger. Resolve what we have, then try the new char fresh.
            resolvePending()
            if (table.isPossiblePrefix(c.toString())) {
                pendingBuffer.append(c)
                renderComposingPreview()
                handler.postDelayed(timeoutRunnable, timeoutMs)
            } else {
                // Not part of any mapping at all (e.g. an unmapped letter) — pass through raw.
                onCommit(c.toString())
            }
        }
    }

    /** Call for space, unmapped punctuation, arrow keys, focus changes, etc. */
    fun onBoundary(literal: String? = null) {
        handler.removeCallbacks(timeoutRunnable)
        resolvePending()
        lastCommittedWasConsonant = false
        if (literal != null) onCommit(literal)
    }

    /**
     * Call when Backspace is pressed.
     * @return true if the engine consumed it (there was pending, uncommitted
     * Latin input to unwind), false if the IME should fall through to
     * deleting the previously committed character normally.
     */
    fun onBackspace(): Boolean {
        handler.removeCallbacks(timeoutRunnable)
        if (pendingBuffer.isNotEmpty()) {
            pendingBuffer.deleteCharAt(pendingBuffer.length - 1)
            if (pendingBuffer.isEmpty()) {
                onUpdateComposing("")
            } else {
                renderComposingPreview()
                handler.postDelayed(timeoutRunnable, timeoutMs)
            }
            return true
        }
        return false
    }

    private fun bestMatchFor(seq: String): Pair<String, MapEntry>? {
        if (seq.isEmpty()) return null
        // After a bare consonant, a vowel sequence should resolve to its
        // matra form (क + ा) rather than its independent form (क + आ).
        val preferredCategory = if (lastCommittedWasConsonant) "matras" else "independentVowels"
        table.findMatch(seq, preferredCategory)?.let { return preferredCategory to it }
        return table.findAnyMatch(seq)
    }

    private fun renderComposingPreview() {
        val seq = pendingBuffer.toString()
        val match = bestMatchFor(seq)
        onUpdateComposing(match?.second?.value ?: seq)
    }

    private fun resolvePending() {
        if (pendingBuffer.isEmpty()) return
        val seq = pendingBuffer.toString()
        val match = bestMatchFor(seq)
        if (match != null) {
            val (category, entry) = match
            onCommit(entry.value)
            lastCommittedWasConsonant = (category == "consonants" || entry.type == "consonant")
        } else {
            // Unmapped sequence: never silently drop input, fall back to raw Latin.
            onCommit(seq)
            lastCommittedWasConsonant = false
        }
        pendingBuffer = StringBuilder()
    }
}
