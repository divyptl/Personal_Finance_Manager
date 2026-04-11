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

@RunWith(AndroidJUnit4.class)
public class StockDaoTest {

    @Rule
    public androidx.arch.core.executor.testing.InstantTaskExecutorRule instantRule =
            new androidx.arch.core.executor.testing.InstantTaskExecutorRule();

    private AppDatabase db;
    private StockDao dao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.stockDao();
    }

    @After
    public void closeDb() throws IOException {
        db.close();
    }

    @Test
    public void insert_replaces_on_conflict() throws InterruptedException {
        dao.insert(new Stock("RELIANCE", "2885", 10, 2000, "AngelOne"));
        dao.insert(new Stock("RELIANCE", "2885", 15, 2100, "AngelOne"));

        List<Stock> stocks = getValue(dao.getAllStocks());
        assertEquals(1, stocks.size());
        assertEquals("Latest insert should win", 15.0, stocks.get(0).getQuantity(), 0.001);
        assertEquals(2100.0, stocks.get(0).getAverageBuyPrice(), 0.001);
    }

    @Test
    public void deleteStock_removes_only_target_row() throws InterruptedException {
        dao.insert(new Stock("RELIANCE", "2885", 10, 2000, "AngelOne"));
        dao.insert(new Stock("INFY",     "1594", 5,  1500, "AngelOne"));

        dao.deleteStock("RELIANCE");

        List<Stock> stocks = getValue(dao.getAllStocks());
        assertEquals(1, stocks.size());
        assertEquals("INFY", stocks.get(0).getTicker());
    }

    @Test
    public void deleteAll_empties_table() throws InterruptedException {
        dao.insert(new Stock("A", "1", 1, 1, "x"));
        dao.insert(new Stock("B", "2", 1, 1, "x"));

        dao.deleteAll();

        assertTrue(getValue(dao.getAllStocks()).isEmpty());
    }

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
