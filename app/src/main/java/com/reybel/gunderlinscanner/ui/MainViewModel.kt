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
        // Operation mapping from Python client
        private val OPERATION_MAP = mapOf(
            "51" to "OP Laser Cutting",
            "52" to "OP Forming",
            "53" to "OP Welding",
            "54" to "OP QC",
            "55" to "OP Painting"
        )

        // Regex patterns - WITHOUT # symbol
        private val USER_PATTERN = Pattern.compile("^A(\\d+)$")
        private val WORK_ORDER_PATTERN = Pattern.compile("^(\\d+)O(\\d+)R(\\d+)$")

        // DEBUG: Testing patterns
        private const val DEBUG_MODE = false  // Change to false for production
    }

    private val apiClient = ApiClient()

    // LiveData for UI
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

    // NEW: LiveData for Clock Out dialog
    private val _showQuantityDialog = MutableLiveData<ClockOutRequest?>()
    val showQuantityDialog: LiveData<ClockOutRequest?> = _showQuantityDialog

    data class ClockOutRequest(
        val userId: String,
        val woNumber: String,
        val operation: String,
        val routerId: String
    )

    init {
        checkServerConnection()
    }

    fun processScannedCode(code: String) {
        viewModelScope.launch {
            addLog("🔍 Code scanned: $code")
            Logger.log("📥 Scan queued: $code")

            // DEBUG: Detailed information
            if (DEBUG_MODE) {
                addLog("🔧 DEBUG: Code received: '$code'")
                addLog("🔧 DEBUG: Length: ${code.length}")
                addLog("🔧 DEBUG: Characters: ${code.toCharArray().joinToString(",") { "'$it'" }}")
                addLog("🔧 DEBUG: User pattern: ${USER_PATTERN.pattern()}")
            }

            // User scan
            val userMatcher = USER_PATTERN.matcher(code)
            if (DEBUG_MODE) {
                addLog("🔧 DEBUG: Pattern matches? ${userMatcher.matches()}")
            }
            if (userMatcher.matches()) {
                val userId = userMatcher.group(1)!!
                _currentUser.value = userId
                _isWaitingForAction.value = true
                addLog("👤 User scanned: $userId")
                Logger.log("👤 User scanned: $userId")
                return@launch
            }

            // Verify active user
            val currentUserId = _currentUser.value
            if (!_isWaitingForAction.value!! || currentUserId == null) {
                val errorMsg = "⚠️ Please scan your user ID first."
                addLog(errorMsg)
                Logger.log(errorMsg)
                _errorMessage.value = "PLEASE SCAN YOUR USER ID FIRST"
                return@launch
            }

            // Verify server connectivity
            if (!_isServerConnected.value!!) {
                val errorMsg = "❌ Server not available"
                addLog(errorMsg)
                Logger.log(errorMsg)
                _errorMessage.value = "SERVER NOT AVAILABLE"
                return@launch
            }

            // Universal format: 3136O51R80
            val workOrderMatcher = WORK_ORDER_PATTERN.matcher(code)
            if (workOrderMatcher.matches()) {
                val wo = workOrderMatcher.group(1)!!
                val opId = workOrderMatcher.group(2)!!
                val routerId = workOrderMatcher.group(3)!!

                val opName = OPERATION_MAP[opId]
                if (opName == null) {
                    val errorMsg = "❌ Unknown operation: $opId"
                    addLog(errorMsg)
                    Logger.log(errorMsg)
                    _errorMessage.value = "UNKNOWN OPERATION ID"
                    return@launch
                }

                processWorkOrder(currentUserId, wo, opName, routerId)
            } else {
                val errorMsg = "❌ Invalid code: $code"
                addLog(errorMsg)
                Logger.log(errorMsg)
                _errorMessage.value = "INVALID BARCODE FORMAT"
            }
        }
    }

    private suspend fun processWorkOrder(userId: String, wo: String, operation: String, routerId: String) {
        _isLoading.value = true

        try {
            addLog("⏳ Sending command to server...")

            // Send clock in command
            val commandId = apiClient.sendClockInWorkOrder(
                CommandRequest.ClockInWO(
                    userId = userId,
                    woNumber = wo,
                    operation = operation,
                    routerId = routerId
                )
            )

            addLog("⏳ Command sent: $commandId")
            Logger.log("⏳ Command sent to server: $commandId")

            // Wait for result
            val result = apiClient.waitForCommandCompletion(commandId)

            if (result.success) {
                val successMsg = "✅ Clock In successful on WO $wo"
                addLog(successMsg)
                Logger.log(successMsg)
                _successMessage.value = "Operation successful"

                // Reset user state after successful Clock In
                _currentUser.value = null
                _isWaitingForAction.value = false

            } else {
                // Check if the error indicates Clock In already exists (need Clock Out)
                if (result.message.contains("ya tiene un clock in activo") ||
                    result.message.contains("Clock In already exists") ||
                    result.message.contains("already has an active clock in")) {

                    addLog("🔄 Clock In exists, requesting Clock Out quantity...")
                    Logger.log("🔄 Requesting Clock Out for WO: $wo")

                    // Show quantity dialog for Clock Out
                    _showQuantityDialog.value = ClockOutRequest(userId, wo, operation, routerId)

                } else {
                    val errorMsg = "❌ Clock In failed: ${result.message}"
                    addLog(errorMsg)
                    Logger.log(errorMsg)
                    _errorMessage.value = "CLOCK IN FAILED"

                    // Reset user state on error
                    _currentUser.value = null
                    _isWaitingForAction.value = false
                }
            }

        } catch (e: Exception) {
            val errorMsg = "❌ Communication error: ${e.message}"
            addLog(errorMsg)
            Logger.log(errorMsg)
            _errorMessage.value = "COMMUNICATION ERROR"

            // Reset user state on exception
            _currentUser.value = null
            _isWaitingForAction.value = false
        } finally {
            _isLoading.value = false
        }
    }

    // NEW: Handle Clock Out with quantity
    fun processClockOut(clockOutRequest: ClockOutRequest, quantity: Double) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                addLog("⏳ Sending Clock Out command (qty: $quantity)...")

                // Send clock out command
                val commandId = apiClient.sendClockOut(
                    CommandRequest.ClockOut(
                        userId = clockOutRequest.userId,
                        woNumber = clockOutRequest.woNumber,
                        qty = quantity
                    )
                )

                addLog("⏳ Clock Out command sent: $commandId")
                Logger.log("⏳ Clock Out command sent to server: $commandId")

                // Wait for result
                val result = apiClient.waitForCommandCompletion(commandId)

                if (result.success) {
                    val successMsg = "✅ Clock Out successful on WO ${clockOutRequest.woNumber} (qty: $quantity)"
                    addLog(successMsg)
                    Logger.log(successMsg)
                    _successMessage.value = "Clock Out successful"
                } else {
                    val errorMsg = "❌ Clock Out failed: ${result.message}"
                    addLog(errorMsg)
                    Logger.log(errorMsg)
                    _errorMessage.value = "CLOCK OUT FAILED"
                }

            } catch (e: Exception) {
                val errorMsg = "❌ Clock Out communication error: ${e.message}"
                addLog(errorMsg)
                Logger.log(errorMsg)
                _errorMessage.value = "COMMUNICATION ERROR"
            } finally {
                // Always reset state after Clock Out attempt
                _currentUser.value = null
                _isWaitingForAction.value = false
                _isLoading.value = false
            }
        }
    }

    fun checkServerConnection() {
        viewModelScope.launch {
            try {
                val isConnected = apiClient.checkHealth()
                _isServerConnected.value = isConnected

                if (isConnected) {
                    addLog("✅ Server connected successfully")
                } else {
                    addLog("🔴 No server connection")
                }
            } catch (e: Exception) {
                _isServerConnected.value = false
                addLog("🔴 Error connecting to server: ${e.message}")
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

    fun clearQuantityDialog() {
        _showQuantityDialog.value = null
    }

    private fun addLog(message: String) {
        val currentLogs = _logs.value?.toMutableList() ?: mutableListOf()
        currentLogs.add(LogEntry(
            message = message,
            timestamp = System.currentTimeMillis()
        ))

        // Keep only last 100 logs
        if (currentLogs.size > 100) {
            currentLogs.removeAt(0)
        }

        _logs.value = currentLogs
    }

    // Clear logs (called from UI)
    fun clearLogs() {
        _logs.value = emptyList()
    }
}