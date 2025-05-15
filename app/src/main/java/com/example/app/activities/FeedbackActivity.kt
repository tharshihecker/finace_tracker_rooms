package com.example.app.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.example.app.R
import com.example.app.database.AppDatabase
import com.example.app.models.Feedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

class FeedbackActivity : AppCompatActivity() {

    private lateinit var ratingBar: RatingBar
    private lateinit var tvStoredRating: TextView
    private lateinit var btnSubmit: Button
    private lateinit var db: AppDatabase
    private var savedRating: Float = 0f

    private val FEEDBACK_CHANNEL_ID = "feedback_rating_channel"
    private val FEEDBACK_NOTIFICATION_ID = 2001
    private val BUDGET_NOTIFICATION_ID = 1001 // if you want to cancel previous one

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        ratingBar = findViewById(R.id.ratingBar)
        tvStoredRating = findViewById(R.id.tvStoredRating)
        btnSubmit = findViewById(R.id.btnSubmitRating)
        db = AppDatabase.getInstance(this)

        lifecycleScope.launch {
            val feedback = withContext(Dispatchers.IO) { db.feedbackDao().getFeedback() }
            savedRating = feedback?.rating ?: 0f
            ratingBar.rating = savedRating
            updateRatingDisplay(savedRating)
        }

        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            updateRatingDisplay(rating)
        }

        btnSubmit.setOnClickListener {
            val selectedRating = ratingBar.rating
            if (selectedRating > 0f) {
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            db.feedbackDao().insertFeedback(Feedback(id = 1, rating = selectedRating))
                        }

                        sendRatingNotification(selectedRating)

                        Toast.makeText(this@FeedbackActivity, "Feedback submitted successfully", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@FeedbackActivity, MainActivity::class.java))
                        finish()

                    } catch (e: Exception) {
                        Toast.makeText(this@FeedbackActivity, "Error saving feedback", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Please select a rating before submitting", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun updateRatingDisplay(rating: Float) {
        val text = "Your rating: ${rating.toInt()} star${if (rating > 1f) "s" else ""}"
        tvStoredRating.text = text

        val colorRes = if (rating <= 2f) R.color.warningColor else R.color.sucessColor
        tvStoredRating.setTextColor(resources.getColor(colorRes, null))
    }

    private fun sendRatingNotification(rating: Float) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // ✅ Cancel budget notification if it's lingering
        notificationManager.cancel(BUDGET_NOTIFICATION_ID)

        // ✅ Create channel only if not already created
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = notificationManager.getNotificationChannel(FEEDBACK_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    FEEDBACK_CHANNEL_ID,
                    "Feedback Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableLights(true)
                    enableVibration(true)
                    description = "Notifications when you submit a rating"
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        val message = when (rating.toInt()) {
            5 -> "🌟🌟🌟🌟🌟 Awesome! You gave us a perfect 5-star rating. Thanks for the love!"
            4 -> "🌟🌟🌟🌟 Great! You rated us 4 stars — we're glad you liked it!"
            3 -> "🌟🌟🌟 Thanks! You gave 3 stars. We're working hard to be even better."
            2 -> "🌟🌟 Noted! You gave 2 stars — we appreciate your honesty."
            1 -> "🌟 Oops! You rated us 1 star. We’d love to know how we can improve."
            else -> "Thanks for your feedback!"
        }

        val notification = NotificationCompat.Builder(this, FEEDBACK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("Feedback Submitted")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setGroup("feedback_group") // optional
            .build()

        notificationManager.notify(FEEDBACK_NOTIFICATION_ID, notification)
    }
}
