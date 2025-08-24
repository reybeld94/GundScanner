package com.reybel.gunderlinscanner.data

import com.reybel.gunderlinscanner.data.models.CommandRequest
import com.reybel.gunderlinscanner.data.models.CommandResponse
import com.reybel.gunderlinscanner.data.models.CommandResult
import com.reybel.gunderlinscanner.data.models.HealthResponse
import com.reybel.gunderlinscanner.data.models.QueueStatus
import kotlinx.coroutines.delay
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient {
    companion object {
        private const val DEFAULT_BASE_URL = "http://192.168.1.100:5000/api"
        private const val TIMEOUT_SECONDS = 10L
        private const val COMMAND_TIMEOUT_SECONDS = 30L
        private const val POLL_INTERVAL_MS = 1000L
    }

    private var baseUrl = DEFAULT_BASE_URL
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun updateBaseUrl(newUrl: String) {
        baseUrl = if (newUrl.endsWith("/api")) {
            newUrl
        } else if (newUrl.endsWith("/")) {
            "${newUrl}api"
        } else {
            "$newUrl/api"
        }
    }

    @Throws(Exception::class)
    suspend fun checkHealth(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    val responseBody = it.body?.string() ?: ""
                    val json = JSONObject(responseBody)
                    json.getString("status") == "ok"
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    @Throws(Exception::class)
    suspend fun sendClockInWorkOrder(request: CommandRequest.ClockInWO): String {
        val json = JSONObject().apply {
            put("user_id", request.userId)
            put("wo_number", request.woNumber)
            put("operation", request.operation)
            put("router_id", request.routerId)
        }

        val requestBody = json.toString().toRequestBody(mediaType)
        val httpRequest = Request.Builder()
            .url("$baseUrl/clockin-wo")
            .post(requestBody)
            .build()

        val response = client.newCall(httpRequest).execute()
        response.use {
            if (it.isSuccessful) {
                val responseBody = it.body?.string() ?: throw Exception("Empty response")
                val responseJson = JSONObject(responseBody)

                if (responseJson.getBoolean("success")) {
                    return responseJson.getString("command_id")
                } else {
                    throw Exception(responseJson.optString("error", "Unknown error"))
                }
            } else {
                throw Exception("HTTP ${it.code}: ${it.message}")
            }
        }
    }

    @Throws(Exception::class)
    suspend fun sendClockOut(request: CommandRequest.ClockOut): String {
        val json = JSONObject().apply {
            put("user_id", request.userId)
            put("wo_number", request.woNumber)
            put("qty", request.qty)
        }

        val requestBody = json.toString().toRequestBody(mediaType)
        val httpRequest = Request.Builder()
            .url("$baseUrl/clockout")
            .post(requestBody)
            .build()

        val response = client.newCall(httpRequest).execute()
        response.use {
            if (it.isSuccessful) {
                val responseBody = it.body?.string() ?: throw Exception("Empty response")
                val responseJson = JSONObject(responseBody)

                if (responseJson.getBoolean("success")) {
                    return responseJson.getString("command_id")
                } else {
                    throw Exception(responseJson.optString("error", "Unknown error"))
                }
            } else {
                throw Exception("HTTP ${it.code}: ${it.message}")
            }
        }
    }

    @Throws(Exception::class)
    suspend fun getCommandStatus(commandId: String): CommandResponse {
        val request = Request.Builder()
            .url("$baseUrl/status/$commandId")
            .get()
            .build()

        val response = client.newCall(request).execute()
        response.use {
            if (it.isSuccessful) {
                val responseBody = it.body?.string() ?: throw Exception("Empty response")
                val responseJson = JSONObject(responseBody)

                if (responseJson.getBoolean("success")) {
                    val commandJson = responseJson.getJSONObject("command")
                    return CommandResponse(
                        id = commandJson.getString("id"),
                        type = commandJson.getString("type"),
                        status = commandJson.getString("status"),
                        message = commandJson.getString("message"),
                        timestamp = commandJson.getString("timestamp")
                    )
                } else {
                    throw Exception(responseJson.optString("error", "Unknown error"))
                }
            } else {
                throw Exception("HTTP ${it.code}: ${it.message}")
            }
        }
    }

    @Throws(Exception::class)
    suspend fun waitForCommandCompletion(commandId: String): CommandResult {
        val startTime = System.currentTimeMillis()
        val timeoutMs = COMMAND_TIMEOUT_SECONDS * 1000

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                val command = getCommandStatus(commandId)

                when (command.status) {
                    "completed" -> {
                        return CommandResult(true, command.message)
                    }
                    "failed" -> {
                        return CommandResult(false, command.message)
                    }
                    "pending", "processing" -> {
                        delay(POLL_INTERVAL_MS)
                        continue
                    }
                    else -> {
                        return CommandResult(false, "Estado desconocido: ${command.status}")
                    }
                }
            } catch (e: Exception) {
                // Si hay error en el polling, esperar y reintentar
                delay(POLL_INTERVAL_MS)
            }
        }

        return CommandResult(false, "Timeout esperando respuesta del servidor")
    }

    @Throws(Exception::class)
    suspend fun getAllCommands(): List<CommandResponse> {
        val request = Request.Builder()
            .url("$baseUrl/commands")
            .get()
            .build()

        val response = client.newCall(request).execute()
        response.use {
            if (it.isSuccessful) {
                val responseBody = it.body?.string() ?: throw Exception("Empty response")
                val responseJson = JSONObject(responseBody)

                if (responseJson.getBoolean("success")) {
                    val commands = mutableListOf<CommandResponse>()
                    val commandsArray = responseJson.getJSONArray("commands")

                    for (i in 0 until commandsArray.length()) {
                        val commandJson = commandsArray.getJSONObject(i)
                        commands.add(
                            CommandResponse(
                                id = commandJson.getString("id"),
                                type = commandJson.getString("type"),
                                status = commandJson.getString("status"),
                                message = commandJson.getString("message"),
                                timestamp = commandJson.getString("timestamp")
                            )
                        )
                    }

                    return commands
                } else {
                    throw Exception(responseJson.optString("error", "Unknown error"))
                }
            } else {
                throw Exception("HTTP ${it.code}: ${it.message}")
            }
        }
    }

    @Throws(Exception::class)
    suspend fun getQueueStatus(): QueueStatus {
        val request = Request.Builder()
            .url("$baseUrl/queue/status")
            .get()
            .build()

        val response = client.newCall(request).execute()
        response.use {
            if (it.isSuccessful) {
                val responseBody = it.body?.string() ?: throw Exception("Empty response")
                val responseJson = JSONObject(responseBody)

                if (responseJson.getBoolean("success")) {
                    return QueueStatus(
                        running = responseJson.getBoolean("running"),
                        pendingCommands = responseJson.getInt("pending_commands"),
                        totalCommands = responseJson.getInt("total_commands")
                    )
                } else {
                    throw Exception(responseJson.optString("error", "Unknown error"))
                }
            } else {
                throw Exception("HTTP ${it.code}: ${it.message}")
            }
        }
    }

    // Función de conveniencia para verificar si el servidor está funcionando
    @Throws(Exception::class)
    suspend fun ping(): Boolean {
        return try {
            checkHealth()
        } catch (e: Exception) {
            false
        }
    }
}
