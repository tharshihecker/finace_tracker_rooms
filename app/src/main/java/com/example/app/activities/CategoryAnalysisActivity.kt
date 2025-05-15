package com.example.app.activities

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.app.R
import com.example.app.database.AppDatabase
import com.example.app.models.Transaction
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.animation.Easing
import kotlinx.coroutines.launch

class CategoryAnalysisActivity : AppCompatActivity() {

    private lateinit var summaryLayout: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var btnBack: Button
    private lateinit var scrollContainer: ScrollView
    private lateinit var pieChart: PieChart
    private var currencySymbol: String = "Rs." // Default fallback symbol

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_analysis)

        summaryLayout = findViewById(R.id.categoryAnalysisLayout)
        emptyText = findViewById(R.id.tvNoDataCategory)
        btnBack = findViewById(R.id.btnBack)
        scrollContainer = findViewById(R.id.scrollContainer)
        pieChart = findViewById(R.id.pieChart)

        btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        lifecycleScope.launch {
            try {
                val settings = AppDatabase.getInstance(applicationContext).settingsDao().getSettings()
                currencySymbol = settings?.currencySymbol ?: currencySymbol

                val transactions = AppDatabase.getInstance(applicationContext)
                    .transactionDao()
                    .getAllTransactions()

                displayAnalysis(transactions)
            } catch (e: Exception) {
                emptyText.text = "Error loading data!"
                emptyText.visibility = View.VISIBLE
                summaryLayout.addView(emptyText)
            }
        }
    }

    private fun displayAnalysis(transactions: List<Transaction>) {
        // Save pie chart before clearing layout
        val existingChart = pieChart
        summaryLayout.removeAllViews()
        summaryLayout.addView(existingChart)

        if (transactions.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            summaryLayout.addView(emptyText)
            return
        }

        val grouped = transactions.groupBy { it.category }
        val pieEntries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()

        // High contrast custom color palette
        val darkColors = listOf(
            Color.parseColor("#FF5733"), // Red-Orange
            Color.parseColor("#2980B9"), // Blue
            Color.parseColor("#27AE60"), // Green
            Color.parseColor("#8E44AD"), // Purple
            Color.parseColor("#F39C12"), // Orange
            Color.parseColor("#C0392B"), // Dark Red
            Color.parseColor("#1ABC9C"), // Teal
            Color.parseColor("#34495E"), // Blue-Grey
            Color.parseColor("#D35400"), // Strong Orange
            Color.parseColor("#2ECC71")  // Bright Green
        )

        var colorIndex = 0

        for ((category, txns) in grouped) {
            val isIncome = category.equals("Income", ignoreCase = true)

            val color = if (isIncome) {
                Color.parseColor("#2E86C1") // Custom blue for income summary
            } else {
                val c = darkColors[colorIndex % darkColors.size]
                colors.add(c)
                colorIndex++
                c
            }

            // Category header
            val categoryTitle = TextView(this).apply {
                text = if (isIncome) "💰 Income" else "📂 $category"
                textSize = 25f
                setTypeface(null, Typeface.BOLD)
                setTextColor(color)
                setPadding(0, 32, 0, 8)
            }
            summaryLayout.addView(categoryTitle)

            var total = 0.0
            for (txn in txns) {
                val detail = TextView(this).apply {
                    text = "• ${txn.title}: $currencySymbol${"%.2f".format(txn.amount)} on ${txn.date}"
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(resources.getColor(R.color.primaryText, null))
                    setPadding(32, 4, 0, 4)
                }
                summaryLayout.addView(detail)
                total += txn.amount
            }

            val totalView = TextView(this).apply {
                text = "➤ Total: $currencySymbol${"%.2f".format(total)}"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(color)
                setPadding(32, 8, 0, 24)
            }
            summaryLayout.addView(totalView)

            // Only include non-income categories in pie chart
            if (!isIncome) {
                pieEntries.add(PieEntry(total.toFloat(), category))
            }
        }

        if (pieEntries.sumOf { it.value.toDouble() } == 0.0) {
            pieChart.clear()
            pieChart.centerText = "No data available"
            return
        }

        val dataSet = PieDataSet(pieEntries, "")
        dataSet.colors = colors
        dataSet.valueTextSize = 14f
        dataSet.setDrawValues(true)
        dataSet.valueFormatter = object : PercentFormatter(pieChart) {
            override fun getFormattedValue(value: Float): String {
                return "%.1f%%".format(value)
            }
        }

        val pieData = PieData(dataSet)

        pieChart.data = pieData
        pieChart.description.isEnabled = false
        pieChart.centerText = "Spending"
        pieChart.setUsePercentValues(true)
        pieChart.setDrawEntryLabels(true)
        pieChart.setEntryLabelColor(Color.BLACK)
        pieChart.setEntryLabelTextSize(16f)
        pieChart.setCenterTextSize(20f)
        pieChart.setCenterTextTypeface(Typeface.DEFAULT_BOLD)
        pieChart.isHighlightPerTapEnabled = true
        pieChart.animateY(1200, Easing.EaseInOutQuad)
        pieChart.holeRadius = 60f
        pieChart.transparentCircleRadius = 65f
        pieChart.setCenterTextOffset(0f, -10f)

        val legend = pieChart.legend
        legend.isEnabled = true
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        legend.isWordWrapEnabled = true
        legend.xEntrySpace = 10f
        legend.yEntrySpace = 10f
        legend.form = Legend.LegendForm.CIRCLE
        legend.formSize = 12f
        legend.textSize = 14f

        pieChart.invalidate()
    }
}
