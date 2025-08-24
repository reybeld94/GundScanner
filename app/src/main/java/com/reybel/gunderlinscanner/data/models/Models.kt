package com.reybel.gunderlinscanner.data.models

// Modelos de request para API
sealed class CommandRequest {
    data class ClockInWO(
        val userId: String,
        val woNumber: String,
        val operation: String,
        val routerId: String
    ) : CommandRequest()

    data class ClockOut(
        val userId: String,
        val woNumber: String,
        val qty: Double = 1.0
    ) : CommandRequest()
}

// Respuesta de comando del servidor
data class CommandResponse(
    val id: String,
    val type: String,
    val status: String,
    val message: String,
    val timestamp: String
)

// Resultado final de un comando
data class CommandResult(
    val success: Boolean,
    val message: String
)

// Respuesta de health check
data class HealthResponse(
    val status: String,
    val timestamp: String,
    val message: String,
    val queueRunning: Boolean
)

// Entrada de log para UI
data class LogEntry(
    val message: String,
    val timestamp: Long,
    val level: LogLevel = LogLevel.INFO
)

enum class LogLevel {
    INFO, SUCCESS, ERROR, WARNING
}

// User state
data class UserState(
    val id: String?,
    val isWaitingForAction: Boolean = false
)

// Queue status response
data class QueueStatus(
    val running: Boolean,
    val pendingCommands: Int,
    val totalCommands: Int
)