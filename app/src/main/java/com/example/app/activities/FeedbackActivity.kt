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
import androidx.lifecycle.lifecycleScope
import com.example.app.R
import com.example.app.database.AppDatabase
import com.example.app.models.Feedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedbackActivity : AppCompatActivity() {

    private lateinit var ratingBar: RatingBar
    private lateinit var tvStoredRating: TextView
    private lateinit var btnSubmit: Button
    private lateinit var db: AppDatabase
    private var savedRating: Float = 0f // Default to 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        ratingBar = findViewById(R.id.ratingBar)
        tvStoredRating = findViewById(R.id.tvStoredRating)
        btnSubmit = findViewById(R.id.btnSubmitRating)
        db = AppDatabase.getInstance(this)

        // Load existing feedback from DB
        lifecycleScope.launch {
            val feedback = withContext(Dispatchers.IO) { db.feedbackDao().getFeedback() }
            savedRating = feedback?.rating ?: 0f
            ratingBar.rating = savedRating
            updateRatingDisplay(savedRating)
        }

        // Live update rating text as user moves stars
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
        val channelId = "feedback_notifications"
        val notificationId = 2001
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Feedback Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows submitted rating notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val message = when (rating.toInt()) {
            5 -> "🌟🌟🌟🌟🌟 Awesome! You gave us a perfect 5-star rating. Thanks for the love!"
            4 -> "🌟🌟🌟🌟 Great! You rated us 4 stars — we're glad you liked it!"
            3 -> "🌟🌟🌟 Thanks! You gave 3 stars. We're working hard to be even better."
            2 -> "🌟🌟 Noted! You gave 2 stars — we appreciate your honesty."
            1 -> "🌟 Oops! You rated us 1 star. We’d love to know how we can improve."
            else -> "Thanks for your feedback!"
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("Feedback Submitted")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
