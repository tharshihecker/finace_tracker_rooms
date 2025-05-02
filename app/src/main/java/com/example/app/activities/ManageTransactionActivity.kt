package com.example.app.activities

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.app.R
import com.example.app.database.AppDatabase
import com.example.app.models.Transaction
import com.example.app.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManageTransactionActivity : AppCompatActivity() {

    private lateinit var summaryLayout: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var btnBack: Button
    private lateinit var clearAllButton: Button
    private var transactions: MutableList<Transaction> = mutableListOf()
    private var currencySymbol: String = "Rs."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_transaction)

        summaryLayout = findViewById(R.id.categorySummaryLayout)
        emptyText = findViewById(R.id.tvNoData)
        btnBack = findViewById(R.id.btnBack)
        clearAllButton = findViewById(R.id.clearAllButton)

        btnBack.setOnClickListener { finish() }

        clearAllButton.setOnClickListener {
            lifecycleScope.launch {
                AppDatabase.getInstance(this@ManageTransactionActivity)
                    .transactionDao()
                    .deleteAllTransactions()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ManageTransactionActivity, "All transactions deleted", Toast.LENGTH_SHORT).show()
                    NotificationHelper.sendBudgetAlert(this@ManageTransactionActivity, "All transactions have been reset.")
                    transactions.clear()
                    displayTransactions()
                }
            }
        }

        // ✅ Load currency symbol from Settings table
        loadCurrencyFromSettings()

        loadTransactionsFromRoom()
    }

    private fun loadTransactionsFromRoom() {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@ManageTransactionActivity).transactionDao()
            transactions = dao.getAllTransactions().toMutableList()

            withContext(Dispatchers.Main) {
                displayTransactions()
            }
        }
    }
    private fun loadCurrencyFromSettings() {
        lifecycleScope.launch {
            val settings = AppDatabase.getInstance(this@ManageTransactionActivity)
                .settingsDao()
                .getSettings()

            withContext(Dispatchers.Main) {
                currencySymbol = settings?.currencySymbol ?: "Rs."
            }
        }
    }

    private fun displayTransactions() {
        summaryLayout.removeAllViews()

        if (transactions.isEmpty()) {
            clearAllButton.isEnabled = false

            emptyText.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (32 * resources.displayMetrics.density).toInt()
                }
                textSize = 40f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(resources.getColor(R.color.primaryText, null))
                gravity = Gravity.CENTER
                visibility = View.VISIBLE
            }
            summaryLayout.addView(emptyText)
            return
        } else {
            clearAllButton.isEnabled = true
            emptyText.visibility = View.GONE
        }

        val grouped = transactions.groupBy { it.category }
        for ((category, txnList) in grouped) {
            val categoryTitle = TextView(this).apply {
                text = "$category:"
                textSize = 25f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(resources.getColor(R.color.primaryText, null))
            }
            summaryLayout.addView(categoryTitle)

            txnList.forEach { txn ->
                val itemLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 16, 16, 24)
                }

                val txnText = TextView(this).apply {
                    text = "• ${txn.title} | $currencySymbol ${"%.2f".format(txn.amount)} | ${txn.date}"
                    textSize = 25f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(resources.getColor(R.color.primaryText, null))
                }

                val buttonLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 0)
                }

                val editBtn = Button(this).apply {
                    text = "Edit"
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(24, 16, 24, 16)
                    setTextColor(getColor(R.color.accent))

                    setOnClickListener {
                        val isIncome = txn.category.equals("income", ignoreCase = true)
                        val intent = if (isIncome) {
                            Intent(this@ManageTransactionActivity, AddIncomeActivity::class.java)
                        } else {
                            Intent(this@ManageTransactionActivity, AddTransactionActivity::class.java)
                        }
                        intent.putExtra("isEditing", true)
                        intent.putExtra("transactionId", txn.id)
                        startActivity(intent)
                    }
                }

                val deleteBtn = Button(this).apply {
                    text = "Delete"
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(24, 16, 24, 16)
                    setTextColor(getColor(R.color.accent))

                    setOnClickListener {
                        lifecycleScope.launch {
                            AppDatabase.getInstance(this@ManageTransactionActivity)
                                .transactionDao()
                                .deleteTransaction(txn)

                            transactions.remove(txn)

                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ManageTransactionActivity, "Transaction deleted", Toast.LENGTH_SHORT).show()
                                displayTransactions()
                            }
                        }
                    }
                }

                buttonLayout.addView(editBtn)
                buttonLayout.addView(deleteBtn)

                itemLayout.addView(txnText)
                itemLayout.addView(buttonLayout)
                summaryLayout.addView(itemLayout)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadTransactionsFromRoom()
    }
}
