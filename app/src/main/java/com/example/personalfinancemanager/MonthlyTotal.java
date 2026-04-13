package com.example.personalfinancemanager;

import androidx.room.ColumnInfo;

/** Projection for monthly aggregate queries used by the Analytics dashboard. */
public class MonthlyTotal {
    @ColumnInfo(name = "month")
    public String month;   // "2026-04"

    @ColumnInfo(name = "total")
    public double total;
}
