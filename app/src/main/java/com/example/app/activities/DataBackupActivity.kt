package com.example.app.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.app.R
import com.example.app.database.AppDatabase
import com.example.app.models.Transaction
import com.example.app.models.Settings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.io.IOException

class DataBackupActivity : AppCompatActivity() {

    private val fileName = "transactions_backup.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_backup)

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        findViewById<Button>(R.id.btnExport).setOnClickListener { exportData() }
        findViewById<Button>(R.id.btnImport).setOnClickListener { importData() }
    }

    private fun exportData() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val transactions = db.transactionDao().getAllTransactions()
                val settings = db.settingsDao().getSettings()

                val backupData = mapOf(
                    "transactions" to transactions,
                    "settings" to settings
                )

                val json = Gson().toJson(backupData)

                openFileOutput(fileName, MODE_PRIVATE).use {
                    it.write(json.toByteArray())
                }

                Toast.makeText(this@DataBackupActivity, "Exported successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this@DataBackupActivity, "Export failed!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importData() {
        lifecycleScope.launch {
            try {
                val json = openFileInput(fileName).bufferedReader().use { it.readText() }
                val mapType = object : TypeToken<Map<String, Any>>() {}.type
                val backupData: Map<String, Any> = Gson().fromJson(json, mapType)

                val db = AppDatabase.getInstance(applicationContext)

                // Deserialize transactions
                val transactionsJson = Gson().toJson(backupData["transactions"])
                val transactionListType = object : TypeToken<List<Transaction>>() {}.type
                val transactionList: List<Transaction> = Gson().fromJson(transactionsJson, transactionListType)

                db.transactionDao().insertAll(*transactionList.toTypedArray())

                // Deserialize settings
                val settingsJson = Gson().toJson(backupData["settings"])
                val settings: Settings = Gson().fromJson(settingsJson, Settings::class.java)

                db.settingsDao().insert(settings)

                Toast.makeText(this@DataBackupActivity, "Imported successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this@DataBackupActivity, "Import failed!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@DataBackupActivity, "Data format error!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
