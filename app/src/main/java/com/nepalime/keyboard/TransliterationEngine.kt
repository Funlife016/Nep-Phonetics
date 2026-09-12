package com.nepalime.keyboard

import android.os.Handler
import android.os.Looper

class TransliterationEngine(
    private val table: MappingTable,
    private val onUpdateComposing: (String) -> Unit,
    private val onCommit: (String) -> Unit,
    private val onLog: (String) -> Unit = {}
) {
    private val timeoutMs = table.matchTimeoutMs
    private val handler = Handler(Looper.getMainLooper())
    private var pendingBuffer = StringBuilder()
    private var lastCommittedWasConsonant = false

    private val timeoutRunnable = Runnable {
        onLog("engine: timeout fired, resolving pending='${pendingBuffer}'")
        resolvePending()
    }

    fun onLatinChar(c: Char) {
        handler.removeCallbacks(timeoutRunnable)
        val candidate = pendingBuffer.toString() + c
        onLog("engine: onLatinChar('$c') pending='${pendingBuffer}' candidate='$candidate'")

        if (table.isPossiblePrefix(candidate)) {
            pendingBuffer.append(c)
            renderComposingPreview()
            handler.postDelayed(timeoutRunnable, timeoutMs)
        } else {
            resolvePending()
            if (table.isPossiblePrefix(c.toString())) {
                pendingBuffer.append(c)
                renderComposingPreview()
                handler.postDelayed(timeoutRunnable, timeoutMs)
            } else {
                onLog("engine: '$c' not in map at all, passthrough commit")
                onCommit(c.toString())
            }
        }
    }

    fun onBoundary(literal: String? = null) {
        onLog("engine: onBoundary(literal='$literal') pending='${pendingBuffer}'")
        handler.removeCallbacks(timeoutRunnable)
        resolvePending()
        lastCommittedWasConsonant = false
        if (literal != null) onCommit(literal)
    }

    fun onBackspace(): Boolean {
        handler.removeCallbacks(timeoutRunnable)
        onLog("engine: onBackspace pending='${pendingBuffer}'")
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
        val preferredCategory = if (lastCommittedWasConsonant) "matras" else "independentVowels"
        table.findMatch(seq, preferredCategory)?.let { return preferredCategory to it }
        return table.findAnyMatch(seq)
    }

    private fun renderComposingPreview() {
        val seq = pendingBuffer.toString()
        val match = bestMatchFor(seq)
        val preview = match?.second?.value ?: seq
        onLog("engine: preview seq='$seq' -> '$preview'")
        onUpdateComposing(preview)
    }

    private fun resolvePending() {
        if (pendingBuffer.isEmpty()) return
        val seq = pendingBuffer.toString()
        val match = bestMatchFor(seq)
        if (match != null) {
            val (category, entry) = match
            onLog("engine: COMMIT seq='$seq' -> '${entry.value}' (category=$category)")
            onCommit(entry.value)
            lastCommittedWasConsonant = (category == "consonants" || entry.type == "consonant")
        } else {
            onLog("engine: COMMIT unmapped seq='$seq' as raw text")
            onCommit(seq)
            lastCommittedWasConsonant = false
        }
        pendingBuffer = StringBuilder()
    }
}
