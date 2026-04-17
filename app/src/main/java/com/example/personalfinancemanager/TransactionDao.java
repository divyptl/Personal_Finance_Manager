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

    @Query("SELECT category, SUM(amount) as total from transaction_table where type='expense' OR type='Debit' group by category")
    LiveData<List<CategorySum>> getExpenseByCategory();

    @Query("SELECT SUM(amount) from transaction_table where type = 'expense' OR type = 'Debit'")
    LiveData<Double> getTotalExpense();

    // Auto-Reset (Shows only the current month's data)
    @Query("SELECT * FROM transaction_table WHERE timestamp >= :startOfMonthMillis ORDER BY timestamp DESC")
    androidx.lifecycle.LiveData<java.util.List<Transaction>> getCurrentMonthTransactions(long startOfMonthMillis);

    // Manual Reset (Wipes everything - Danger!)
    @Query("DELETE FROM transaction_table")
    void deleteAllTransactions();

    // --- Single-row edit / delete (manual transaction editor) ---
    // Done as @Query instead of @Update so the Transaction entity can stay
    // immutable (no public setters for amount/category/etc.). This also avoids
    // needing to requery the row just to construct an @Update parameter.
    @Query("UPDATE transaction_table SET message = :message, amount = :amount, " +
           "timestamp = :timestamp, type = :type, category = :category WHERE id = :id")
    void updateTransactionFields(int id, String message, double amount,
                                 long timestamp, String type, String category);

    @Query("DELETE FROM transaction_table WHERE id = :id")
    void deleteTransactionById(int id);

    /**
     * Synchronous read of every transaction, newest first. Used by CSV export,
     * which writes on a background thread and needs a plain {@code List<>}
     * rather than LiveData.
     */
    @Query("SELECT * FROM transaction_table ORDER BY timestamp DESC")
    List<Transaction> getAllTransactionsSync();

    // Fetch transactions between a specific start and end date
    @Query("SELECT * FROM transaction_table WHERE timestamp >= :startMillis AND timestamp <= :endMillis ORDER BY timestamp DESC")
    androidx.lifecycle.LiveData<java.util.List<Transaction>> getTransactionsByMonth(long startMillis, long endMillis);

    // ---- Budget alert queries (synchronous — called from WorkManager / background) ----

    /** Sum of expenses in a given category since a specific timestamp (month start). */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transaction_table " +
           "WHERE (type = 'expense' OR type = 'Debit') " +
           "AND category = :category AND timestamp >= :sinceMillis")
    double getCategoryExpenseSince(String category, long sinceMillis);

    /** All expense categories with their totals since a timestamp. */
    @Query("SELECT category, SUM(amount) as total FROM transaction_table " +
           "WHERE (type = 'expense' OR type = 'Debit') AND timestamp >= :sinceMillis " +
           "GROUP BY category")
    List<CategorySum> getCategoryExpensesSinceSync(long sinceMillis);

    // ---- Analytics queries ----

    /** Monthly expense totals for the last N months (for line chart). */
    @Query("SELECT strftime('%Y-%m', timestamp/1000, 'unixepoch') AS month, " +
           "SUM(amount) AS total " +
           "FROM transaction_table " +
           "WHERE (type = 'expense' OR type = 'Debit') " +
           "AND timestamp >= :sinceMillis " +
           "GROUP BY month ORDER BY month ASC")
    List<MonthlyTotal> getMonthlyExpenseTotals(long sinceMillis);

    /** Monthly expenses broken down by category (for stacked bar chart). */
    @Query("SELECT strftime('%Y-%m', timestamp/1000, 'unixepoch') AS month, " +
           "category, SUM(amount) AS total " +
           "FROM transaction_table " +
           "WHERE (type = 'expense' OR type = 'Debit') " +
           "AND timestamp >= :sinceMillis " +
           "GROUP BY month, category ORDER BY month ASC")
    List<MonthlyCategoryTotal> getMonthlyCategoryExpenses(long sinceMillis);

    /** Monthly income totals (for line chart overlay). */
    @Query("SELECT strftime('%Y-%m', timestamp/1000, 'unixepoch') AS month, " +
           "SUM(amount) AS total " +
           "FROM transaction_table " +
           "WHERE (type = 'income' OR type = 'Credit') " +
           "AND timestamp >= :sinceMillis " +
           "GROUP BY month ORDER BY month ASC")
    List<MonthlyTotal> getMonthlyIncomeTotals(long sinceMillis);
}
