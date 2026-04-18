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

    public void updateTransaction(int id, String message, double amount,
                                  long timestamp, String type, String category) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                mTransactionDao.updateTransactionFields(id, message, amount, timestamp, type, category));
    }

    public void deleteTransaction(int id) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                mTransactionDao.deleteTransactionById(id));
    }

    /** Bulk restore — re-inserts rows with fresh primary keys (Room ignores old id). */
    public void restoreAll(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) return;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Clear ids so Room autogenerates fresh ones on insert.
            List<Transaction> fresh = new java.util.ArrayList<>(transactions.size());
            for (Transaction t : transactions) {
                Transaction copy = new Transaction(
                        t.getMessage(), t.getAmount(), t.getTimestamp(),
                        t.getType(), t.getCategory());
                fresh.add(copy);
            }
            mTransactionDao.insertAll(fresh);
        });
    }
}
