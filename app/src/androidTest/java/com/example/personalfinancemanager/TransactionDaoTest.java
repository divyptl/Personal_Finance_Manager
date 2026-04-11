package com.example.personalfinancemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * In-memory Room tests for {@link TransactionDao}.
 *
 * <p>These run on a connected device or emulator. The database is built
 * with {@code Room.inMemoryDatabaseBuilder} so each test starts with an
 * empty schema and nothing leaks between tests.
 *
 * <p>The {@link androidx.arch.core.executor.testing.InstantTaskExecutorRule}
 * makes LiveData synchronous so we can assert on it directly.
 */
@RunWith(AndroidJUnit4.class)
public class TransactionDaoTest {

    @Rule
    public androidx.arch.core.executor.testing.InstantTaskExecutorRule instantRule =
            new androidx.arch.core.executor.testing.InstantTaskExecutorRule();

    private AppDatabase db;
    private TransactionDao dao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.transactionDao();
    }

    @After
    public void closeDb() throws IOException {
        db.close();
    }

    @Test
    public void insert_then_read_returns_inserted_transaction() throws InterruptedException {
        Transaction t = new Transaction("Rs.500 at Zomato", 500.0,
                System.currentTimeMillis(), "expense", "Food & Dining");
        dao.insert(t);

        List<Transaction> all = getValue(dao.getAllTransactions());
        assertEquals(1, all.size());
        assertEquals(500.0, all.get(0).getAmount(), 0.001);
        assertEquals("Food & Dining", all.get(0).getCategory());
    }

    @Test
    public void getTransactionsByMonth_filters_by_timestamp_range() throws InterruptedException {
        long now = System.currentTimeMillis();
        long oneDay = TimeUnit.DAYS.toMillis(1);

        dao.insert(new Transaction("yesterday", 100, now - oneDay, "expense", "Other"));
        dao.insert(new Transaction("today",     200, now,           "expense", "Other"));
        dao.insert(new Transaction("two weeks ago", 300, now - (14 * oneDay), "expense", "Other"));

        List<Transaction> recent = getValue(
                dao.getTransactionsByMonth(now - (3 * oneDay), now + oneDay));
        assertEquals("Should include only the two recent ones", 2, recent.size());
    }

    @Test
    public void deleteAllTransactions_empties_table() throws InterruptedException {
        dao.insert(new Transaction("a", 1, 1L, "expense", "Other"));
        dao.insert(new Transaction("b", 2, 2L, "expense", "Other"));
        assertEquals(2, getValue(dao.getAllTransactions()).size());

        dao.deleteAllTransactions();
        assertTrue(getValue(dao.getAllTransactions()).isEmpty());
    }

    @Test
    public void transactions_ordered_descending_by_timestamp() throws InterruptedException {
        dao.insert(new Transaction("old", 1, 1000L, "expense", "Other"));
        dao.insert(new Transaction("new", 2, 9999L, "expense", "Other"));

        List<Transaction> all = getValue(dao.getAllTransactions());
        assertEquals("new", all.get(0).getMessage());
        assertEquals("old", all.get(1).getMessage());
    }

    /** Pulls the current value out of a LiveData synchronously. */
    private static <T> T getValue(LiveData<T> liveData) throws InterruptedException {
        Object[] data = new Object[1];
        CountDownLatch latch = new CountDownLatch(1);
        Observer<T> observer = new Observer<T>() {
            @Override
            public void onChanged(T t) {
                data[0] = t;
                latch.countDown();
                liveData.removeObserver(this);
            }
        };
        liveData.observeForever(observer);
        assertTrue("LiveData never emitted", latch.await(2, TimeUnit.SECONDS));
        @SuppressWarnings("unchecked")
        T result = (T) data[0];
        assertNotNull(result);
        return result;
    }
}
