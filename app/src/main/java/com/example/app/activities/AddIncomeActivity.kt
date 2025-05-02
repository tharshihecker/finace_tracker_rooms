package com.example.app.activities

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.app.R
import com.example.app.database.AppDatabase
import com.example.app.models.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddIncomeActivity : AppCompatActivity() {

    private lateinit var tvCurrentIncome: TextView
    private lateinit var etTitle: EditText
    private lateinit var etAmount: EditText
    private lateinit var datePicker: DatePicker
    private lateinit var btnSave: Button
    private lateinit var btnBack: Button

    // Track editing state and ID
    private var isEditing = false
    private var editingTransactionId: Long = -1L

    // Define currency as a class-level property
    private lateinit var currency: String

    // Initialize AppDatabase and TransactionDao using the correct getInstance method
    private val transactionDao by lazy { AppDatabase.getInstance(this).transactionDao() }
    private val settingsDao by lazy { AppDatabase.getInstance(this).settingsDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_income)

        // UI initialization
        tvCurrentIncome = findViewById(R.id.tvCurrentIncome)
        etTitle = findViewById(R.id.etTitle)
        etAmount = findViewById(R.id.etAmount)
        datePicker = findViewById(R.id.datePicker)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)

        // Check if we're editing an existing transaction
        isEditing = intent.getBooleanExtra("isEditing", false)
        if (isEditing) {
            editingTransactionId = intent.getLongExtra("transactionId", -1L)
            // Load existing transaction from Room
            lifecycleScope.launch {
                val existing = transactionDao.getTransactionById(editingTransactionId)
                existing?.let {
                    etTitle.setText(it.title)
                    etAmount.setText(it.amount.toString())
                    // Date stored as "day-month-year"
                    val parts = it.date.split("-")
                    if (parts.size == 3) {
                        val day = parts[0].toInt()
                        val month = parts[1].toInt() - 1  // DatePicker months are 0-based
                        val year = parts[2].toInt()
                        datePicker.updateDate(year, month, day)
                    }
                }
            }
        }

        // Show current income and actual total expenses using Room data
        updateIncomeAndExpenses()

        // Save income logic
        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val amountText = etAmount.text.toString().trim()

            if (title.isEmpty() || amountText.isEmpty()) {
                Toast.makeText(this, "Please provide a title and amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Format date as day-month-year
            val date = "${datePicker.dayOfMonth}-${datePicker.month + 1}-${datePicker.year}"

            lifecycleScope.launch {
                if (isEditing && editingTransactionId != -1L) {
                    // Update existing transaction in Room
                    val updatedTransaction = Transaction(
                        id = editingTransactionId,
                        title = title,
                        amount = amount,
                        category = "Income",
                        date = date
                    )
                    transactionDao.updateTransaction(updatedTransaction)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AddIncomeActivity, "Income updated", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Create new transaction in Room
                    val newTransaction = Transaction(
                        id = System.currentTimeMillis(),
                        title = title,
                        amount = amount,
                        category = "Income",
                        date = date
                    )
                    transactionDao.insertTransaction(newTransaction)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AddIncomeActivity, "Income saved", Toast.LENGTH_SHORT).show()
                    }
                }
                updateIncomeAndExpenses()  // Update UI after saving
                finish() // Go back after saving
            }
        }

        // Navigate back
        btnBack.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun updateIncomeAndExpenses() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@AddIncomeActivity)
            val txDao = db.transactionDao()
            val settings = settingsDao.getSettings()

            // 1) Sum actual income & expenses
            val allTxns = txDao.getAllTransactions()
            val currentIncome = allTxns.filter { it.category.equals("Income", true) }
                .sumOf { it.amount }
            val totalExpenses = allTxns.filter { !it.category.equals("Income", true) }
                .sumOf { it.amount }

            // 2) Get currency & (optional) budget
            currency = settings?.currencySymbol ?: "₹"
            val budget = settings?.budget ?: 0.0

            // 3) Update UI on main thread
            withContext(Dispatchers.Main) {
                tvCurrentIncome.setTextColor(
                    if (totalExpenses > currentIncome) getColor(R.color.warningColor)
                    else getColor(R.color.sucessColor)
                )

                tvCurrentIncome.text = if (totalExpenses > currentIncome) {
                    "Warning!\nSpent: $currency${"%.2f".format(totalExpenses)} is more than Income: $currency${"%.2f".format(currentIncome)}"
                } else {
                    "Current Income: $currency${"%.2f".format(currentIncome)}\nTotal Spent: $currency${"%.2f".format(totalExpenses)}"
                }
            }
        }
    }

}
