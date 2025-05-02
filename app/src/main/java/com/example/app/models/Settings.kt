package com.example.app.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Long = 1,
    val income: Double,
    val expenses: Double,
    val budget: Double,
    val currencySymbol: String
)
