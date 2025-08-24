package com.reybel.gunderlinscanner

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.reybel.gunderlinscanner.databinding.ActivityMainBinding
import com.reybel.gunderlinscanner.ui.LogAdapter
import com.reybel.gunderlinscanner.ui.MainViewModel
import com.reybel.gunderlinscanner.utils.AppPreferences
import com.reybel.gunderlinscanner.utils.BarcodeInputHandler
import com.reybel.gunderlinscanner.utils.Logger

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var logAdapter: LogAdapter
    private lateinit var preferences: AppPreferences
    private lateinit var barcodeHandler: BarcodeInputHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences(this)
        setupWindowInsets()
        setupViewModel()
        setupBarcodeHandler()
        setupUI()
        setupObservers()

        Logger.init(this)
        Logger.log("🟢 App iniciada - Scanner USB listo")
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
    }

    private fun setupBarcodeHandler() {
        barcodeHandler = BarcodeInputHandler { scannedCode ->
            viewModel.processScannedCode(scannedCode)
        }
    }

    private fun setupUI() {
        // Setup RecyclerView for logs
        logAdapter = LogAdapter()
        binding.recyclerViewLogs.apply {
            adapter = logAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                reverseLayout = true
                stackFromEnd = true
            }
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnClearLogs.setOnClickListener {
            logAdapter.clearLogs()
            Logger.log("🧹 Logs limpiados")
        }

        // Setup initial state
        updateScannerHint("Escanee su ID de usuario", "Formato: A12345")
    }

    // ¡CLAVE! - Interceptar eventos de teclado para el scanner USB
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Primero intentar que el handler procese el evento
        if (barcodeHandler.handleKeyEvent(keyCode, event)) {
            return true
        }

        // Si no fue procesado por el scanner, usar comportamiento normal
        return super.onKeyDown(keyCode, event)
    }

    private fun setupObservers() {
        // Observe current user
        viewModel.currentUser.observe(this) { user ->
            if (user != null) {
                binding.textCurrentUser.text = "Usuario: $user"
                binding.statusIndicator.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.status_waiting)
                )
                updateScannerHint("Escanee Work Order", "Formato: 1234O56R78")
            } else {
                binding.textCurrentUser.text = "Sin usuario activo"
                binding.statusIndicator.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.status_inactive)
                )
                updateScannerHint("Escanee su ID de usuario", "Formato: A12345")
            }
        }

        // Observe server status
        viewModel.isServerConnected.observe(this) { isConnected ->
            val serverUrl = preferences.getServerUrl()
            val serverHost = try {
                serverUrl.substringAfter("://").substringBefore(":")
            } catch (e: Exception) {
                "localhost"
            }

            binding.textServerStatus.text = if (isConnected) {
                "● Conectado ($serverHost)"
            } else {
                "● Desconectado ($serverHost)"
            }
            binding.textServerStatus.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (isConnected) R.color.status_connected else R.color.status_error
                )
            )
        }

        // Observe logs
        viewModel.logs.observe(this) { logs ->
            logAdapter.updateLogs(logs)
        }

        // Observe error messages
        viewModel.errorMessage.observe(this) { errorMessage ->
            if (errorMessage.isNotEmpty()) {
                showErrorDialog(errorMessage)
                viewModel.clearErrorMessage()
            }
        }

        // Observe success messages
        viewModel.successMessage.observe(this) { successMessage ->
            if (successMessage.isNotEmpty()) {
                Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
                viewModel.clearSuccessMessage()
            }
        }
    }

    private fun updateScannerHint(main: String, sub: String) {
        binding.textScannerHint.text = main
        // El subtítulo ya está en el XML estático
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ ERROR DEL SISTEMA")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSettingsDialog() {
        val currentUrl = preferences.getServerUrl()
        val input = android.widget.EditText(this).apply {
            setText(currentUrl)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Configuración del Servidor")
            .setMessage("URL del servidor:")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    preferences.setServerUrl(newUrl)
                    // Ya no tenemos textServerUrl en el nuevo layout
                    viewModel.updateServerUrl(newUrl)
                    Logger.log("⚙️ URL del servidor actualizada: $newUrl")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkServerConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        barcodeHandler.destroy()
    }
}