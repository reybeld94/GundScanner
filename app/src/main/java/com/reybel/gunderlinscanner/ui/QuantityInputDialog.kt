package com.reybel.gunderlinscanner.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.reybel.gunderlinscanner.R

class QuantityInputDialog(
    context: Context,
    private val workOrderInfo: String,
    private val onQuantityConfirmed: (Double) -> Unit,
    private val onCancelled: () -> Unit = {}
) : Dialog(context, android.R.style.Theme_Material_Dialog) {

    private lateinit var editQuantity: EditText
    private lateinit var textWorkOrderInfo: TextView

    private var currentInput = StringBuilder()
    private var hasDecimal = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_quantity_input, null)
        setContentView(view)

        // Make dialog not cancelable by touching outside
        setCancelable(false)
        setCanceledOnTouchOutside(false)

        setupViews(view)
        setupNumpadListeners(view)
        setupActionButtons(view)

        // Set work order info
        textWorkOrderInfo.text = "Work Order: $workOrderInfo"
    }

    private fun setupViews(view: android.view.View) {
        editQuantity = view.findViewById(R.id.editQuantity)
        textWorkOrderInfo = view.findViewById(R.id.textWorkOrderInfo)

        // Initialize with "1" as default
        currentInput.append("1")
        updateDisplay()
    }

    private fun setupNumpadListeners(view: android.view.View) {
        // Number buttons - Individual setup to avoid forEach issues
        view.findViewById<MaterialButton>(R.id.btn0).setOnClickListener { addDigit("0") }
        view.findViewById<MaterialButton>(R.id.btn1).setOnClickListener { addDigit("1") }
        view.findViewById<MaterialButton>(R.id.btn2).setOnClickListener { addDigit("2") }
        view.findViewById<MaterialButton>(R.id.btn3).setOnClickListener { addDigit("3") }
        view.findViewById<MaterialButton>(R.id.btn4).setOnClickListener { addDigit("4") }
        view.findViewById<MaterialButton>(R.id.btn5).setOnClickListener { addDigit("5") }
        view.findViewById<MaterialButton>(R.id.btn6).setOnClickListener { addDigit("6") }
        view.findViewById<MaterialButton>(R.id.btn7).setOnClickListener { addDigit("7") }
        view.findViewById<MaterialButton>(R.id.btn8).setOnClickListener { addDigit("8") }
        view.findViewById<MaterialButton>(R.id.btn9).setOnClickListener { addDigit("9") }

        // Decimal point
        view.findViewById<MaterialButton>(R.id.btnDecimal).setOnClickListener {
            addDecimalPoint()
        }

        // Clear button
        view.findViewById<MaterialButton>(R.id.btnClear).setOnClickListener {
            clearInput()
        }
    }

    private fun setupActionButtons(view: android.view.View) {
        // Cancel button
        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            onCancelled()
            dismiss()
        }

        // Confirm button
        view.findViewById<MaterialButton>(R.id.btnConfirm).setOnClickListener {
            val quantity = getCurrentQuantity()
            if (quantity > 0) {
                onQuantityConfirmed(quantity)
                dismiss()
            } else {
                // Show error - quantity must be greater than 0
                editQuantity.error = "Quantity must be greater than 0"
            }
        }
    }

    private fun addDigit(digit: String) {
        // Limit to reasonable number of digits (e.g., 8 total)
        if (currentInput.length < 8) {
            // If current input is "0" or "1" (default), replace it with the new digit
            if (currentInput.toString() == "0" || currentInput.toString() == "1") {
                currentInput.clear()
                hasDecimal = false
            }

            currentInput.append(digit)
            updateDisplay()
        }
    }

    private fun addDecimalPoint() {
        if (!hasDecimal && currentInput.isNotEmpty()) {
            currentInput.append(".")
            hasDecimal = true
            updateDisplay()
        }
    }

    private fun clearInput() {
        currentInput.clear()
        hasDecimal = false
        currentInput.append("1") // Reset to default "1"
        updateDisplay()
        editQuantity.error = null
    }

    private fun updateDisplay() {
        val displayText = if (currentInput.isEmpty()) "1" else currentInput.toString()
        editQuantity.setText(displayText)
    }

    private fun getCurrentQuantity(): Double {
        return try {
            val text = currentInput.toString()
            if (text.isEmpty() || text == ".") {
                1.0 // Default to 1.0 if empty or just decimal point
            } else {
                text.toDouble()
            }
        } catch (e: NumberFormatException) {
            1.0 // Default to 1.0 on parsing error
        }
    }

    companion object {
        fun show(
            context: Context,
            workOrderInfo: String,
            onQuantityConfirmed: (Double) -> Unit,
            onCancelled: () -> Unit = {}
        ): QuantityInputDialog {
            val dialog = QuantityInputDialog(context, workOrderInfo, onQuantityConfirmed, onCancelled)
            dialog.show()
            return dialog
        }
    }
}