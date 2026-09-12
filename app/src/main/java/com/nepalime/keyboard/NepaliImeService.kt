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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // TODO: check currentInputEditorInfo.inputType and skip transliteration
        // entirely for password / numeric-only / email fields.

        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                if (engine.onBackspace()) return true
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_SPACE -> {
                engine.onBoundary(" ")
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                engine.onBoundary()
                // Let the system handle newline vs. IME action (submit/search/etc).
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_TAB,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_MOVE_HOME,
            KeyEvent.KEYCODE_MOVE_END -> {
                engine.onBoundary()
                return super.onKeyDown(keyCode, event)
            }
        }

        val unicodeChar = event.getUnicodeChar(event.metaState)
        if (unicodeChar == 0) {
            return super.onKeyDown(keyCode, event)
        }
        val c = unicodeChar.toChar()

        return if (c.isLetter()) {
            engine.onLatinChar(c)
            true
        } else {
            // Digits/punctuation: the engine will check the map (e.g. Devanagari
            // digits, danda for '.') and fall back to the literal char otherwise.
            engine.onLatinChar(c)
            true
        }
    }
}
