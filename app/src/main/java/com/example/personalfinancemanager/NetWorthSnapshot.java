package com.example.personalfinancemanager;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Daily snapshot of the user's net worth. Used to render the long-term trend
 * line on the Analytics screen.
 *
 * <p>We persist invested + cash-flow separately (in addition to the total)
 * so later features — e.g. "invested vs. cash split" overlay — don't require
 * a second schema migration.
 */
@Entity(tableName = "net_worth_snapshot")
public class NetWorthSnapshot {

    @PrimaryKey(autoGenerate = true)
    private int id;

    /** Wall-clock ms when the snapshot was taken. */
    private long timestamp;

    /** Sum over stock holdings of (quantity × avgBuyPrice), in ₹. */
    private double investedValue;

    /** Lifetime income minus lifetime expenses (cash position proxy), in ₹. */
    private double cashFlow;

    /** investedValue + cashFlow, cached for chart queries. */
    private double netWorth;

    public NetWorthSnapshot(long timestamp, double investedValue,
                            double cashFlow, double netWorth) {
        this.timestamp = timestamp;
        this.investedValue = investedValue;
        this.cashFlow = cashFlow;
        this.netWorth = netWorth;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public long getTimestamp() { return timestamp; }
    public double getInvestedValue() { return investedValue; }
    public double getCashFlow() { return cashFlow; }
    public double getNetWorth() { return netWorth; }
}
