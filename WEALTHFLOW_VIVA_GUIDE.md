# WealthFlow — Viva & Technical Mentorship Guide

> A beginner-friendly yet technically complete walkthrough of the Personal Finance Manager ("WealthFlow") Android project — architecture, data flow, every SQL query, viva prep, and future roadmap.

---

## 1. Complete Architecture and Process Flow

### 1.1 The Big Picture in Plain English
WealthFlow is a **native Android app written in Java** that helps a user track everyday expenses, set category budgets, and monitor stock holdings — all stored on the phone itself. Think of it as three mini-apps glued together:

1. **Expense Tracker** — pulls data from bank SMS automatically and lets the user add/edit manually.
2. **Budget Monitor** — compares month-to-date spend against user-set limits.
3. **Portfolio Viewer** — optionally logs into Upstox / Angel One to show live stock positions.

Because the user's money data is sensitive, **everything is local by default**: Room (SQLite) for structured data and EncryptedSharedPreferences (AES-256) for credentials. The only network traffic is the optional broker API call for live stock prices.

### 1.2 Architectural Layers

The app follows the **MVVM (Model–View–ViewModel) pattern** with a **Repository** layer — Google's officially recommended Android architecture.

```
┌─────────────────────────────────────────────────────────────────┐
│  VIEW  (Activities + XML layouts + RecyclerView Adapters)       │
│  e.g. MainActivity, BudgetActivity, PortfolioActivity           │
│  - Pure UI: no business logic, no direct DB calls               │
└─────────────────────────────────────────────────────────────────┘
                         ▲ observes LiveData
                         │
┌─────────────────────────────────────────────────────────────────┐
│  VIEWMODEL  (TransactionViewModel, StockViewModel)              │
│  - Survives screen rotation                                      │
│  - Exposes LiveData<List<Transaction>> to the View              │
│  - Asks the Repository for data, never the DAO directly         │
└─────────────────────────────────────────────────────────────────┘
                         ▲
                         │
┌─────────────────────────────────────────────────────────────────┐
│  REPOSITORY  (TransactionRepository, StockRepository)           │
│  - Single source of truth                                        │
│  - Decides: DB cache vs. network? refresh or return cache?      │
│  - Runs writes on databaseWriteExecutor (background thread)     │
└─────────────────────────────────────────────────────────────────┘
                         ▲                 ▲
                         │                 │
        ┌────────────────┘                 └─────────────────┐
        │                                                   │
┌───────────────────┐                         ┌─────────────────────┐
│  DAO (Room)       │                         │  BrokerApi (OkHttp) │
│  SQL → Java objs  │                         │  HTTPS → JSON → POJO│
└───────────────────┘                         └─────────────────────┘
        │                                                   │
        ▼                                                   ▼
┌───────────────────┐                         ┌─────────────────────┐
│  SQLite (Room DB) │                         │  Upstox / Angel One │
│  app database v8  │                         │  REST API           │
└───────────────────┘                         └─────────────────────┘
```

### 1.3 End-to-End Process Flow (cold start → interaction)

**Step 1 — App Launches (`WealthFlowApplication.onCreate`)**
- `ServiceLocator.initialize()` creates singletons: Room database, OkHttpClient, repositories, token managers.
- `ThemeController.applyFromPrefs()` reads saved theme (system/light/dark) and calls `AppCompatDelegate.setDefaultNightMode()`.
- WorkManager schedules two periodic background jobs: **BudgetCheckWorker** (daily) and **PriceAlertWorker** (15 min).

**Step 2 — Which screen opens first?**
- First time: `OnboardingActivity` (3-slide intro).
- Biometric lock on: `LockActivity` → `BiometricPrompt` → on success, `MainActivity`.
- Otherwise: straight to `MainActivity`.

**Step 3 — `MainActivity` composition**
1. `setContentView(R.layout.activity_main)`
2. `applyDashboardOrder()` reorders the 4 cards (balance, pie, actions, transactions) per the user's saved preference.
3. `ViewModelProvider.get(TransactionViewModel.class)` — ViewModel is cached by Android, survives rotation.
4. `vm.getTransactions().observe(this, adapter::submitList)` — Room's LiveData auto-pushes updates.

**Step 4 — Background SMS capture (always on)**
- `SmsReceiver` (BroadcastReceiver registered in manifest) fires on every inbound SMS.
- `SmsTransactionParser` regex-matches HDFC / ICICI / SBI / UPI formats.
- Parsed `Transaction` inserted via `databaseWriteExecutor`.
- Room invalidates queries → LiveData re-emits → RecyclerView updates automatically.
- `BudgetChecker` compares new total against limit → fires notification if over 80% / 100%.

### 1.4 Core Logic in Simple Terms
| Concept | What it does |
|---|---|
| **LiveData** | "Observable" data holder. UI subscribes, Room pushes updates. No polling needed. |
| **Room** | Turns Java interface methods into SQL at compile time. You write `@Query("SELECT …")`, Room generates the JDBC-like glue. |
| **Repository** | Front door to data. ViewModels don't know or care if data came from DB or network. |
| **WorkManager** | Android's reliable background job scheduler. Survives reboots and Doze mode. |
| **EncryptedSharedPreferences** | Like SharedPreferences but AES-256 encrypted using the Android Keystore. |
| **BiometricPrompt** | Unified fingerprint/face/PIN prompt. Falls back to device credential if no biometrics enrolled. |

---

## 2. The Final Tech Stack

### 2.1 Complete Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Language** | Java 11 | Core programming language |
| **Platform** | Android SDK (minSdk 24, targetSdk 36) | Runtime |
| **UI Framework** | AndroidX AppCompat + Material Components | Widgets, theming |
| **Layouts** | XML (ConstraintLayout, LinearLayout, CoordinatorLayout) | Declarative views |
| **Charts** | MPAndroidChart | Pie & line charts |
| **Architecture** | MVVM + Repository + Manual DI (ServiceLocator) | Separation of concerns |
| **Reactive data** | AndroidX LiveData + ViewModel | Lifecycle-aware data binding |
| **Database** | SQLite via Room 2.x (schema v8) | Local storage |
| **Secure storage** | AndroidX Security (EncryptedSharedPreferences, AES-256) | Tokens, secrets |
| **Concurrency** | ExecutorService + WorkManager | Background work |
| **Networking** | OkHttp3 + Interceptors | HTTPS to broker APIs |
| **JSON parsing** | `org.json` (built-in) | Broker API responses |
| **Authentication** | AndroidX Biometric | App lock + sensitive-action gates |
| **OAuth** | In-app WebView (UpstoxLoginActivity) | Broker login |
| **Build** | Gradle 9.3 (Kotlin DSL / Groovy) | Compile, package, sign |
| **VCS** | Git + GitHub | Source control |

### 2.2 Why These Technologies for a Finance App?

| Choice | Rationale |
|---|---|
| **Native Android (not cross-platform)** | Full access to SMS APIs (which React Native/Flutter struggle with) and Android Keystore for hardware-backed encryption. |
| **Java over Kotlin** | Lower barrier for team members still learning; mature ecosystem. (Migration to Kotlin is a future upgrade.) |
| **Room over raw SQLite** | Compile-time query validation — a typo like `SLECT` fails the build, not at runtime in front of a user. Critical when financial data is on the line. |
| **LiveData + ViewModel** | Automatic lifecycle management — no memory leaks on rotation, no stale observers. |
| **EncryptedSharedPreferences** | Broker OAuth tokens are bearer credentials; losing them = account takeover. AES-256 + Keystore prevents this even on rooted devices (with caveats). |
| **BiometricPrompt** | Standard, accessibility-compliant, uses platform-managed secure hardware. |
| **WorkManager over AlarmManager** | Respects Doze/battery optimizations; jobs persist across reboots — we need the budget check to run *even if the user never opens the app*. |
| **OkHttp + Interceptors** | Clean token refresh, header injection, and request logging without littering repositories with boilerplate. |
| **MPAndroidChart** | Battle-tested, zero-cost, and supports both pie and line charts with TalkBack labels. |
| **Offline-first SQLite** | A finance app must work on a flight, in a tunnel, with no signal. All data is local. |

---

## 3. Step-by-Step Dry Run

### Scenario
User manually adds an expense: **₹50 for "Groceries"** via the "+" FAB on the dashboard.

### Trace

```
╔══ STEP 1: USER INPUT (View layer) ══════════════════════════╗
  User taps FAB in MainActivity.
  onClick → TransactionEditorDialog.show(this).
  Dialog's layout: dialog_transaction.xml (EditText amount,
                   Spinner category, DatePicker date, Save btn).
╚══════════════════════════════════════════════════════════════╝
                              ↓
╔══ STEP 2: DIALOG CAPTURE ═══════════════════════════════════╗
  User types "50", picks "Groceries", presses Save.
  TransactionEditorDialog.onSaveClicked():

  Transaction tx = new Transaction(
      /* id        */ 0,                    // autoGenerate
      /* message   */ "Manual: Groceries",
      /* amount    */ 50.0,
      /* type      */ "expense",
      /* category  */ "Groceries",
      /* timestamp */ System.currentTimeMillis()
  );

  listener.onTransactionSaved(tx);   // callback to MainActivity
╚══════════════════════════════════════════════════════════════╝
                              ↓
╔══ STEP 3: VIEWMODEL / REPOSITORY ═══════════════════════════╗
  MainActivity → transactionViewModel.insert(tx);

  // TransactionViewModel.java
  public void insert(Transaction tx) {
      repository.insert(tx);
  }

  // TransactionRepository.java
  public void insert(Transaction tx) {
      AppDatabase.databaseWriteExecutor.execute(() -> {
          dao.insert(tx);            // ← off the main thread
      });
  }
╚══════════════════════════════════════════════════════════════╝
                              ↓
╔══ STEP 4: DAO → SQLITE ═════════════════════════════════════╗
  // TransactionDao.java (Room-generated at compile time)
  @Insert
  void insert(Transaction transaction);

  Generated SQL:
  INSERT INTO transaction_table (message, amount, type,
                                  category, timestamp)
  VALUES ('Manual: Groceries', 50.0, 'expense',
          'Groceries', 1713451650123);

  SQLite assigns id = 145 (autoincrement).
╚══════════════════════════════════════════════════════════════╝
                              ↓
╔══ STEP 5: ROOM INVALIDATES LIVEDATA ═══════════════════════╗
  Room's InvalidationTracker sees a write to transaction_table.
  All active queries against that table re-execute:
    • getAllTransactions()           → emits 145 rows
    • getTotalExpense()              → emits new SUM
    • getExpenseByCategory()         → "Groceries" bucket +₹50
    • getCurrentMonthTransactions()  → emits current-month list
  These LiveData holders deliver to all registered observers
  ON THE MAIN THREAD automatically.
╚══════════════════════════════════════════════════════════════╝
                              ↓
╔══ STEP 6: UI UPDATE ════════════════════════════════════════╗
  MainActivity registered:
      viewModel.getTransactions().observe(this, list -> {
          transactionAdapter.submitList(list);
          updateCardDisplay();        // recompute totals
          announceBalanceCardState(); // TalkBack announcement
      });

  Adapter DiffUtil diffs old vs new list → detects 1 inserted
  row → animates it sliding in at position 0 (sorted DESC by
  timestamp).

  Pie chart observer:
      viewModel.getExpenseByCategory().observe(this, sums -> {
          renderPie(sums);            // MPAndroidChart redraws
      });
╚══════════════════════════════════════════════════════════════╝
                              ↓
╔══ STEP 7: BUDGET CHECK (side effect) ══════════════════════╗
  BudgetChecker.check(context, "Groceries"):

    double spent = dao.getCategoryExpenseSince("Groceries",
                                                monthStart);
    // Say spent = 4,250 after this +50
    Budget b = budgetDao.getBudgetForCategory("Groceries");
    // b.monthlyLimit = 5000

    double pct = spent / b.monthlyLimit;     // 0.85
    if (pct >= 0.80 && !alreadyAlertedThisMonth) {
        NotificationHelper.notifyBudget(
            "Groceries at 85% of ₹5,000 limit");
    }
╚══════════════════════════════════════════════════════════════╝
                              ↓
                        FINAL STATE
  Screen:       New row animated at top of transactions list.
  Balance card: Total expense increased by ₹50, TalkBack speaks.
  Pie chart:    Groceries slice grew proportionally.
  Notification: "Groceries at 85%…" in system tray.
  Database:     1 new row in transaction_table (id=145).
```

---

## 4. SQL Queries Breakdown

### 4.1 Total Count
The project contains **38 distinct database operations** distributed across 5 DAOs:

| DAO | Ops | Entity |
|---|---|---|
| TransactionDao | 16 | transaction_table |
| PriceAlertDao | 7 | price_alert |
| BudgetDao | 6 | budget_table |
| StockDao | 5 | stock_table |
| NetWorthSnapshotDao | 4 | net_worth_snapshot |
| **Total** | **38** | — |

### 4.2 Every Query — With Trigger Points

#### TransactionDao (16 ops)
| # | Operation | SQL | Where it runs |
|---|---|---|---|
| 1 | `insert(Transaction)` | `INSERT INTO transaction_table(...) VALUES(...)` | Manual add, SMS auto-capture |
| 2 | `insertAll(List)` | Batch INSERT | "Undo" restore after Clear-All |
| 3 | `getAllTransactions()` | `SELECT * FROM transaction_table ORDER BY timestamp DESC` | MainActivity list observer |
| 4 | `getExpenseByCategory()` | `SELECT category, SUM(amount) AS total FROM transaction_table WHERE type IN ('expense','Debit') GROUP BY category` | Pie-chart on dashboard |
| 5 | `getTotalExpense()` | `SELECT SUM(amount) FROM transaction_table WHERE type IN ('expense','Debit')` | Balance card |
| 6 | `getCurrentMonthTransactions(startMs)` | `SELECT * … WHERE timestamp >= :startMs ORDER BY timestamp DESC` | Month auto-reset view |
| 7 | `deleteAllTransactions()` | `DELETE FROM transaction_table` | "Clear all data" destructive action |
| 8 | `updateTransactionFields(id,…)` | `UPDATE transaction_table SET message=?, amount=?, timestamp=?, type=?, category=? WHERE id=?` | Inline edit dialog |
| 9 | `deleteTransactionById(id)` | `DELETE FROM transaction_table WHERE id=?` | Swipe-to-delete row |
| 10 | `getAllTransactionsSync()` | Same as #3 (blocking) | CSV export on background thread |
| 11 | `getTransactionsByMonth(start,end)` | `SELECT * … WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp DESC` | Month spinner navigation |
| 12 | `getCategoryExpenseSince(cat,ms)` | `SELECT COALESCE(SUM(amount),0) … WHERE category=? AND timestamp>=? AND type IN (…)` | BudgetChecker threshold query |
| 13 | `getCategoryExpensesSinceSync(ms)` | `SELECT category, SUM(amount) AS total … GROUP BY category` | BudgetCheckWorker daily sweep |
| 14 | `getMonthlyExpenseTotals(ms)` | `SELECT strftime('%Y-%m', timestamp/1000,'unixepoch') AS month, SUM(amount) AS total … GROUP BY month ORDER BY month ASC` | Analytics line chart |
| 15 | `getMonthlyCategoryExpenses(ms)` | `SELECT month, category, SUM(amount) … GROUP BY month, category` | Analytics stacked bar |
| 16 | `getMonthlyIncomeTotals(ms)` | Same as #14 but for income | Analytics overlay |

#### BudgetDao (6 ops)
| # | Operation | SQL | Where |
|---|---|---|---|
| 17 | `insertOrUpdate(Budget)` | `INSERT OR REPLACE INTO budget_table …` | BudgetActivity add/edit dialog |
| 18 | `getAllBudgets()` | `SELECT * FROM budget_table ORDER BY category ASC` | BudgetActivity list |
| 19 | `getAllBudgetsSync()` | Same as #18 (blocking) | BudgetCheckWorker |
| 20 | `getBudgetForCategory(cat)` | `SELECT * FROM budget_table WHERE category=? LIMIT 1` | Threshold check on new tx |
| 21 | `deleteBudget(cat)` | `DELETE FROM budget_table WHERE category=?` | Swipe / Remove button |
| 22 | `deleteAll()` | `DELETE FROM budget_table` | "Delete all data" |

#### StockDao (5 ops)
| # | Operation | SQL | Where |
|---|---|---|---|
| 23 | `insert(Stock)` | `INSERT OR REPLACE INTO stock_table …` | Broker holding sync, manual add |
| 24 | `getAllStocks()` | `SELECT * FROM stock_table` | PortfolioActivity list |
| 25 | `deleteStock(ticker)` | `DELETE FROM stock_table WHERE ticker=?` | Remove holding |
| 26 | `deleteAll()` | `DELETE FROM stock_table` | "Delete all data" |
| 27 | `getAllStocksSync()` | Same as #24 (blocking) | NetWorthTracker snapshot |

#### PriceAlertDao (7 ops)
| # | Operation | SQL | Where |
|---|---|---|---|
| 28 | `insert(PriceAlert)` | `INSERT INTO price_alert …` | "Create alert" dialog |
| 29 | `update(PriceAlert)` | `UPDATE price_alert SET … WHERE id=?` | Edit alert |
| 30 | `delete(PriceAlert)` | `DELETE FROM price_alert WHERE id=?` | Delete button |
| 31 | `getAllLive()` | `SELECT * FROM price_alert ORDER BY ticker ASC` | PriceAlertActivity list |
| 32 | `getAllEnabledSync()` | `SELECT * FROM price_alert WHERE enabled=1` | PriceAlertWorker 15-min poll |
| 33 | `stampNotified(id,when)` | `UPDATE price_alert SET lastNotifiedAt=? WHERE id=?` | After firing notification (debounce) |
| 34 | `deleteAll()` | `DELETE FROM price_alert` | "Delete all data" |

#### NetWorthSnapshotDao (4 ops)
| # | Operation | SQL | Where |
|---|---|---|---|
| 35 | `insert(Snapshot)` | `INSERT INTO net_worth_snapshot …` | NetWorthTracker daily rollup |
| 36 | `getLatestSync()` | `SELECT * FROM net_worth_snapshot ORDER BY timestamp DESC LIMIT 1` | "Did we already snapshot today?" gate |
| 37 | `getSinceSync(ms)` | `SELECT * … WHERE timestamp>=? ORDER BY timestamp ASC` | Net-worth trend chart |
| 38 | `deleteAll()` | `DELETE FROM net_worth_snapshot` | "Delete all data" |

### 4.3 Notable Query Patterns
- **`COALESCE(SUM(amount), 0)`** — returns 0 when no rows match instead of NULL, so the caller can do plain `double` arithmetic without null-checks.
- **`strftime('%Y-%m', timestamp/1000, 'unixepoch')`** — SQLite date formatting on epoch-millis columns, used for server-side month bucketing (faster than Java-side grouping).
- **`INSERT OR REPLACE`** (OnConflictStrategy.REPLACE) — upsert pattern for budgets and stocks where primary key collisions should overwrite.
- **Sync vs LiveData variants** — every "hot" read has two forms: LiveData for the UI, blocking `Sync` for WorkManager background jobs.

---

## 5. Potential Viva Questions & Answers

### Basic / Conceptual

**Q1. What is MVVM and why did you use it?**
MVVM = Model-View-ViewModel. The **View** (Activity) only renders UI and forwards events. The **ViewModel** holds UI state and survives configuration changes (like screen rotation). The **Model** (Repository + DAO + entities) owns the data. Benefits: testability, no UI logic in Activities, automatic lifecycle handling.

**Q2. What is Room? How does it differ from raw SQLite?**
Room is an AndroidX library that sits on top of SQLite. You declare entities (`@Entity`), DAOs (`@Dao`), and the database (`@Database`). Room generates the boilerplate and validates SQL at **compile time**. Raw SQLite requires manually writing `SQLiteOpenHelper`, cursors, and string SQL — errors surface only at runtime.

**Q3. What is LiveData?**
A lifecycle-aware observable data holder. The UI calls `.observe(this, callback)`; when the data changes, the callback fires — but only if the Activity is in STARTED or RESUMED state. Prevents crashes from updating dead views.

**Q4. Explain the difference between SharedPreferences and EncryptedSharedPreferences.**
Both store key-value pairs in an XML file. Plain SharedPreferences are readable by root or a malicious backup. EncryptedSharedPreferences uses AES-256 with a key stored in Android Keystore (hardware-backed on modern devices). Used here for OAuth tokens.

**Q5. What are the four CRUD operations implemented?**
- **Create**: `@Insert` — e.g. `TransactionDao.insert(tx)`.
- **Read**: `@Query("SELECT …")` — e.g. `getAllTransactions()`.
- **Update**: `@Update` or `@Query("UPDATE …")` — e.g. `updateTransactionFields()`.
- **Delete**: `@Delete` or `@Query("DELETE …")` — e.g. `deleteTransactionById()`.

**Q6. What is a BroadcastReceiver and where do you use one?**
A component that listens for system or app-wide events. `SmsReceiver` subscribes to `android.provider.Telephony.SMS_RECEIVED` and runs `onReceive()` whenever an SMS arrives — even if the app's UI isn't open.

**Q7. What is WorkManager and why not AlarmManager?**
WorkManager is the modern API for deferrable, guaranteed-execution background work. It respects Android's Doze and App Standby restrictions and survives reboots. `BudgetCheckWorker` runs once per day even if the app is never opened — AlarmManager alone can't make that guarantee post-Android 8.

### Code-Specific / Logical

**Q8. Why is `databaseWriteExecutor` used for inserts?**
Room forbids I/O on the main thread (would freeze UI and trigger ANR). The executor is a single-thread `ExecutorService` so all writes are serialized in insertion order — no race conditions.

**Q9. Why does `BiometricGate` have a `hasRecentAuth()` grace window?**
Without it, every sensitive action (view portfolio, logout broker, delete data) would re-prompt the user even seconds after unlocking. A 5-minute in-process window keeps the app secure without being annoying. Process death resets the timer, which is fine because the app-lock screen gates cold start.

**Q10. Why did you use `@Query("UPDATE…")` instead of `@Update` for editing transactions?**
Room's `@Update` requires passing a full `Transaction` entity. Our entity is immutable (no public setters for `amount`/`category`). Writing a targeted `UPDATE` by id is cleaner and avoids having to re-read the row just to mutate it.

**Q11. Why do the analytics queries use SQLite's `strftime` instead of grouping in Java?**
Database-side aggregation means we transfer ~12 rows (months) instead of hundreds of transactions. Faster and less memory. The `timestamp/1000, 'unixepoch'` converts our epoch-millis column into SQLite's native seconds-epoch.

**Q12. Why is there both a LiveData and a `Sync` version of many queries?**
LiveData is observed from the UI thread and auto-updates. But `PriceAlertWorker` and `BudgetCheckWorker` run on background threads with no lifecycle owner — they need a blocking return. So we expose both flavors over the same underlying table.

**Q13. Explain the `ServiceLocator` pattern you used instead of Dagger/Hilt.**
Single class with static accessors that lazily construct and cache singletons (DB, OkHttp client, repos). Simpler than Dagger annotation processing, zero-cost at runtime, and sufficient for a small app. Trade-off: harder to mock in unit tests — future migration to Hilt is planned.

**Q14. Why is the drag handle the only drag trigger in `DashboardOrderActivity`?**
Android's default `ItemTouchHelper.isLongPressDragEnabled()` would let users accidentally grab a card while scrolling. Disabling it and triggering drag from a dedicated handle on `ACTION_DOWN` is the same UX Gmail and Google Tasks use.

**Q15. Why wrap `BudgetActivity` in `CoordinatorLayout`?**
Material `Snackbar` automatically shifts a FAB upward when shown — but only if both are children of `CoordinatorLayout`. Needed for the "Undo" Snackbar after budget removal not to cover the Add button.

### Database / Architecture

**Q16. What happens when you change the schema — how does Room handle it?**
Every `@Database(version = N)` bump requires either a `Migration` object describing the ALTER TABLE SQL, or `fallbackToDestructiveMigration()` (wipes data — only safe during development). We're on v8, meaning the DB was migrated 7 times.

**Q17. How do you ensure data integrity on a concurrent SMS insert + user manual insert?**
All writes funnel through `databaseWriteExecutor` (single thread). SQLite itself wraps each write in a transaction. Combined, concurrent producers never corrupt the table.

**Q18. Why are `onConflict = REPLACE` used on `Stock` but not `Transaction`?**
A Stock has a natural key (`ticker`) — re-syncing from the broker should overwrite the old row. Transactions don't have such a key; every insert is meant to be a new row (id auto-generated).

**Q19. How does the app handle offline mode?**
Everything reads from Room first. Broker holdings are cached; UI just shows the last synced price with a timestamp. Writes never hit the network — SMS capture, manual entry, budgets, alerts all persist locally and work with airplane mode on.

**Q20. What are the security trade-offs of keeping data local vs. in the cloud?**
**Local pros**: no server breach can leak user data; no GDPR/DPDP processor obligations; works offline.
**Local cons**: no cross-device sync; backup requires user action (CSV export); device loss = data loss unless the user enables Google Backup. We mitigate the last point by **excluding** sensitive tables from Auto-Backup via `backup_rules.xml` (tokens aren't backed up) while allowing transaction history to be restored.

---

## 6. Future Expansions & Scalability

### 6.1 Receipt OCR Scanner
**What:** Let users photograph a receipt; auto-fill amount, merchant, date, suggested category.
**How to build it on the current architecture:**
1. Add a CameraX or `ACTION_IMAGE_CAPTURE` flow from the `TransactionEditorDialog` ("📷 Scan receipt" button).
2. Feed the bitmap to **ML Kit Text Recognition** (on-device, no network, free) — returns lines of text with bounding boxes.
3. Write a `ReceiptParser` class (analogous to the existing `SmsTransactionParser`) that regex-matches total line ("TOTAL ₹…"), date, and vendor name.
4. Pre-fill the dialog fields; user reviews and taps Save → existing insert path.
**Effort:** ~2–3 days. **Schema change:** none.

### 6.2 Cloud Sync with End-to-End Encryption
**What:** Optional Firebase/Supabase backup so users can restore across devices.
**How:**
1. Add a `SyncManager` that serializes the Room DB to JSON and encrypts it client-side (libsodium / Tink) with a key derived from user password.
2. Upload to Firestore as an opaque blob — server sees nothing meaningful.
3. On a new device: user enters password → key derived → blob decrypted → Room re-populated via `insertAll()`.
4. Conflict resolution: last-write-wins per row, using a new `updatedAt` column.
**Effort:** 2–3 weeks. **Schema change:** add `updatedAt` to every entity (migration).

### 6.3 AI Expense Insights ("Where did my money go?")
**What:** Natural-language weekly summary — *"You spent 40% more on Food than last month, mostly at Blue Tokai."*
**How:**
1. Add a button "Get insights" in `AnalyticsActivity`.
2. Aggregate last 4 weeks via existing `getMonthlyCategoryExpenses` + a new weekly variant.
3. Send the aggregate (NOT raw transactions — privacy) to Claude API or an on-device model (Gemini Nano on supported devices).
4. Display the returned text in a card, cache in Room.
**Effort:** 1 week. **Schema change:** new `ai_insight` table for cache.

### 6.4 Bill-Split & Shared Wallets
**What:** Friends or spouses share a budget / ledger without a full social graph — invite by QR code.
**How:**
1. Introduce a `wallet_id` column on Transaction and Budget (migrate v8 → v9).
2. Use Firestore with security rules scoped to `wallet_id` for opt-in shared wallets; private-wallet data stays local.
3. QR code encodes a signed invite token; accepting adds the scanner to the wallet.
**Effort:** 3–4 weeks. **Schema change:** major — needs multi-user identity model.

### 6.5 Investment Goal Tracker
**What:** "I want ₹10L for a car in 3 years" — app projects trajectory, recommends monthly savings, shows gap vs. actual.
**How:**
1. New `goal_table` entity: id, name, targetAmount, targetDate, currentContributions.
2. Reuse existing `NetWorthTracker` snapshots to chart progress over time.
3. Add a `GoalActivity` with a simple line chart (actual vs. required trajectory) using MPAndroidChart.
4. Nightly `GoalCheckWorker` (piggy-back on WorkManager) fires a notification when the user falls behind.
**Effort:** 1–2 weeks. **Schema change:** one new table; bump Room v8 → v9 with migration.

### 6.6 Scalability Considerations (architectural hygiene)
| Concern | Current | Scaling strategy |
|---|---|---|
| **Transaction count > 50k rows** | Single table, no pagination | Add Room `PagingSource` + `Paging 3` library; keep SQL indexes on `timestamp`, `category`, `type`. |
| **Multi-module build time** | Single `app` module | Split into `:data`, `:domain`, `:feature-budget`, `:feature-portfolio`; enables parallel compilation. |
| **Test coverage** | Minimal | Add JUnit tests on repositories (using Room's in-memory DB) and ViewModels (using `InstantTaskExecutorRule` for LiveData). Target ≥ 60 %. |
| **Kotlin migration** | Java 11 codebase | Gradual file-by-file — Kotlin & Java interop is seamless. Start with ViewModels; keep Activities last. |
| **DI migration** | `ServiceLocator` | Move to **Hilt** — lets you swap implementations in tests (fake DB, fake broker API). |

---

## Appendix — Quick Cheatsheet for the Viva

| Concept | One-liner |
|---|---|
| **Room** | Compile-time-validated ORM over SQLite. |
| **LiveData** | Lifecycle-aware observable — no leaks. |
| **ViewModel** | Survives rotation; holds UI state. |
| **Repository** | Single source of truth between VM and DB/network. |
| **ServiceLocator** | Manual DI — static singletons. |
| **WorkManager** | Guaranteed deferred background work. |
| **EncryptedSharedPreferences** | AES-256 key-value store. |
| **BiometricPrompt** | Platform fingerprint/face/PIN UI. |
| **OkHttp Interceptor** | Middleware for HTTP (add headers, retry). |
| **CoordinatorLayout** | Root container for Material motion (FAB + Snackbar). |
| **ItemTouchHelper** | Drag-reorder & swipe-dismiss for RecyclerView. |
| **MVVM** | View → VM → Repo → Model, unidirectional. |

---

*End of guide. Good luck with the viva!*
