package com.example.personalfinancemanager;
import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import java.util.List;

public class TransactionViewModel extends AndroidViewModel {
    private TransactionRepository mRepository;
    private final LiveData<List<Transaction>> mAllTransactions;
    private final LiveData<List<CategorySum>> mCategorySums;

    public TransactionViewModel(@NonNull Application application) {
        super(application);
        mRepository = new TransactionRepository(application);
        mAllTransactions = mRepository.getAllTransactions();
        mCategorySums = mRepository.getCategorySum();
    }

    public LiveData<List<Transaction>> getAllTransactions() {
        return mAllTransactions;
    }

    public LiveData<List<CategorySum>> getCategorySums() {
        return mCategorySums;
    }

    public void insert(Transaction transaction) {
        mRepository.insert(transaction);
    }

    public androidx.lifecycle.LiveData<java.util.List<Transaction>> getCurrentMonthTransactions(long startOfMonthMillis) {
        return mRepository.getCurrentMonthTransactions(startOfMonthMillis);
    }

    public void deleteAllTransactions() {
        mRepository.deleteAllTransactions();
    }


    public androidx.lifecycle.LiveData<java.util.List<Transaction>> getTransactionsByMonth(long startMillis, long endMillis) {
        return mRepository.getTransactionsByMonth(startMillis, endMillis);
    }

    public void updateTransaction(int id, String message, double amount,
                                  long timestamp, String type, String category) {
        mRepository.updateTransaction(id, message, amount, timestamp, type, category);
    }

    public void deleteTransaction(int id) {
        mRepository.deleteTransaction(id);
    }
}