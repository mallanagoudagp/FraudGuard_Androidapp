package com.fraudguard.demo

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.content.Context

class TypingIME : InputMethodService() {
    private val TAG = "TypingIME"
    
    override fun onCreateInputView(): View? {
        // Create a simple keyboard layout
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2C2C2C"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Add a title
        val title = TextView(this).apply {
            text = "FraudGuard Keyboard"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(20, 10, 20, 10)
        }
        layout.addView(title)
        
        // Add some basic keys for testing
        val keys = arrayOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J")
        val keyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        
        keys.forEach { key ->
            val button = Button(this).apply {
                text = key
                setOnClickListener {
                    // Send the character to the current input connection
                    currentInputConnection?.commitText(key, 1)
                    // Also send to TypingAgent
                    Agents.typing.onKeyEvent(true, KeyEvent.KEYCODE_A + (key[0] - 'A'), 0.0f)
                    Agents.typing.onKeyEvent(false, KeyEvent.KEYCODE_A + (key[0] - 'A'), 0.0f)
                    Log.d(TAG, "Key pressed: $key")
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(5, 5, 5, 5)
                }
            }
            keyRow.addView(button)
        }
        layout.addView(keyRow)
        
        // Add space and backspace
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        
        val spaceButton = Button(this).apply {
            text = "SPACE"
            setOnClickListener {
                currentInputConnection?.commitText(" ", 1)
                Agents.typing.onKeyEvent(true, KeyEvent.KEYCODE_SPACE, 0.0f)
                Agents.typing.onKeyEvent(false, KeyEvent.KEYCODE_SPACE, 0.0f)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(5, 5, 5, 5)
            }
        }
        
        val backspaceButton = Button(this).apply {
            text = "⌫"
            setOnClickListener {
                currentInputConnection?.deleteSurroundingText(1, 0)
                Agents.typing.onKeyEvent(true, KeyEvent.KEYCODE_DEL, 0.0f)
                Agents.typing.onKeyEvent(false, KeyEvent.KEYCODE_DEL, 0.0f)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(5, 5, 5, 5)
            }
        }
        
        bottomRow.addView(spaceButton)
        bottomRow.addView(backspaceButton)
        layout.addView(bottomRow)
        
        return layout
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Capture ALL key events globally
        Agents.typing.onKeyEvent(true, keyCode, 0.0f)
        Log.d(TAG, "Global key down: $keyCode")
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Capture ALL key events globally
        Agents.typing.onKeyEvent(false, keyCode, 0.0f)
        Log.d(TAG, "Global key up: $keyCode")
        return super.onKeyUp(keyCode, event)
    }
    
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.d(TAG, "IME started input in app: ${attribute?.packageName}")
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        Log.d(TAG, "IME finished input")
    }
}


