package com.example.personalfinancemanager;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transaction_table")
public class Transaction {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String message;
    private double amount;
    private long timestamp;
    private String type; // "income" or "expense"
    private String category;

    public Transaction(String message, double amount, long timestamp, String type, String category) {
        this.message = message;
        this.amount = amount;
        this.timestamp = timestamp;
        this.type = type;
        this.category = category;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getMessage() { return message; }
    public double getAmount() { return amount; }
    public long getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public String getCategory() { return category; }
}