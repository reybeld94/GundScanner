package com.reybel.gunderlinscanner.utils

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BarcodeInputHandler(
    private val onBarcodeScanned: (String) -> Unit
) {
    private var scanBuffer = StringBuilder()
    private var lastInputTime = 0L
    private val scanTimeout = 300L // 300ms timeout to detect scanner
    private val minBarcodeLength = 3

    private var timeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        // Only process KEY_DOWN events
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        val currentTime = System.currentTimeMillis()

        // If too much time has passed since last input, clear buffer
        if (currentTime - lastInputTime > scanTimeout) {
            scanBuffer.clear()
        }

        lastInputTime = currentTime

        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                // Scanner finished, process code
                processScanBuffer()
                return true
            }

            // Alphanumeric characters and common symbols
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9,
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_SPACE -> {

                // Add character to buffer
                val char = getCharFromKeyEvent(keyCode, event)
                if (char != null) {
                    scanBuffer.append(char)

                    // Cancel previous timeout and create new one
                    timeoutJob?.cancel()
                    timeoutJob = scope.launch {
                        delay(scanTimeout)
                        // If no more input, process what we have
                        if (scanBuffer.isNotEmpty()) {
                            processScanBuffer()
                        }
                    }
                }
                return true
            }

            // For any other key, try to capture the character
            else -> {
                val char = getCharFromKeyEvent(keyCode, event)
                if (char != null && (char.isLetterOrDigit() || char == ' ')) {
                    scanBuffer.append(char)

                    // Cancel previous timeout and create new one
                    timeoutJob?.cancel()
                    timeoutJob = scope.launch {
                        delay(scanTimeout)
                        if (scanBuffer.isNotEmpty()) {
                            processScanBuffer()
                        }
                    }
                    return true
                }
            }
        }

        return false
    }

    private fun processScanBuffer() {
        timeoutJob?.cancel()

        val scannedCode = scanBuffer.toString().trim()
        scanBuffer.clear()

        if (scannedCode.length >= minBarcodeLength) {
            Logger.log("🔍 USB Scanner detected: $scannedCode")
            onBarcodeScanned(scannedCode)
        }
    }

    private fun getCharFromKeyEvent(keyCode: Int, event: KeyEvent): Char? {
        return when (keyCode) {
            // Numbers
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                (keyCode - KeyEvent.KEYCODE_0 + '0'.code).toChar()
            }

            // Letters
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                val baseChar = (keyCode - KeyEvent.KEYCODE_A + 'a'.code).toChar()
                if (event.isShiftPressed) baseChar.uppercaseChar() else baseChar
            }

            // Special symbols - Only space now
            KeyEvent.KEYCODE_SPACE -> ' '

            // Try to get character using Android's native method
            else -> {
                try {
                    val unicodeChar = event.unicodeChar
                    if (unicodeChar != 0 && unicodeChar.toChar().isDefined()) {
                        unicodeChar.toChar()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun destroy() {
        timeoutJob?.cancel()
    }
}