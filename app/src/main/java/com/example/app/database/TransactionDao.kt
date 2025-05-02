package com.example.app.database

import androidx.room.*
import com.example.app.models.Transaction

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransactionById(transactionId: Long): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg transactions: Transaction) // Method for inserting multiple transactions

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactions(): List<Transaction>

    @Query("SELECT budget FROM transactions WHERE budget IS NOT NULL LIMIT 1")
    suspend fun getBudget(): Double?

    @Query("UPDATE transactions SET budget = :budget WHERE id = 1")
    suspend fun updateBudget(budget: Double)

    // ✅ Correctly placed — outside of any nested interface
    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    // New method to delete all transactions (added for backup reset)
    @Query("DELETE FROM transactions WHERE id != 1") // To retain any important transactions (if necessary)
    suspend fun deleteBackupTransactions()

}
