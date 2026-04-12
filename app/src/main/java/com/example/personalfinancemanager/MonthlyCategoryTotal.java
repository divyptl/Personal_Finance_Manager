package com.example.personalfinancemanager;

import androidx.room.ColumnInfo;

/** Projection for monthly category breakdown queries used by the Analytics stacked bar chart. */
public class MonthlyCategoryTotal {
    @ColumnInfo(name = "month")
    public String month;      // "2026-04"

    @ColumnInfo(name = "category")
    public String category;

    @ColumnInfo(name = "total")
    public double total;
}
