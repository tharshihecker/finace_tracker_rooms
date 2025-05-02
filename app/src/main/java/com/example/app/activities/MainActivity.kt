package com.example.app.activities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.app.R
import com.example.app.database.AppDatabase
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvTotalIncome: TextView
    private lateinit var tvTotalExpenses: TextView
    private lateinit var tvBudget: TextView
    private lateinit var tvBudgetStatus: TextView
    private lateinit var tvNetBalance: TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Initialize Views ---
        tvTotalIncome = findViewById(R.id.tvTotalIncome)
        tvTotalExpenses = findViewById(R.id.tvTotalExpenses)
        tvBudget = findViewById(R.id.tvBudget)
        tvBudgetStatus = findViewById(R.id.tvBudgetStatus)
        tvNetBalance = findViewById(R.id.tvNetBalance)

        // --- Navigation Buttons ---
        findViewById<Button>(R.id.btnSetupIncome).setOnClickListener {
            startActivity(Intent(this, AddIncomeActivity::class.java))
        }
        findViewById<Button>(R.id.btnAddTransaction).setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }
        findViewById<Button>(R.id.btnCategoryAnalysis).setOnClickListener {
            startActivity(Intent(this, CategoryAnalysisActivity::class.java))
        }
        findViewById<Button>(R.id.btnSetupBudget).setOnClickListener {
            startActivity(Intent(this, BudgetSetupActivity::class.java))
        }
        findViewById<Button>(R.id.btnBackupRestore).setOnClickListener {
            startActivity(Intent(this, DataBackupActivity::class.java))
        }
        findViewById<Button>(R.id.btnHistroy).setOnClickListener {
            startActivity(Intent(this, ManageTransactionActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val transactionDao = db.transactionDao()
            val settingsDao    = db.settingsDao()

            // 1. Fetch and sum transactions
            val allTxns = transactionDao.getAllTransactions()
            val income  = allTxns.filter { it.category.equals("Income", true) }
                .sumOf { it.amount }
            val expense = allTxns.filter { !it.category.equals("Income", true) }
                .sumOf { it.amount }

            // 2. Fetch budget + currency from Settings
            val settings = settingsDao.getSettings()
            val budget   = settings?.budget ?: 0.0
            val currency = settings?.currencySymbol ?: "₨"

            // 3. Compute net and diff
            val net  = income - expense
            val diff = budget - expense

            // 4. Update UI on main thread
            withContext(Dispatchers.Main) {
                // Budget status
                val statusText: String
                val statusColor: Int
                if (diff >= 0) {
                    statusText  = "\uD83C\uDF89 On Track!\nSaved $currency${"%.2f".format(diff)} this month"
                    statusColor = getColor(R.color.sucessColor)
                } else {
                    statusText  = "\uD83D\uDEA8 Budget Exceeded!\nOverspent by $currency${"%.2f".format(-diff)}"
                    statusColor = getColor(R.color.warningColor)
                }
                tvBudgetStatus.text          = statusText
                tvBudgetStatus.setTextColor(statusColor)

                // Net Balance
                val netText = if (net >= 0) {
                    "\uD83D\uDCB0 Balance Left: $currency${"%.2f".format(net)}"
                } else {
                    "\uD83D\uDCB8 Overused: $currency${"%.2f".format(-net)}"
                }
                tvNetBalance.text         = netText
                tvNetBalance.setTextColor(
                    if (net >= 0) getColor(R.color.sucessColor)
                    else getColor(R.color.warningColor)
                )

                // Totals
                tvTotalIncome.text   = "\uD83D\uDCB0 Income: $currency${"%.2f".format(income)}"
                tvTotalExpenses.text = "\uD83D\uDCB8 Spent: $currency${"%.2f".format(expense)}"
                tvBudget.text        = "\uD83D\uDCC5 Budget: $currency${"%.2f".format(budget)}"
            }
        }
    }

}
