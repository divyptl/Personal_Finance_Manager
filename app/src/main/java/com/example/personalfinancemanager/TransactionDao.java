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
