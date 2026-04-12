package com.example.personalfinancemanager;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName = "budget_table")
public class Budget {

    @PrimaryKey
    @NonNull
    private String category;

    private double monthlyLimit;

    public Budget(@NonNull String category, double monthlyLimit) {
        this.category = category;
        this.monthlyLimit = monthlyLimit;
    }

    @NonNull
    public String getCategory() { return category; }
    public double getMonthlyLimit() { return monthlyLimit; }

    public void setCategory(@NonNull String category) { this.category = category; }
    public void setMonthlyLimit(double monthlyLimit) { this.monthlyLimit = monthlyLimit; }
}
