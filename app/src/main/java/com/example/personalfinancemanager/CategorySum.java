package com.example.personalfinancemanager;


import androidx.room.ColumnInfo;

public class CategorySum {
    @ColumnInfo(name = "category")
    public String category;
    @ColumnInfo(name = "total")
    public double total;
}
