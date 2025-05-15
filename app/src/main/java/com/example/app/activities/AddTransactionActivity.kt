package com.example.app.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.app.R
import com.example.app.database.AppDatabase
import com.example.app.models.Transaction
import com.example.app.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

class AddTransactionActivity : AppCompatActivity() {

    private lateinit var tvCurrentBudget: TextView
    private lateinit var etTitle: EditText
    private lateinit var etAmount: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var datePicker: DatePicker
    private lateinit var btnSave: Button
    private lateinit var btnDelete: Button
    private lateinit var btnBack: Button

    private val categories = arrayOf("Food", "Transport", "Bills", "Entertainment", "Other")
    private var currentTransaction: Transaction? = null
    private var isEditing: Boolean = false
    private var transactionId: Long = -1L

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        tvCurrentBudget = findViewById(R.id.tvCurrentBudget)
        etTitle = findViewById(R.id.etTitle)
        etAmount = findViewById(R.id.etAmount)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        datePicker = findViewById(R.id.datePicker)
        btnSave = findViewById(R.id.btnSave)
        btnDelete = findViewById(R.id.btnDelete)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        spinnerCategory.adapter = adapter

        isEditing = intent.getBooleanExtra("isEditing", false)
        transactionId = intent.getLongExtra("transactionId", -1L)

        if (isEditing && transactionId != -1L) {
            lifecycleScope.launch {
                currentTransaction = AppDatabase.getInstance(this@AddTransactionActivity)
                    .transactionDao()
                    .getTransactionById(transactionId)

                currentTransaction?.let { txn ->
                    etTitle.setText(txn.title)
                    etAmount.setText(txn.amount.toString())
                    spinnerCategory.setSelection(categories.indexOf(txn.category))

                    val parts = txn.date.split("-")
                    if (parts.size == 3) {
                        datePicker.updateDate(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
                    }

                    btnDelete.visibility = Button.VISIBLE
                }
            }
        } else {
            btnDelete.visibility = Button.GONE
        }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val amountText = etAmount.text.toString().trim()

            if (title.isEmpty() || amountText.isEmpty()) {
                Toast.makeText(this, getString(R.string.invalid_input), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val category = spinnerCategory.selectedItem.toString()
            val date = "${datePicker.dayOfMonth}-${datePicker.month + 1}-${datePicker.year}"

            lifecycleScope.launch {
                val dao = AppDatabase.getInstance(this@AddTransactionActivity)

                if (isEditing && currentTransaction != null) {
                    val updatedTransaction = currentTransaction!!.copy(
                        title = title, amount = amount, category = category, date = date
                    )
                    dao.transactionDao().updateTransaction(updatedTransaction)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AddTransactionActivity,
                            getString(R.string.transaction_updated),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } else {
                    val newTransaction = Transaction(
                        id = System.currentTimeMillis(),
                        title = title,
                        amount = amount,
                        category = category,
                        date = date
                    )
                    dao.transactionDao().insertTransaction(newTransaction)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AddTransactionActivity,
                            getString(R.string.transaction_saved),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // ✅ Check budget after DB update
                checkIfBudgetExceeded()

                // ✅ Safely finish on Main thread
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }

        btnDelete.setOnClickListener {
            currentTransaction?.let {
                lifecycleScope.launch {
                    AppDatabase.getInstance(this@AddTransactionActivity).transactionDao()
                        .deleteTransaction(it)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AddTransactionActivity,
                            getString(R.string.transaction_deleted),
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }

                    checkIfBudgetExceeded()
                }
            }
        }

        checkNotificationPermission()
        updateBudgetStatusUIOnly()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private suspend fun checkIfBudgetExceeded() {
        val dao = AppDatabase.getInstance(this@AddTransactionActivity)
        val transactions = dao.transactionDao().getAllTransactions()
        val totalExpenses = transactions
            .filter { !it.category.equals("Income", ignoreCase = true) }
            .sumOf { it.amount }

        val settings = dao.settingsDao().getSettings()
        val budget = settings?.budget ?: 0.0
        val currency = settings?.currencySymbol ?: "$"

        withContext(Dispatchers.Main) {
            updateBudgetAndExpenses(totalExpenses, budget, currency)
        }

        if (budget == 0.0) return

        if (totalExpenses > budget) {
            val exceededAmount = totalExpenses - budget
            val message = "Your total expenses ($currency${format(totalExpenses)}) have exceeded your budget ($currency${format(budget)})! " +
                    "You have exceeded by $currency${format(exceededAmount)}."
            checkAndSendNotification(message)
        } else if (totalExpenses >= 0.9 * budget) {
            val remaining = budget - totalExpenses
            val message = "Warning: You are about to reach your budget limit!\n" +
                    "Only $currency${format(remaining)} remaining from your budget of $currency${format(budget)}."
            checkAndSendNotification(message)
        }
    }

    private fun updateBudgetStatusUIOnly() {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@AddTransactionActivity)
            val transactions = dao.transactionDao().getAllTransactions()
            val totalExpenses = transactions
                .filter { !it.category.equals("Income", ignoreCase = true) }
                .sumOf { it.amount }

            val settings = dao.settingsDao().getSettings()
            val budget = settings?.budget ?: 0.0
            val currency = settings?.currencySymbol ?: "$"

            withContext(Dispatchers.Main) {
                updateBudgetAndExpenses(totalExpenses, budget, currency)
            }
        }
    }

    private fun updateBudgetAndExpenses(totalExpenses: Double, budget: Double, currency: String) {
        val formatter = DecimalFormat("#,###.00")
        val statusColor = if (totalExpenses > budget) {
            getColor(R.color.warningColor)
        } else {
            getColor(R.color.sucessColor)
        }

        val statusText = "Current Budget: $currency${formatter.format(budget)}\n" +
                "Total Spent: $currency${formatter.format(totalExpenses)}"

        tvCurrentBudget.text = statusText
        tvCurrentBudget.setTextColor(statusColor)
    }

    private fun checkAndSendNotification(message: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationHelper.sendBudgetAlert(this, message)
        } else {
            Log.w("AddTransactionActivity", "Notification permission not granted")
        }
    }

    private fun format(value: Double): String {
        return DecimalFormat("#,###.00").format(value)
    }
}
