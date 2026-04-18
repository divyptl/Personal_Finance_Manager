package com.example.personalfinancemanager;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NetWorthSnapshotDao {

    @Insert
    void insert(NetWorthSnapshot snapshot);

    /** Newest first — used by the "last snapshot?" gate on the tracker. */
    @Query("SELECT * FROM net_worth_snapshot ORDER BY timestamp DESC LIMIT 1")
    NetWorthSnapshot getLatestSync();

    /** Oldest-first for charting. */
    @Query("SELECT * FROM net_worth_snapshot WHERE timestamp >= :sinceMillis " +
           "ORDER BY timestamp ASC")
    List<NetWorthSnapshot> getSinceSync(long sinceMillis);

    /** Wipes history — used by the "Delete all data" flow. */
    @Query("DELETE FROM net_worth_snapshot")
    void deleteAll();
}
