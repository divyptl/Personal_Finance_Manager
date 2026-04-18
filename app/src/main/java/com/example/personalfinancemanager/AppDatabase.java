package com.example.personalfinancemanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {Transaction.class, Stock.class, Budget.class,
                    NetWorthSnapshot.class, PriceAlert.class},
        version = 8,
        exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract TransactionDao transactionDao();
    public abstract StockDao stockDao();
    public abstract BudgetDao budgetDao();
    public abstract NetWorthSnapshotDao netWorthSnapshotDao();
    public abstract PriceAlertDao priceAlertDao();

    private static volatile AppDatabase INSTANCE;

    static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    /**
     * 6 → 7: adds the {@code net_worth_snapshot} table for the Analytics
     * net-worth trend chart. No existing columns changed; this is additive
     * and safe to run on any v6 database without data loss.
     */
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `net_worth_snapshot` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`investedValue` REAL NOT NULL, " +
                    "`cashFlow` REAL NOT NULL, " +
                    "`netWorth` REAL NOT NULL)");
        }
    };

    /**
     * 7 → 8: adds the {@code price_alert} table for user-configured stock
     * price thresholds consumed by {@link PriceAlertWorker}. Additive only —
     * existing transaction / stock / budget / snapshot data is untouched.
     */
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `price_alert` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`ticker` TEXT NOT NULL, " +
                    "`lowerBound` REAL, " +
                    "`upperBound` REAL, " +
                    "`lastNotifiedAt` INTEGER NOT NULL, " +
                    "`enabled` INTEGER NOT NULL)");
        }
    };

    static AppDatabase getDatabase(final Context context){
        if(INSTANCE==null){
            synchronized (AppDatabase.class){
                if(INSTANCE==null){
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "wealth_database")
                            .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                            // fallbackToDestructiveMigration stays as a safety net
                            // for older / broken schemas — but the explicit
                            // migrations run first and preserve user data.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
