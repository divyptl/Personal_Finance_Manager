package com.example.personalfinancemanager;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface StockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Stock stock);
    @Query("SELECT * FROM stock_table")
    androidx.lifecycle.LiveData<java.util.List<Stock>> getAllStocks();
    @Query("DELETE FROM stock_table WHERE ticker = :tickerSymbol")
    void deleteStock(String tickerSymbol);

    /** Used by the "Delete all data" account-deletion flow. */
    @Query("DELETE FROM stock_table")
    void deleteAll();
}