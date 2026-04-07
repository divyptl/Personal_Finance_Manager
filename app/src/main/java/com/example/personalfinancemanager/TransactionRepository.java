package com.example.personalfinancemanager;
import android.app.Application;
import androidx.lifecycle.LiveData;
import java.util.List;

public class TransactionRepository {
    private TransactionDao mTransactionDao;
    private LiveData<List<Transaction>> mAllTransactions;
    private LiveData<List<CategorySum>> mCategorySums;

    TransactionRepository(Application application){
        AppDatabase db = AppDatabase.getDatabase(application);
        mTransactionDao = db.transactionDao();
        mAllTransactions = mTransactionDao.getAllTransactions();
        mCategorySums = mTransactionDao.getExpenseByCategory();
    }
    LiveData<List<Transaction>> getAllTransactions(){
        return mAllTransactions;
    }

    LiveData<List<CategorySum>> getCategorySum(){
        return mCategorySums;
    }

    void insert(Transaction transaction){
        AppDatabase.databaseWriteExecutor.execute(() ->{
            mTransactionDao.insert(transaction);
        });
    }

    public androidx.lifecycle.LiveData<java.util.List<Transaction>> getCurrentMonthTransactions(long startOfMonthMillis) {
        return mTransactionDao.getCurrentMonthTransactions(startOfMonthMillis);
    }
    public void deleteAllTransactions() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            mTransactionDao.deleteAllTransactions();
        });
    }

    public androidx.lifecycle.LiveData<java.util.List<Transaction>> getTransactionsByMonth(long startMillis, long endMillis) {
        return mTransactionDao.getTransactionsByMonth(startMillis, endMillis);
    }

}
