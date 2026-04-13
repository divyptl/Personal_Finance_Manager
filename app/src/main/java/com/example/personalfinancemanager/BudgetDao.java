package com.example.personalfinancemanager;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(Budget budget);

    @Query("SELECT * FROM budget_table ORDER BY category ASC")
    LiveData<List<Budget>> getAllBudgets();

    /** Synchronous version — called from WorkManager background thread. */
    @Query("SELECT * FROM budget_table")
    List<Budget> getAllBudgetsSync();

    @Query("SELECT * FROM budget_table WHERE category = :category LIMIT 1")
    Budget getBudgetForCategory(String category);

    @Query("DELETE FROM budget_table WHERE category = :category")
    void deleteBudget(String category);

    @Query("DELETE FROM budget_table")
    void deleteAll();
}
