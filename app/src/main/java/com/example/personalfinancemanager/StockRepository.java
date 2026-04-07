package com.example.personalfinancemanager;

import android.app.Application;
import androidx.lifecycle.LiveData;
import java.util.List;

public class StockRepository {
    private StockDao stockDao;
    private LiveData<List<Stock>> allStocks;

    public StockRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        stockDao = db.stockDao();
        allStocks = stockDao.getAllStocks();
    }

    public LiveData<List<Stock>> getAllStocks() {
        return allStocks;
    }

    public void insert(Stock stock) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            stockDao.insert(stock);
        });
    }
}