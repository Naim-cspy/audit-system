# Supermarket POS & Financial Audit System (Android)

An enterprise-grade, modern Android application for Supermarket Point of Sale (POS) and Financial Auditing, rewritten in **Kotlin** and **Jetpack Compose** with **Room (SQLite)** local persistence.

---

## Key Features

### 1. Cashier Point of Sale (POS)
- **Continuous Barcode Scanning & Quick-Picks**: Instant barcode lookup with quick-pick chips for popular supermarket items (`P001` to `P005`).
- **Interactive Multi-Item Cart**: Real-time quantity adjustments (`+` / `-`), automatic stock availability enforcement, item removal, and subtotal recalculation.
- **Thermal Invoice / Receipt Printing**: Modal receipt generator displaying unique receipt IDs, timestamp, itemized breakdown, and totals.
- **Atomic Transaction Checkouts**: Atomically decrements stock, records sales transactions, updates general ledger balance, and writes to the audit log.

### 2. Executive Financial Audit & Intelligence
- **Real-Time KPI Metric Cards**: Current Balance, Net Profit / Loss, Total Money In, Total Money Out, Sales Revenue, and Inventory Valuation.
- **Interactive Visual Analytics**: Custom Compose Canvas chart plotting actual profit progression alongside statistical linear regression trend lines.
- **Machine Learning Profit Forecasting**: Statistical profit projection using linear regression, computing daily run-rate slope, 30-day, 90-day, and 365-day trajectories.
- **Automated Stock Refill Alerts**: Visual badges and alert banners for items with low stock ($\le 50$ units) and critical stock ($\le 10$ units).
- **Financial Ledger & Bookkeeping**: Instant recording of operational expense bills and revenue receipts with running balance updates.
- **Inventory Management**: Add products, update prices, delete products, and search by barcode or name.

### 3. Enterprise Security & Role-Based Access Control
- **Cryptographic SHA-256 Hashing**: Passwords stored and validated with SHA-256 hashing.
- **Role-Based Access Control (RBAC)**:
  - **Admin**: Full access to POS, Admin Intelligence Hub, User Management, and Finance.
  - **Cashier**: POS Cashier operations & barcode checkout only *(Admin Panel blocked with permission barrier)*.
- **User Account Management**: Administrators can create new accounts, assign roles (`cashier` / `admin`), change passwords, and delete users.

### 4. Database & CSV Synchronization
- **ACID-Compliant Room SQLite Engine**: High-performance local storage with reactive Kotlin Coroutines `Flow`.
- **CSV Data Export**: Built-in export function formatting inventory, sales, and balance history for external auditing.

---

## Default Login Credentials

| Username | Password | Role | Permissions |
| :--- | :--- | :--- | :--- |
| **`admin`** | `admin123` | **Administrator** | Full access to POS, Admin Intelligence Hub, User Management, and Finance |
| **`john`** | `password1` | **Cashier** | POS Cashier operations & barcode checkout only *(Admin Panel blocked)* |
| **`sarah`** | `securePass99` | **Cashier** | POS Cashier operations & barcode checkout only *(Admin Panel blocked)* |

---

## Android Architecture

```text
app/src/main/java/com/example/
├── MainActivity.kt                # Main activity entry point (Edge-to-Edge, ViewModel injection)
├── data/
│   ├── model/
│   │   └── Entities.kt            # Room entities (Product, Sale, Balance, AuditLog, User) & models
│   ├── dao/
│   │   └── Daos.kt                # Room DAOs (ProductDao, SaleDao, BalanceDao, AuditLogDao, UserDao)
│   ├── db/
│   │   └── AppDatabase.kt         # Room database with seed data
│   └── repository/
│       └── SupermarketRepository.kt # Central business logic, checkout transaction, linear regression
└── ui/
    ├── theme/
    │   ├── Color.kt               # Supermarket green & accent color palette
    │   └── Theme.kt               # Material 3 Compose theme
    ├── viewmodel/
    │   ├── AuthViewModel.kt       # Authentication, RBAC & user management state
    │   ├── PosViewModel.kt        # Cart, scanner, checkout & receipts state
    │   └── AdminViewModel.kt      # Analytics, financial KPIs & inventory state
    └── screens/
        ├── MainApp.kt             # TopAppBar, BottomNavigationBar (5 tabs)
        ├── PosScreen.kt           # Barcode scanner, cart & receipt dialog
        ├── InventoryScreen.kt     # Stock alerts, product catalog & price editing
        ├── FinanceScreen.kt       # Financial KPIs, bills/receipts & general ledger
        ├── AnalyticsScreen.kt     # Linear regression canvas chart & profit forecasting
        ├── AdminScreen.kt         # System diagnostics, RBAC user accounts & CSV sync
        └── LoginScreen.kt         # Sign-in & quick demo profile switchers
```
