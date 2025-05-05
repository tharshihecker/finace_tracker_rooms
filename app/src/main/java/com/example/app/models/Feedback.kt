 package com.example.app.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feedback")
data class Feedback(
    @PrimaryKey val id: Long = 1,  // Single user, static id
    val rating: Float
)
