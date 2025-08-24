package com.reybel.gunderlinscanner.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reybel.gunderlinscanner.data.ApiClient
import com.reybel.gunderlinscanner.data.models.CommandRequest
import com.reybel.gunderlinscanner.data.models.LogEntry
import com.reybel.gunderlinscanner.utils.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class MainViewModel : ViewModel() {

    companion object {
        // Mapeo de operaciones desde el cliente Python
        private val OPERATION_MAP = mapOf(
            "51" to "OP Laser Cutting",
            "52" to "OP Forming",
            "53" to "OP Welding",
            "54" to "OP QC",
            "55" to "OP Painting"
        )

        // Regex patterns - SIN simbolo #
        private val USER_PATTERN = Pattern.compile("^A(\\d+)$")
        private val WORK_ORDER_PATTERN = Pattern.compile("^(\\d+)O(\\d+)R(\\d+)$")

        // DEBUG: Patrones para testing
        private const val DEBUG_MODE = false  // Cambiar a false para producción
    }

    private val apiClient = ApiClient()

    // LiveData para la UI
    private val _currentUser = MutableLiveData<String?>()
    val currentUser: LiveData<String?> = _currentUser

    private val _isWaitingForAction = MutableLiveData<Boolean>(false)
    val isWaitingForAction: LiveData<Boolean> = _isWaitingForAction

    private val _isServerConnected = MutableLiveData<Boolean>(false)
    val isServerConnected: LiveData<Boolean> = _isServerConnected

    private val _logs = MutableLiveData<List<LogEntry>>(emptyList())
    val logs: LiveData<List<LogEntry>> = _logs

    private val _errorMessage = MutableLiveData<String>("")
    val errorMessage: LiveData<String> = _errorMessage

    private val _successMessage = MutableLiveData<String>("")
    val successMessage: LiveData<String> = _successMessage

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        checkServerConnection()
    }

    fun processScannedCode(code: String) {
        viewModelScope.launch {
            addLog("🔍 Código escaneado: $code")
            Logger.log("📥 Escaneo encolado: $code")

            // DEBUG: Información detallada
            if (DEBUG_MODE) {
                addLog("🔧 DEBUG: Código recibido: '$code'")
                addLog("🔧 DEBUG: Longitud: ${code.length}")
                addLog("🔧 DEBUG: Caracteres: ${code.toCharArray().joinToString(",") { "'$it'" }}")
                addLog("🔧 DEBUG: Patrón usuario: ${USER_PATTERN.pattern()}")
            }

            // Escaneo de usuario
            val userMatcher = USER_PATTERN.matcher(code)
            if (DEBUG_MODE) {
                addLog("🔧 DEBUG: ¿Coincide patrón? ${userMatcher.matches()}")
            }
            if (userMatcher.matches()) {
                val userId = userMatcher.group(1)!!
                _currentUser.value = userId
                _isWaitingForAction.value = true
                addLog("👤 Usuario escaneado: $userId")
                Logger.log("👤 Usuario escaneado: $userId")
                return@launch
            }

            // Verificar que hay un usuario activo
            val currentUserId = _currentUser.value
            if (!_isWaitingForAction.value!! || currentUserId == null) {
                val errorMsg = "⚠️ Escanee primero su ID de usuario."
                addLog(errorMsg)
                Logger.log(errorMsg)
                _errorMessage.value = "PLEASE SCAN YOUR USER ID FIRST"
                return@launch
            }

            // Verificar conectividad del servidor
            if (!_isServerConnected.value!!) {
                val errorMsg = "❌ Servidor no disponible"
                addLog(errorMsg)
                Logger.log(errorMsg)
                _errorMessage.value = "SERVER NOT AVAILABLE"
                return@launch
            }

            // Formato universal: 3136O51R80
            val workOrderMatcher = WORK_ORDER_PATTERN.matcher(code)
            if (workOrderMatcher.matches()) {
                val wo = workOrderMatcher.group(1)!!
                val opId = workOrderMatcher.group(2)!!
                val routerId = workOrderMatcher.group(3)!!

                val opName = OPERATION_MAP[opId]
                if (opName == null) {
                    val errorMsg = "❌ Operación desconocida: $opId"
                    addLog(errorMsg)
                    Logger.log(errorMsg)
                    _errorMessage.value = "UNKNOWN OPERATION ID"
                    return@launch
                }

                processWorkOrder(currentUserId, wo, opName, routerId)
            } else {
                val errorMsg = "❌ Código inválido: $code"
                addLog(errorMsg)
                Logger.log(errorMsg)
                _errorMessage.value = "INVALID BARCODE FORMAT"
            }
        }
    }

    private suspend fun processWorkOrder(userId: String, wo: String, operation: String, routerId: String) {
        _isLoading.value = true

        try {
            addLog("⏳ Enviando comando al servidor...")

            // Enviar comando de clock in
            val commandId = apiClient.sendClockInWorkOrder(
                CommandRequest.ClockInWO(
                    userId = userId,
                    woNumber = wo,
                    operation = operation,
                    routerId = routerId
                )
            )

            addLog("⏳ Comando enviado: $commandId")
            Logger.log("⏳ Comando enviado al servidor: $commandId")

            // Esperar resultado
            val result = apiClient.waitForCommandCompletion(commandId)

            if (result.success) {
                val successMsg = "✅ Clock In exitoso en WO $wo"
                addLog(successMsg)
                Logger.log(successMsg)
                _successMessage.value = "Operación exitosa"
            } else {
                val errorMsg = "❌ Clock In falló: ${result.message}"
                addLog(errorMsg)
                Logger.log(errorMsg)
                _errorMessage.value = "CLOCK IN FAILED"
            }

        } catch (e: Exception) {
            val errorMsg = "❌ Error de comunicación: ${e.message}"
            addLog(errorMsg)
            Logger.log(errorMsg)
            _errorMessage.value = "COMMUNICATION ERROR"
        } finally {
            // Limpiar estados
            _currentUser.value = null
            _isWaitingForAction.value = false
            _isLoading.value = false
        }
    }

    fun checkServerConnection() {
        viewModelScope.launch {
            try {
                val isConnected = apiClient.checkHealth()
                _isServerConnected.value = isConnected

                if (isConnected) {
                    addLog("✅ Servidor conectado correctamente")
                } else {
                    addLog("🔴 Sin conexión al servidor")
                }
            } catch (e: Exception) {
                _isServerConnected.value = false
                addLog("🔴 Error conectando al servidor: ${e.message}")
            }
        }
    }

    fun updateServerUrl(newUrl: String) {
        apiClient.updateBaseUrl(newUrl)
        checkServerConnection()
    }

    fun clearErrorMessage() {
        _errorMessage.value = ""
    }

    fun clearSuccessMessage() {
        _successMessage.value = ""
    }

    private fun addLog(message: String) {
        val currentLogs = _logs.value?.toMutableList() ?: mutableListOf()
        currentLogs.add(LogEntry(
            message = message,
            timestamp = System.currentTimeMillis()
        ))

        // Mantener solo los últimos 100 logs
        if (currentLogs.size > 100) {
            currentLogs.removeAt(0)
        }

        _logs.value = currentLogs
    }

    // Limpiar logs (llamado desde la UI)
    fun clearLogs() {
        _logs.value = emptyList()
    }
}