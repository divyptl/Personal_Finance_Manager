package com.example.personalfinancemanager;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface TransactionDao {
    @Insert
    void insert(Transaction transaction);

    @Query("SELECT * FROM transaction_table ORDER BY timestamp DESC")
    LiveData<List<Transaction>> getAllTransactions();

    @Query("SELECT category, SUM(amount) as total from transaction_table where type='Debit' group by category")
    LiveData<List<CategorySum>> getExpenseByCategory();

    @Query("SELECT SUM(amount) from transaction_table where type = 'Debit'")
    LiveData<Double> getTotalExpense();

    // Auto-Reset (Shows only the current month's data)
    @Query("SELECT * FROM transaction_table WHERE timestamp >= :startOfMonthMillis ORDER BY timestamp DESC")
    androidx.lifecycle.LiveData<java.util.List<Transaction>> getCurrentMonthTransactions(long startOfMonthMillis);

    // Manual Reset (Wipes everything - Danger!)
    @Query("DELETE FROM transaction_table")
    void deleteAllTransactions();

    // Fetch transactions between a specific start and end date
    @Query("SELECT * FROM transaction_table WHERE timestamp >= :startMillis AND timestamp <= :endMillis ORDER BY timestamp DESC")
    androidx.lifecycle.LiveData<java.util.List<Transaction>> getTransactionsByMonth(long startMillis, long endMillis);
}
