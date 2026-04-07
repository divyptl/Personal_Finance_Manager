package com.example.personalfinancemanager;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import java.util.List;

public class StockViewModel extends AndroidViewModel {

    private StockRepository repository;
    private LiveData<List<Stock>> allStocks;

    public StockViewModel(@NonNull Application application) {
        super(application);
        repository = new StockRepository(application);
        allStocks = repository.getAllStocks();
    }

    public LiveData<List<Stock>> getAllStocks() {
        return allStocks;
    }

    // Updated to accept the brokerName
    public void buyStock(String ticker, String symbolToken, double quantity, double averageBuyPrice, String brokerName) {
        repository.insert(new Stock(ticker, symbolToken, quantity, averageBuyPrice, brokerName));
    }
}