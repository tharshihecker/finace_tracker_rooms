package com.example.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import com.example.app.models.Settings

@Dao
interface SettingsDao {

    // Fetch settings
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): Settings?

    // Insert a new Settings record
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: Settings)

    // Update the existing Settings record
    @Update
    suspend fun updateSettings(settings: Settings)

    // Delete the Settings record
    @Delete
    suspend fun deleteSettings(settings: Settings)

    // Optionally, you can add a method to clear all settings
    @Query("DELETE FROM settings")
    suspend fun deleteAllSettings()
}
