package com.example.personalfinancemanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * User-configured price threshold on a single ticker. Evaluated daily by
 * {@link PriceAlertWorker}: when the latest traded price crosses a bound,
 * a notification is fired and {@link #lastNotifiedAt} is stamped so the
 * same alert cannot re-fire more than once every 24 h.
 *
 * <p>Bounds are independently nullable — users can set only an upper
 * target ("sell if it goes over X") or only a lower stop ("warn me if it
 * falls below Y"), or both.
 */
@Entity(tableName = "price_alert")
public class PriceAlert {

    @PrimaryKey(autoGenerate = true)
    private int id;

    /** Stock ticker, e.g. "RELIANCE". Case-sensitive match against Stock.ticker. */
    @NonNull
    private String ticker;

    /** Notify when LTP ≤ this value. Null disables the lower bound. */
    @Nullable
    private Double lowerBound;

    /** Notify when LTP ≥ this value. Null disables the upper bound. */
    @Nullable
    private Double upperBound;

    /** Wall-clock ms of the most recent notification for this alert. 0 = never. */
    private long lastNotifiedAt;

    /** When false the worker skips this row entirely. */
    private boolean enabled;

    public PriceAlert(@NonNull String ticker,
                      @Nullable Double lowerBound,
                      @Nullable Double upperBound,
                      long lastNotifiedAt,
                      boolean enabled) {
        this.ticker = ticker;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.lastNotifiedAt = lastNotifiedAt;
        this.enabled = enabled;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    @NonNull public String getTicker() { return ticker; }
    public void setTicker(@NonNull String ticker) { this.ticker = ticker; }

    @Nullable public Double getLowerBound() { return lowerBound; }
    public void setLowerBound(@Nullable Double lowerBound) { this.lowerBound = lowerBound; }

    @Nullable public Double getUpperBound() { return upperBound; }
    public void setUpperBound(@Nullable Double upperBound) { this.upperBound = upperBound; }

    public long getLastNotifiedAt() { return lastNotifiedAt; }
    public void setLastNotifiedAt(long lastNotifiedAt) { this.lastNotifiedAt = lastNotifiedAt; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
