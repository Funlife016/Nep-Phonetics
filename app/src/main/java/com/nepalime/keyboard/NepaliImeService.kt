package com.nepalime.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo

/**
 * Physical-keyboard-first IME. Deliberately has no soft keyboard UI —
 * onCreateInputView returns a zero-size view and onEvaluateInputViewShown
 * returns false, so it stays out of the way when a hardware keyboard is
 * attached (which is the only scenario this IME is designed for).
 */
class NepaliImeService : InputMethodService() {

    private lateinit var table: MappingTable
    private lateinit var engine: TransliterationEngine

    override fun onCreate() {
        super.onCreate()
        table = MappingTable(this)
        engine = TransliterationEngine(
            table = table,
            onUpdateComposing = { preview ->
                currentInputConnection?.setComposingText(preview, 1)
            },
            onCommit = { finalText ->
                currentInputConnection?.finishComposingText()
                currentInputConnection?.commitText(finalText, 1)
            }
        )
    }

    override fun onCreateInputView(): View {
        return View(this).apply {
            layoutParams = ViewGroup.LayoutParams(0, 0)
        }
    }

    override fun onEvaluateInputViewShown(): Boolean = false

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        engine.onBoundary()
    }

    // Tracks which key codes WE consumed on the way down, so we consume the
    // matching key-up too. If a down/up pair is only half-consumed, some
    // devices fall back to inserting the raw character directly into the
    // EditText in addition to whatever we committed — that's what caused
    // the doubled/"mesh" output.
    private val consumedKeyCodes = mutableSetOf<Int>()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handled = handleKeyDown(keyCode, event)
        if (handled) consumedKeyCodes.add(keyCode) else consumedKeyCodes.remove(keyCode)
        return handled
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return if (consumedKeyCodes.remove(keyCode)) {
            true // swallow it — we already handled the down half ourselves
        } else {
            super.onKeyUp(keyCode, event)
        }
    }

    private fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // TODO: check currentInputEditorInfo.inputType and skip transliteration
        // entirely for password / numeric-only / email fields.

        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                return engine.onBackspace() // false -> let system handle normal delete
            }
            KeyEvent.KEYCODE_SPACE -> {
                engine.onBoundary(" ")
                return true
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_TAB,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_MOVE_HOME,
            KeyEvent.KEYCODE_MOVE_END -> {
                engine.onBoundary()
                return false // not consumed by us -> system handles nav/newline/action normally
            }
        }

        val unicodeChar = event.getUnicodeChar(event.metaState)
        if (unicodeChar == 0) {
            return false
        }
        val c = unicodeChar.toChar()

        // Letters go through the transliteration engine. Digits/punctuation
        // also go through it — the engine checks the map (e.g. Devanagari
        // digits, danda for '.') and falls back to the literal char if
        // there's no mapping, so this branch covers both cases the same way.
        engine.onLatinChar(c)
        return true
    }
}
