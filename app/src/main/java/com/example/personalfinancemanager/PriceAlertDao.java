package com.example.personalfinancemanager;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PriceAlertDao {

    @Insert
    long insert(PriceAlert alert);

    @Update
    void update(PriceAlert alert);

    @Delete
    void delete(PriceAlert alert);

    @Query("SELECT * FROM price_alert ORDER BY ticker ASC")
    LiveData<List<PriceAlert>> getAllLive();

    /** Synchronous read used by {@link PriceAlertWorker} on a background thread. */
    @Query("SELECT * FROM price_alert WHERE enabled = 1")
    List<PriceAlert> getAllEnabledSync();

    @Query("UPDATE price_alert SET lastNotifiedAt = :when WHERE id = :id")
    void stampNotified(int id, long when);

    /** Used by the "Delete all data" flow. */
    @Query("DELETE FROM price_alert")
    void deleteAll();
}
