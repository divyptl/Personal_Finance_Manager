# WealthFlow — Technical Documentation

**Version:** 1.0 (Room DB v8)
**Platform:** Android (minSdk 24 / targetSdk 36)
**Language:** Java 11
**Document Date:** April 2026

---

## 1. Project Overview

### Executive Summary
**WealthFlow** is a privacy-first, offline-capable Android personal finance manager that unifies three historically separate domains — day-to-day expense tracking, household budgeting, and investment portfolio monitoring — into a single cohesive mobile experience. The application ingests financial activity automatically (via bank/UPI SMS parsing) and manually (via a quick-entry dialog), stores everything locally in an encrypted SQLite (Room) database, and surfaces actionable insights through a reorderable dashboard, category breakdowns, budget progress indicators, and live broker-linked holdings.

### Problem Solved
Most finance apps in the Indian market fall into one of two camps:
1. **Aggregator apps** (e.g. CRED, Walnut) — convenient but require surrendering SMS read access and transaction history to a third-party cloud.
2. **Broker apps** (e.g. Zerodha Kite, Upstox Pro) — great for trading but blind to everyday spending.

WealthFlow bridges this gap by keeping **all data on-device** (EncryptedSharedPreferences + Room), while still offering optional read-only OAuth integration with Indian brokers (Upstox, Angel One) for live portfolio NAV. No analytics SDK, no ad network, no cloud sync.

### Primary Objectives
| # | Objective | Implementation |
|---|-----------|----------------|
| 1 | Zero-effort expense capture | SMS BroadcastReceiver + regex parser |
| 2 | Data privacy by default | AndroidX Security + Biometric app lock |
| 3 | Unified net worth view | Transactions + live holdings reconciled nightly |
| 4 | Proactive financial health | Budget alerts + price alerts via WorkManager |
| 5 | Accessibility-first UI | TalkBack announcements, light/dark/system theming |

### Tech Stack
- **Core:** Java 11, AndroidX (AppCompat, RecyclerView, CoordinatorLayout, SwipeRefreshLayout)
- **Persistence:** Room 2.x (v8 schema), EncryptedSharedPreferences, SharedPreferences
- **Concurrency:** `ExecutorService` (DB writes), WorkManager (periodic checks), LiveData (UI binding)
- **Networking:** OkHttp3 + custom Interceptors, Upstox & Angel One REST APIs
- **Security:** AndroidX Biometric (`BiometricPrompt` + DEVICE_CREDENTIAL fallback), AES-256 (Jetpack Security)
- **UI:** Material Components, MPAndroidChart (pie), ViewPager2 (onboarding)
- **Architecture:** MVVM + Repository + manual DI (`ServiceLocator`)

---

## 2. File Structure and Component Description

### 2.1 Application Bootstrap & Core Infrastructure

| File | Role | Description |
|------|------|-------------|
| `WealthFlowApplication.java` | Application class | Initializes `ServiceLocator`, applies saved theme via `ThemeController`, schedules periodic WorkManager jobs (budget + price alerts). |
| `ServiceLocator.java` | Manual DI | Lazy singletons for `AppDatabase`, `OkHttpClient`, repositories, token managers. Avoids Dagger overhead. |
| `AppDatabase.java` | Room DB | Schema v8. Entities: `Transaction`, `Budget`, `Stock`, `Subscription`, `PriceAlert`, `NetWorthSnapshot`. Provides `databaseWriteExecutor` for off-main-thread writes. |
| `NetworkModule.java` | HTTP client factory | Builds `OkHttpClient` with `StandardHeadersInterceptor`, 15 s timeouts, and certificate-pinned endpoints per `network_security_config.xml`. |
| `StandardHeadersInterceptor.java` | OkHttp interceptor | Adds `User-Agent`, `Accept: application/json`, and broker-specific `X-ClientType` headers. |

### 2.2 Security & Credentials

| File | Role | Description |
|------|------|-------------|
| `CredentialManager.java` | Encrypted prefs wrapper | Stores biometric-lock toggle, theme mode (`system`/`light`/`dark`), onboarding-complete flag. |
| `BiometricGate.java` | Biometric orchestrator | Wraps `BiometricPrompt` with a 5-minute in-process grace window (`hasRecentAuth`) so users aren't re-prompted during active sessions. Two-callback API: `onAuth` + optional `onCancel`. |
| `LockActivity.java` | Cold-start gate | Shown before `MainActivity` when biometric lock is enabled. Calls `BiometricGate.markAuthenticated()` on success. |
| `UpstoxTokenManager.java` / `AngelOneTokenManager.java` | OAuth token store | Persist access/refresh tokens in `EncryptedSharedPreferences`; handle refresh flow with exponential backoff. |

### 2.3 Data Model (Room Entities & DAOs)

| Entity | DAO | Purpose |
|--------|-----|---------|
| `Transaction.java` | `TransactionDao.java` | Expense/income record: amount, category, merchant, date, source (manual/SMS). |
| `Budget.java` | `BudgetDao.java` | Per-category monthly spending limit. |
| `Stock.java` | `StockDao.java` | Holding: symbol, quantity, avgBuyPrice, lastPrice, broker. |
| `Subscription.java` | *(inline in Activity)* | Detected recurring charges (Netflix, Spotify, etc.). |
| `PriceAlert.java` | `PriceAlertDao.java` | Symbol + threshold + direction; triggers notification when hit. |
| `NetWorthSnapshot.java` | `NetWorthSnapshotDao.java` | Daily rollup: cash + holdings at timestamp. Drives trend chart. |

**Projection classes** (not entities, just Room `@Query` results):
- `MonthlyTotal.java` — `SUM(amount)` grouped by month.
- `MonthlyCategoryTotal.java` — above, further grouped by category.
- `CategorySum.java` — category totals for the pie chart.

### 2.4 Repositories & ViewModels (MVVM)

| File | Role |
|------|------|
| `TransactionRepository.java` | Wraps `TransactionDao`; exposes `LiveData<List<Transaction>>` + CRUD on `databaseWriteExecutor`. |
| `TransactionViewModel.java` | Holds filtered/searched transaction list + selected-month state. |
| `StockRepository.java` | Coalesces broker API responses with local `StockDao`; cache-first with 60s TTL. |
| `StockViewModel.java` | Exposes holdings + derived net-worth total to `PortfolioActivity`. |

### 2.5 UI — Activities

| File | Screen |
|------|--------|
| `OnboardingActivity.java` | 3-page ViewPager2 intro shown on first launch. Gated by `CredentialManager.isOnboardingComplete()`. |
| `MainActivity.java` | Dashboard: balance card, expense pie, quick-action row, recent transactions. Applies `DashboardOrderController` order on create. Hosts settings popup (theme, lock, export, reorder, price alerts, subscriptions). |
| `DashboardOrderActivity.java` | Drag-to-reorder screen for the four dashboard cards. Uses `ItemTouchHelper` with handle-only drag. |
| `BudgetActivity.java` | List/add/remove monthly category budgets. Snackbar undo on remove. |
| `AnalyticsActivity.java` | Month-over-month trend line + category breakdown stacked bars. |
| `PortfolioActivity.java` | Live holdings list; biometric-gated entry; logout broker action; re-auth scrim. |
| `SubscriptionActivity.java` | Auto-detected recurring payments (monthly pattern in transactions). |
| `PriceAlertActivity.java` | Create/delete stock price alerts (triggers `PriceAlertWorker`). |
| `UpstoxLoginActivity.java` / `UpstoxOAuthCallbackActivity.java` | In-app WebView OAuth flow for Upstox broker linking. |

### 2.6 UI — Adapters & Dialogs

| File | Purpose |
|------|---------|
| `TransactionAdapter.java` | RecyclerView adapter for transaction list; inline recategorize dropdown; swipe-to-delete with undo. |
| `BudgetAdapter.java` | Progress-bar budget row with spent/limit and % color coding. |
| `StockAdapter.java` | Holding row: symbol, qty, LTP, P&L chip (green/red). |
| `PriceAlertAdapter.java` | Alert row with delete button (contentDescription wired for TalkBack). |
| `SubscriptionAdapter.java` | Detected subscription row with merchant + cadence label. |
| `TransactionEditorDialog.java` | Add/edit-transaction BottomSheet; amount, category spinner, date picker. |

### 2.7 Background Workers & Receivers

| File | Trigger | Purpose |
|------|---------|---------|
| `SmsReceiver.java` | `SMS_RECEIVED` broadcast | Filters financial SMS, delegates to `SmsTransactionParser`, inserts into DB. |
| `SmsTransactionParser.java` | Called by receiver | Regex matchers for HDFC/SBI/ICICI/Axis/UPI formats → `Transaction`. |
| `BudgetCheckWorker.java` | Daily WorkManager | Compares month-to-date spend vs limit; fires notification at 80 % / 100 %. |
| `BudgetChecker.java` | Helper | Pure logic for threshold checks; unit-testable. |
| `PriceAlertWorker.java` | 15-min periodic WorkManager | Fetches quotes, compares against active `PriceAlert` rows, fires notification on cross. |
| `SubscriptionDetector.java` | On-demand | Scans last 90 days of transactions for ~monthly recurring merchants. |
| `NetWorthTracker.java` | Daily | Snapshots cash + holdings total into `NetWorthSnapshot` table. |

### 2.8 Cross-Cutting Utilities

| File | Role |
|------|------|
| `ThemeController.java` | Reads saved mode, calls `AppCompatDelegate.setDefaultNightMode()`. Invoked on app start and on user theme change. |
| `DashboardOrderController.java` | Persists card order (CSV in plain prefs) and re-parents the four section views in a `LinearLayout` according to saved order. |
| `TransactionCsvExporter.java` | Generates RFC-4180 CSV of all transactions; writes via Storage Access Framework. |
| `NotificationHelper.java` | Channel creation (`alerts`, `budgets`) + notification builders. |
| `CasPdfParser.java` | Parses CAMS/NSDL Consolidated Account Statement PDFs for bulk holding import. |

### 2.9 Resource Organization

```
res/
├── values/                  ← LIGHT theme palette (F8FAFC / 0F172A)
│   ├── colors.xml
│   ├── themes.xml           ← windowLightStatusBar = true
│   └── strings.xml          ← ~200 localizable strings
├── values-night/            ← DARK theme palette (0A0E14 / E8E9ED)
│   ├── colors.xml
│   └── themes.xml
├── layout/                  ← 28 activity + dialog + item XMLs
├── drawable/                ← Shape drawables for cards, chips, progress
├── xml/
│   ├── network_security_config.xml   ← cert pinning for broker APIs
│   ├── backup_rules.xml              ← excludes tokens + DB from Auto-Backup
│   └── data_extraction_rules.xml     ← Android 12+ cloud/device rules
└── mipmap-*/                ← adaptive launcher icons
```

---

## 3. Execution Workflow

### 3.1 Cold-Start Sequence

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. WealthFlowApplication.onCreate()                              │
│    ├── ServiceLocator.initialize(this)                           │
│    │     └── builds Room DB, OkHttpClient, repositories          │
│    ├── ThemeController.applyFromPrefs(cm)                        │
│    │     └── setDefaultNightMode(SYSTEM|LIGHT|DARK)              │
│    └── WorkManager.enqueueUniquePeriodicWork(budget, priceAlert) │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 2. Launcher Activity resolution                                  │
│    ├── if !onboardingComplete   → OnboardingActivity             │
│    ├── else if biometricLock    → LockActivity                   │
│    │                                └── BiometricGate.require()  │
│    │                                     └── markAuthenticated() │
│    └── else                     → MainActivity                   │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 3. MainActivity.onCreate()                                       │
│    ├── setContentView(activity_main)                             │
│    ├── applyDashboardOrder()                                     │
│    │     └── DashboardOrderController.applyOrder(parent, views…) │
│    ├── ViewModelProvider.get(TransactionViewModel.class)         │
│    ├── vm.getTransactions().observe(this, adapter::submitList)   │
│    └── updateCardDisplay()                                       │
│         └── announceBalanceCardState()  ← TalkBack               │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 Automatic Transaction Capture

```
Bank SMS arrives
      ↓
SmsReceiver.onReceive(context, intent)
      ↓
SmsTransactionParser.parse(sender, body)
      ↓
   ┌──── match? ──── no ──→ drop (not financial)
   │
   yes
      ↓
Transaction(merchant, amount, category, date, SOURCE_SMS)
      ↓
AppDatabase.databaseWriteExecutor.execute(() ->
    transactionDao.insert(tx))
      ↓
Room invalidates LiveData → MainActivity RecyclerView re-binds
      ↓
BudgetChecker.check(tx.category) → maybe NotificationHelper.notifyBudget()
```

### 3.3 Portfolio Refresh (Pull-to-Refresh)

```
User swipes down on PortfolioActivity
      ↓
SwipeRefreshLayout → StockViewModel.refresh()
      ↓
StockRepository.refresh()
      ↓
   ┌──── cached < 60 s? ──── yes ──→ return cache (no network)
   │
   no
      ↓
UpstoxBrokerApi.getHoldings()  [OkHttp + StandardHeadersInterceptor]
      ↓
   ┌──── 401? ──── yes ──→ UpstoxTokenManager.refresh()  → retry
   │
   no
      ↓
StockDao.replaceAll(freshList) in transaction
      ↓
LiveData emits → StockAdapter re-binds → SwipeRefresh stops
      ↓
NetWorthTracker.snapshotIfNewDay(cash + holdingsTotal)
```

### 3.4 Dashboard Reorder Flow

```
Settings → "Reorder cards"
      ↓
DashboardOrderActivity (ItemTouchHelper drag UP|DOWN)
      ↓
onMove() → Collections.swap(items, from, to)
      ↓
DashboardOrderController.saveOrder(items)  [CSV in SharedPreferences]
      ↓
User taps Back
      ↓
(Next time MainActivity.onCreate fires)
      ↓
applyDashboardOrder() → removeView × 4 + addView × 4 in new order
```

---

## 4. Example and Dry Run

### 4.1 Scenario
User "Priya" has WealthFlow installed with biometric lock enabled. At **2:47 PM on 18 Apr 2026** she buys coffee at a café; the bank sends an SMS. She then opens the app to check her Food budget.

### 4.2 Sample Input

**Inbound SMS (from `HDFCBK`):**
```
Thx for using HDFC Bank Debit Card ending 1234 for Rs.185.00 at
BLUE TOKAI COFFEE on 18-04-26. Avl bal: Rs.42,650.15. Not you?
Call 18002586161.
```

**User's existing state:**
```
Budget: { category: "Food", monthlyLimit: 5000 }
Spend-to-date (Apr): 4880.00
Theme: SYSTEM
Dashboard order: [balance, actions, pie, transactions]  (customized)
```

### 4.3 Step-by-Step Trace

#### Step 1 — SMS Reception
`SmsReceiver.onReceive()` fires. Extracts sender `HDFCBK` and body.

```java
// SmsReceiver (abbreviated)
for (SmsMessage msg : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
    Transaction tx = parser.parse(msg.getOriginatingAddress(), msg.getMessageBody());
    if (tx != null) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long id = dao.insert(tx);
            budgetChecker.check(context, tx.getCategory());
        });
    }
}
```

#### Step 2 — Parsing
`SmsTransactionParser.parse()` matches the HDFC debit regex:

```
Regex:  Rs\.(\d+\.\d{2}) at ([A-Z][A-Z0-9 ]+) on (\d{2}-\d{2}-\d{2})
Groups: [1]=185.00  [2]=BLUE TOKAI COFFEE  [3]=18-04-26
```

**Intermediate object:**
```java
Transaction {
    id          = 0                    // auto-generated by Room
    amount      = 185.00
    merchant    = "BLUE TOKAI COFFEE"
    category    = "Food"               // merchant→category map
    date        = 2026-04-18T14:47:03Z
    source      = SOURCE_SMS
    isIncome    = false
    note        = null
}
```

#### Step 3 — Persistence
```java
// TransactionDao.insert(tx) returns 4217
// Room notifies LiveData observers:
mainActivityViewModel.transactions: List<Transaction>
    size: 143 → 144
    [0] = { id: 4217, amount: 185.00, merchant: "BLUE TOKAI COFFEE", ... }
```

#### Step 4 — Budget Threshold Check
```java
// BudgetChecker (pure logic)
double spent = txDao.sumForCategoryThisMonth("Food");   // 4880.00 + 185.00 = 5065.00
Budget b     = budgetDao.byCategory("Food");             // { limit: 5000 }
double pct   = spent / b.getMonthlyLimit();              // 1.013

if (pct >= 1.0) fire("Food budget exceeded: ₹5,065 / ₹5,000");
```

**NotificationHelper emits:**
```
Channel:  budgets
Title:    Food budget exceeded
Body:     You've spent ₹5,065 of your ₹5,000 monthly Food budget.
Action:   Open BudgetActivity
```

#### Step 5 — Priya Opens the App
`LockActivity` shows (biometric lock is on). She authenticates with fingerprint.

```java
BiometricGate.require(activity, "Unlock WealthFlow", "...",
    () -> { markAuthenticated(); startActivity(MainActivity); },
    null);
// lastAuthAt = 1713451650000   (timestamp for 5-min grace window)
```

#### Step 6 — Dashboard Composition
`MainActivity.onCreate()`:

```java
setContentView(R.layout.activity_main);
applyDashboardOrder();
```

`DashboardOrderController.applyOrder()` reads saved order `"balance,actions,pie,transactions"` and re-parents the `LinearLayout` children:

**Before (XML default):**
```
[cardBalance, pieChart, sectionActions, sectionTransactions]
```

**After applyOrder():**
```
[cardBalance, sectionActions, pieChart, sectionTransactions]
```

#### Step 7 — LiveData Binds
`TransactionViewModel` observes `TransactionDao.observeAllForMonth(2026-04)`. Room returns 144 rows including the just-inserted coffee transaction. `TransactionAdapter.submitList()` diffs and binds row 0:

```
┌─────────────────────────────────────────────────────┐
│ 🍔 BLUE TOKAI COFFEE          −₹185.00             │
│    Food · Today 2:47 PM                             │
└─────────────────────────────────────────────────────┘
```

`announceBalanceCardState()` fires for TalkBack users:

```
TalkBack speaks:
  "Balance this month: minus 5,065 rupees"
```

#### Step 8 — Budget Row
Scrolling to `BudgetActivity` would show:

```
Food                                  ₹5,065 / ₹5,000
████████████████████████████████████ 101%  (red)
```

### 4.4 Final Expected Output

| Surface | Observable State |
|---------|------------------|
| **Database** | `transactions` table: +1 row (id=4217, amount=185.00). |
| **Notification tray** | 1 notification: "Food budget exceeded". |
| **MainActivity dashboard** | Top card order = balance → actions → pie → transactions. Recent list shows coffee tx as row 0. |
| **Analytics screen** | Food category bar for April rises from ₹4,880 to ₹5,065; exceeds limit line. |
| **NetWorthSnapshot table** | New row for 2026-04-18 with cash total −₹185 vs previous snapshot (if first transaction of day). |
| **TalkBack** | Announces updated balance card when card re-binds. |

### 4.5 Data Flow at a Glance

```
Bank SMS
   │
   ▼
SmsReceiver ──► SmsTransactionParser ──► Transaction(POJO)
                                              │
                                              ▼
                              databaseWriteExecutor.execute()
                                              │
                                              ▼
                                      TransactionDao.insert()
                                              │
                          ┌───────────────────┼───────────────────┐
                          ▼                   ▼                   ▼
                    LiveData emit       BudgetChecker       NetWorthTracker
                          │                   │                   │
                          ▼                   ▼                   ▼
                  TransactionAdapter   NotificationHelper   NetWorthSnapshotDao
                          │                   │
                          ▼                   ▼
                  RecyclerView row     System notification
                          │
                          ▼
              announceBalanceCardState()
                          │
                          ▼
                   TalkBack speech
```

---

## Appendix A — Build & Deployment

**Gradle commands:**
```bash
./gradlew compileDebugJavaWithJavac   # type-check only
./gradlew assembleDebug               # produces app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease             # signed release build (needs keystore.properties)
```

**Required `local.properties` entries (not in VCS):**
```properties
sdk.dir=…
UPSTOX_API_KEY=…
UPSTOX_API_SECRET=…
UPSTOX_REDIRECT_URI=https://wealthflow.app/oauth/upstox
ANGELONE_API_KEY=…
```

**Minimum runtime permissions:**
- `RECEIVE_SMS`, `READ_SMS` — for automatic transaction capture
- `USE_BIOMETRIC` — for app lock & sensitive-action gates
- `POST_NOTIFICATIONS` (Android 13+) — budget & price alerts
- `INTERNET` — broker APIs only

## Appendix B — Glossary

| Term | Meaning |
|------|---------|
| **Grace window** | 5-minute period after successful biometric auth during which sensitive actions don't re-prompt. |
| **Snapshot** | Daily net-worth rollup row in `NetWorthSnapshot` used for the trend chart. |
| **Scrim** | Semi-opaque full-screen overlay shown while awaiting biometric re-auth on Portfolio entry. |
| **Handle-only drag** | Drag-to-reorder started from a dedicated handle ImageView, not long-press on the whole row — prevents accidental grabs while scrolling. |

---

*End of document.*
