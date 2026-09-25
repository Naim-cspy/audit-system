package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AuditLogDao
import com.example.data.dao.BalanceDao
import com.example.data.dao.ProductDao
import com.example.data.dao.SaleDao
import com.example.data.dao.SecurityEventDao
import com.example.data.dao.SyncEventDao
import com.example.data.dao.UserDao
import com.example.data.model.AuditLogEntity
import com.example.data.model.BalanceEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SecurityEventEntity
import com.example.data.model.SyncEventEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ProductEntity::class,
        SaleEntity::class,
        BalanceEntity::class,
        AuditLogEntity::class,
        UserEntity::class,
        SyncEventEntity::class,
        SecurityEventEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun balanceDao(): BalanceDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun userDao(): UserDao
    abstract fun syncEventDao(): SyncEventDao
    abstract fun securityEventDao(): SecurityEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "supermarket_audit.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        seedDatabase(database)
                    }
                }
            }
        }

        suspend fun seedDatabase(db: AppDatabase) {
            // Note: In Phase 1 hardcoded demo credentials are permanently removed.
            // User accounts are now created securely with PBKDF2 cryptographic hashing.

            // 1. Seed Inventory
            if (db.productDao().count() == 0) {
                val initialProducts = listOf(
                    ProductEntity("P001", "Wireless Mouse", 25.99, 4892, 304, "2026-09-21", "2026-05-15", "Electronics"),
                    ProductEntity("P002", "Mechanical Keyboard", 79.99, 4895, 226, "2026-09-21", "2026-05-10", "Electronics"),
                    ProductEntity("P003", "Standing Desk", 299.50, 4897, 133, "2026-09-17", "2026-04-20", "Furniture"),
                    ProductEntity("P004", "Ergonomic Chair", 199.99, 4896, 169, "2026-09-17", "2026-05-01", "Furniture"),
                    ProductEntity("P005", "USB-C Hub", 19.99, 4898, 312, "2026-09-17", "2026-05-25", "Electronics")
                )
                db.productDao().insertAll(initialProducts)
            }

            // 3. Seed Balance History
            if (db.balanceDao().count() == 0) {
                val balanceEntries = listOf(
                    BalanceEntity(date = "2026-06-01", budget_starting = 5000.0, money_in = 1500.0, money_out = 0.0, reason = "Monthly Base Capital"),
                    BalanceEntity(date = "2026-06-02", budget_starting = 6500.0, money_in = 0.0, money_out = 45.0, reason = "Store Supplies"),
                    BalanceEntity(date = "2026-06-03", budget_starting = 6455.0, money_in = 0.0, money_out = 120.5, reason = "Utilities Bill"),
                    BalanceEntity(date = "2026-06-04", budget_starting = 6334.5, money_in = 250.0, money_out = 0.0, reason = "Special Order Deposit"),
                    BalanceEntity(date = "2026-06-05", budget_starting = 6584.5, money_in = 0.0, money_out = 15.99, reason = "Receipt Paper & Labels"),
                    BalanceEntity(date = "2026-09-17", budget_starting = 6568.51, money_in = 51.98, money_out = 0.0, reason = "POS Checkout (Wireless Mouse x2)"),
                    BalanceEntity(date = "2026-09-17", budget_starting = 6620.49, money_in = 0.0, money_out = 50.0, reason = "Cleaning Services"),
                    BalanceEntity(date = "2026-09-17", budget_starting = 6570.49, money_in = 100.0, money_out = 0.0, reason = "Register Float Deposit"),
                    BalanceEntity(date = "2026-09-21", budget_starting = 6670.49, money_in = 105.98, money_out = 0.0, reason = "POS Checkout (Mechanical Keyboard + Mouse)")
                )
                db.balanceDao().insertAll(balanceEntries)
            }

            // 4. Seed initial Sales
            if (db.saleDao().count() == 0) {
                val initialSales = listOf(
                    SaleEntity("S101", "P001", "Wireless Mouse", 2, 25.99, "2026-09-17", "C101"),
                    SaleEntity("S102", "P002", "Mechanical Keyboard", 1, 79.99, "2026-09-21", "C102"),
                    SaleEntity("S103", "P005", "USB-C Hub", 1, 19.99, "2026-09-21", "C103")
                )
                db.saleDao().insertAll(initialSales)
            }

            // 5. Initial Audit Log
            db.auditLogDao().insert(
                AuditLogEntity(
                    action = "SYSTEM_INIT",
                    details = "Supermarket POS & Audit Database initialized successfully"
                )
            )

            // 6. Initial Cloud Sync State Event
            db.syncEventDao().insert(
                SyncEventEntity(
                    sync_id = "SYNC-INIT-001",
                    store_id = "STR-LBN-NAB-001",
                    device_id = "TERM-NAB-01",
                    user_id = "system",
                    operation = "INITIAL_REPLICATION",
                    entity_type = "STORE_SETTINGS",
                    entity_id = "STR-LBN-NAB-001",
                    status = "SUCCESS"
                )
            )
        }
    }
}
