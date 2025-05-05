package com.example.app.database

import androidx.room.*
import com.example.app.models.Feedback

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedback WHERE id = 1 LIMIT 1")
    suspend fun getFeedback(): Feedback?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: Feedback)

    @Query("DELETE FROM feedback")
    suspend fun clearFeedback()
}
