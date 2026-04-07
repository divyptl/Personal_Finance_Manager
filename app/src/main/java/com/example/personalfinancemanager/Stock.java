package com.example.personalfinancemanager;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "stock_table")
public class Stock {

    @PrimaryKey
    @NonNull
    private String ticker;
    private String symbolToken;
    private double quantity;
    private double averageBuyPrice;
    private String brokerName; // Supports Zerodha, Upstox, CAS, etc.

    public Stock(@NonNull String ticker, String symbolToken, double quantity, double averageBuyPrice, String brokerName) {
        this.ticker = ticker;
        this.symbolToken = symbolToken;
        this.quantity = quantity;
        this.averageBuyPrice = averageBuyPrice;
        this.brokerName = brokerName;
    }

    @NonNull
    public String getTicker() { return ticker; }
    public String getSymbolToken() { return symbolToken; }
    public double getQuantity() { return quantity; }
    public double getAverageBuyPrice() { return averageBuyPrice; }
    public String getBrokerName() { return brokerName; }
}