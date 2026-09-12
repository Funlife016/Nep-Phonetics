package com.nepalime.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo

class NepaliImeService : InputMethodService() {

    private lateinit var table: MappingTable
    private lateinit var engine: TransliterationEngine

    override fun onCreate() {
        super.onCreate()
        Logger.init(applicationContext)
        Logger.d("service: onCreate")
        table = MappingTable(this)
        engine = TransliterationEngine(
            table = table,
            onUpdateComposing = { preview ->
                Logger.d("IC: setComposingText('$preview')")
                currentInputConnection?.setComposingText(preview, 1)
            },
            onCommit = { finalText ->
                // commitText() already finalizes/replaces the current composing
                // region on its own. Calling finishComposingText() first would
                // finalize the composing preview into real text, and THEN
                // commitText() would insert the same text again — that was the
                // doubling bug (कक, ततिि, etc).
                Logger.d("IC: commitText('$finalText')")
                currentInputConnection?.commitText(finalText, 1)
            },
            onLog = { Logger.d(it) }
        )
    }

    override fun onCreateInputView(): View {
        return View(this).apply { layoutParams = ViewGroup.LayoutParams(0, 0) }
    }

    override fun onEvaluateInputViewShown(): Boolean = false

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Logger.d("service: onStartInput restarting=$restarting inputType=${attribute?.inputType}")
        engine.onBoundary()
    }

    private val consumedKeyCodes = mutableSetOf<Int>()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        Logger.d(
            "KEY DOWN code=$keyCode unicode=${event.getUnicodeChar(event.metaState)} " +
                "repeatCount=${event.repeatCount} downTime=${event.downTime} eventTime=${event.eventTime} " +
                "deviceId=${event.deviceId} source=${event.source}"
        )

        if (event.repeatCount > 0) {
            Logger.d("KEY DOWN ignored (auto-repeat)")
            return true
        }

        val handled = handleKeyDown(keyCode, event)
        Logger.d("KEY DOWN code=$keyCode handled=$handled")
        if (handled) consumedKeyCodes.add(keyCode) else consumedKeyCodes.remove(keyCode)
        return handled
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val wasConsumed = consumedKeyCodes.remove(keyCode)
        Logger.d("KEY UP code=$keyCode wasConsumedOnDown=$wasConsumed")
        return if (wasConsumed) true else super.onKeyUp(keyCode, event)
    }

    private fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // TODO: check currentInputEditorInfo.inputType and skip transliteration
        // entirely for password / numeric-only / email fields.

        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                return engine.onBackspace()
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
                return false
            }
        }

        val unicodeChar = event.getUnicodeChar(event.metaState)
        if (unicodeChar == 0) {
            return false
        }
        val c = unicodeChar.toChar()
        engine.onLatinChar(c)
        return true
    }
}
