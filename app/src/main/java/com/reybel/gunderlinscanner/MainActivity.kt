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
import com.reybel.gunderlinscanner.ui.QuantityInputDialog
import com.reybel.gunderlinscanner.utils.AppPreferences
import com.reybel.gunderlinscanner.utils.BarcodeInputHandler
import com.reybel.gunderlinscanner.utils.Logger

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var logAdapter: LogAdapter
    private lateinit var preferences: AppPreferences
    private lateinit var barcodeHandler: BarcodeInputHandler

    private var quantityDialog: QuantityInputDialog? = null

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
        Logger.log("🟢 App started - USB Scanner ready")
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
            Logger.log("🧹 Logs cleared")
        }

        // Setup initial state
        updateScannerHint("Scan your user ID", "Format: A12345")
    }

    // KEY! - Intercept keyboard events for USB scanner
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // First try to let handler process the event
        if (barcodeHandler.handleKeyEvent(keyCode, event)) {
            return true
        }

        // If not processed by scanner, use normal behavior
        return super.onKeyDown(keyCode, event)
    }

    private fun setupObservers() {
        // Observe current user
        viewModel.currentUser.observe(this) { user ->
            if (user != null) {
                binding.textCurrentUser.text = "User: $user"
                binding.statusIndicator.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.status_waiting)
                )
                updateScannerHint("Scan Work Order", "Format: 1234O56R78")
            } else {
                binding.textCurrentUser.text = getString(R.string.no_active_user)
                binding.statusIndicator.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.status_inactive)
                )
                updateScannerHint("Scan your user ID", "Format: A12345")
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
                "● Connected ($serverHost)"
            } else {
                "● Disconnected ($serverHost)"
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

        // NEW: Observe quantity dialog requests
        viewModel.showQuantityDialog.observe(this) { clockOutRequest ->
            if (clockOutRequest != null) {
                showQuantityDialog(clockOutRequest)
                viewModel.clearQuantityDialog()
            }
        }
    }

    private fun updateScannerHint(main: String, sub: String) {
        binding.textScannerHint.text = main
        // The subtitle is already in static XML
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.system_error))
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun showSettingsDialog() {
        val currentUrl = preferences.getServerUrl()
        val input = android.widget.EditText(this).apply {
            setText(currentUrl)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.server_settings))
            .setMessage(getString(R.string.server_url))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    preferences.setServerUrl(newUrl)
                    viewModel.updateServerUrl(newUrl)
                    Logger.log("⚙️ Server URL updated: $newUrl")
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showQuantityDialog(clockOutRequest: MainViewModel.ClockOutRequest) {
        // Dismiss any existing dialog
        quantityDialog?.dismiss()

        val workOrderInfo = "${clockOutRequest.woNumber}O${getOperationId(clockOutRequest.operation)}R${clockOutRequest.routerId}"

        quantityDialog = QuantityInputDialog.show(
            context = this,
            workOrderInfo = workOrderInfo,
            onQuantityConfirmed = { quantity ->
                Logger.log("📦 Quantity entered: $quantity for WO ${clockOutRequest.woNumber}")
                viewModel.processClockOut(clockOutRequest, quantity)
            },
            onCancelled = {
                Logger.log("❌ Clock Out cancelled by user")
                // Reset user state when cancelled
                viewModel.apply {
                    clearErrorMessage()
                    clearSuccessMessage()
                }
            }
        )
    }

    private fun getOperationId(operationName: String): String {
        // Reverse lookup of operation ID from name
        return when (operationName) {
            "OP Laser Cutting" -> "51"
            "OP Forming" -> "52"
            "OP Welding" -> "53"
            "OP QC" -> "54"
            "OP Painting" -> "55"
            else -> "51" // Default
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkServerConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        barcodeHandler.destroy()
        quantityDialog?.dismiss()
    }
}