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
    private val scanTimeout = 300L // 300ms timeout para detectar scanner
    private val minBarcodeLength = 3

    private var timeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        // Solo procesar eventos KEY_DOWN
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        val currentTime = System.currentTimeMillis()

        // Si ha pasado mucho tiempo desde el último input, limpiar buffer
        if (currentTime - lastInputTime > scanTimeout) {
            scanBuffer.clear()
        }

        lastInputTime = currentTime

        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                // Scanner terminó, procesar código
                processScanBuffer()
                return true
            }

            // Caracteres alfanuméricos y símbolos comunes
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9,
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_SPACE -> {

                // Agregar carácter al buffer
                val char = getCharFromKeyEvent(keyCode, event)
                if (char != null) {
                    scanBuffer.append(char)

                    // Cancelar timeout previo y crear uno nuevo
                    timeoutJob?.cancel()
                    timeoutJob = scope.launch {
                        delay(scanTimeout)
                        // Si no hay más input, procesar lo que tenemos
                        if (scanBuffer.isNotEmpty()) {
                            processScanBuffer()
                        }
                    }
                }
                return true
            }

            // Para cualquier otra tecla, intentar capturar el carácter
            else -> {
                val char = getCharFromKeyEvent(keyCode, event)
                if (char != null && (char.isLetterOrDigit() || char == ' ')) {
                    scanBuffer.append(char)

                    // Cancelar timeout previo y crear uno nuevo
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
            Logger.log("🔍 Scanner USB detectado: $scannedCode")
            onBarcodeScanned(scannedCode)
        }
    }

    private fun getCharFromKeyEvent(keyCode: Int, event: KeyEvent): Char? {
        return when (keyCode) {
            // Números
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                (keyCode - KeyEvent.KEYCODE_0 + '0'.code).toChar()
            }

            // Letras
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                val baseChar = (keyCode - KeyEvent.KEYCODE_A + 'a'.code).toChar()
                if (event.isShiftPressed) baseChar.uppercaseChar() else baseChar
            }

            // Símbolos especiales - Solo espacio ahora
            KeyEvent.KEYCODE_SPACE -> ' '

            // Intentar obtener el carácter usando el método nativo de Android
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