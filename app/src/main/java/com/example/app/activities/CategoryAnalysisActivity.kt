package com.example.app.activities

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.app.R
import com.example.app.models.Transaction
import com.example.app.utils.SharedPrefsHelper

class CategoryAnalysisActivity : AppCompatActivity() {

    private lateinit var summaryLayout: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var btnBack: Button
    private lateinit var scrollContainer: ScrollView
    private lateinit var transactions: List<Transaction>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_analysis)

        summaryLayout   = findViewById(R.id.categoryAnalysisLayout)
        emptyText       = findViewById(R.id.tvNoDataCategory)
        btnBack         = findViewById(R.id.btnBack)
        scrollContainer = findViewById(R.id.scrollContainer)

        btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        transactions = SharedPrefsHelper.getTransactions(this)
        displayAnalysis()
    }

    private fun displayAnalysis() {
        summaryLayout.removeAllViews()

        if (transactions.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            summaryLayout.addView(emptyText)
            return
        }

        val grouped = transactions.groupBy { it.category }

        val colors = listOf(
            R.color.maroon, R.color.categoryBlue,
            R.color.sucessColor, R.color.categoryTeal,
            R.color.categoryPurple, R.color.categoryOrange
        )

        var colorIndex = 0

        for ((category, txns) in grouped) {
            // Cycle through predefined colors
            val categoryColor = resources.getColor(colors[colorIndex % colors.size], null)
            colorIndex++

            // Category Title
            val categoryTitle = TextView(this).apply {
                text = "📂 $category"
                textSize = 25f
                setTypeface(null, Typeface.BOLD)
                setTextColor(categoryColor)
                setPadding(0, 32, 0, 8)
            }
            summaryLayout.addView(categoryTitle)

            var total = 0.0
            for (txn in txns) {
                val detail = TextView(this).apply {
                    text = "• Rs.${"%.2f".format(txn.amount)} on ${txn.date}"
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD) // <-- This makes the text bold
                    setTextColor(resources.getColor(R.color.primaryText, null))
                    setPadding(32, 4, 0, 4)
                }

                summaryLayout.addView(detail)
                total += txn.amount
            }

            val totalView = TextView(this).apply {
                text = "➤ Total: Rs. ${"%.2f".format(total)}"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(categoryColor)
                setPadding(32, 8, 0, 24)
            }
            summaryLayout.addView(totalView)
        }
    }

}
