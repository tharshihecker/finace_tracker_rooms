package com.example.app.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.example.app.R
import com.example.app.database.AppDatabase
import com.example.app.models.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

class BudgetSetupActivity : AppCompatActivity() {

    private lateinit var etBudget: EditText
    private lateinit var btnSaveBudget: Button
    private lateinit var tvBudgetStatus: TextView
    private lateinit var btnBack: Button
    private lateinit var spinnerCurrency: Spinner

    private val currencySymbols = arrayOf("₨", "₹", "$")
    private val db by lazy { AppDatabase.getInstance(this) }
    private val settingsDao by lazy { db.settingsDao() }
    private val transactionDao by lazy { db.transactionDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget_setup)

        etBudget = findViewById(R.id.etBudget)
        btnSaveBudget = findViewById(R.id.btnSaveBudget)
        tvBudgetStatus = findViewById(R.id.tvBudgetStatus)
        btnBack = findViewById(R.id.btnBack)
        spinnerCurrency = findViewById(R.id.spinnerCurrency)

        // Spinner setup
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencySymbols).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerCurrency.adapter = adapter

        // When currency changes, refresh display
        spinnerCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                refreshStatus()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnBack.setOnClickListener { finish() }

        // Load existing settings
        lifecycleScope.launch {
            val s = settingsDao.getSettings()
            withContext(Dispatchers.Main) {
                if (s != null) {
                    etBudget.setText(s.budget.toString())
                    val pos = currencySymbols.indexOf(s.currencySymbol)
                    if (pos >= 0) spinnerCurrency.setSelection(pos)
                }
                refreshStatus()
            }
        }

        btnSaveBudget.setOnClickListener {
            val bText = etBudget.text.toString().trim()
            val bValue = bText.toDoubleOrNull()
            if (bValue == null || bValue <= 0) {
                Toast.makeText(this, "Please enter a valid budget", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val cur = currencySymbols[spinnerCurrency.selectedItemPosition]

            lifecycleScope.launch {
                val existing = settingsDao.getSettings()
                val all = transactionDao.getAllTransactions()
                val spent = all.filter { !it.category.equals("Income", true) }.sumOf { it.amount }

                val settings = Settings(
                    id = 1,
                    income = existing?.income ?: 0.0,
                    expenses = existing?.expenses ?: 0.0,
                    budget = bValue,
                    currencySymbol = cur
                )
                settingsDao.insert(settings)

                val fmt = DecimalFormat("#,###.00")
                val budgetStr = fmt.format(bValue)
                val spentStr = fmt.format(spent)

                when {
                    spent > bValue -> {
                        val exceedStr = fmt.format(spent - bValue)
                        val message = """
                            Your total expenses ($cur$spentStr) have exceeded your budget ($cur$budgetStr)!
                            You have exceeded by $cur$exceedStr.
                        """.trimIndent()
                        sendBudgetNotification("⚠️ Budget Exceeded", message)
                    }

                    spent >= 0.9 * bValue -> {
                        val remainingStr = fmt.format(bValue - spent)
                        val message = """
                            Your total expenses ($cur$spentStr) are nearing your budget limit ($cur$budgetStr).
                            Only $cur$remainingStr left before you exceed your budget.
                        """.trimIndent()
                        sendBudgetNotification("⚠️ Budget Warning", message)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BudgetSetupActivity, "Budget saved", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@BudgetSetupActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            val all = transactionDao.getAllTransactions()
            val income = all.filter { it.category.equals("Income", true) }.sumOf { it.amount }
            val spent = all.filter { !it.category.equals("Income", true) }.sumOf { it.amount }

            val s = settingsDao.getSettings()
            val budget = s?.budget ?: 0.0
            val currency = currencySymbols[spinnerCurrency.selectedItemPosition]

            val fmt = DecimalFormat("#,###.00")
            val exceedAmount = spent - budget
            val remaining = budget - spent

            val status = buildString {
                append("Income: $currency${fmt.format(income)}\n")
                append("Spent:  $currency${fmt.format(spent)}\n")
                append("Budget: $currency${fmt.format(budget)}\n\n")

                when {
                    spent > budget -> append("⚠️ Budget Exceeded by $currency${fmt.format(exceedAmount)}")
                    spent >= 0.9 * budget -> append("⚠️ Near budget limit. Only $currency${fmt.format(remaining)} left")
                    else -> append("✅ Within budget. $currency${fmt.format(remaining)} remaining")
                }
            }

            val colorRes = when {
                spent > budget || spent >= 0.9 * budget -> R.color.warningColor
                else -> R.color.sucessColor
            }

            withContext(Dispatchers.Main) {
                tvBudgetStatus.text = status
                tvBudgetStatus.setTextColor(resources.getColor(colorRes, null))
            }
        }
    }

    private fun sendBudgetNotification(title: String, message: String) {
        val channelId = "budget_notifications"
        val notificationId = 1001
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Budget Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies about budget limit and overspending"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message.lines().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
