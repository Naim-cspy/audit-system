package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Safe migration for inventory (composite primary key store_id + product_id)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `inventory_new` (
                `product_id` TEXT NOT NULL,
                `product_name` TEXT NOT NULL,
                `product_price` REAL NOT NULL,
                `product_amount_left` INTEGER NOT NULL,
                `product_amount_sold` INTEGER NOT NULL,
                `date_sold` TEXT NOT NULL,
                `date_filled` TEXT NOT NULL,
                `product_type` TEXT NOT NULL,
                `store_id` TEXT NOT NULL,
                `sync_status` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`store_id`, `product_id`)
            )
            """.trimIndent()
        )
        db.execSQL("INSERT OR IGNORE INTO `inventory_new` SELECT product_id, product_name, product_price, product_amount_left, product_amount_sold, date_sold, date_filled, product_type, store_id, sync_status, created_at, updated_at FROM `inventory`")
        db.execSQL("DROP TABLE `inventory`")
        db.execSQL("ALTER TABLE `inventory_new` RENAME TO `inventory`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_store_id` ON `inventory` (`store_id`)")

        // 2. Safe migration for sales (composite primary key store_id + sale_id)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sales_new` (
                `sale_id` TEXT NOT NULL,
                `product_id` TEXT NOT NULL,
                `product_name` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `price` REAL NOT NULL,
                `sale_date` TEXT NOT NULL,
                `customer_id` TEXT NOT NULL,
                `cashier_id` TEXT NOT NULL,
                `store_id` TEXT NOT NULL,
                `sync_status` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`store_id`, `sale_id`)
            )
            """.trimIndent()
        )
        db.execSQL("INSERT OR IGNORE INTO `sales_new` SELECT sale_id, product_id, product_name, quantity, price, sale_date, customer_id, cashier_id, store_id, sync_status, created_at FROM `sales`")
        db.execSQL("DROP TABLE `sales`")
        db.execSQL("ALTER TABLE `sales_new` RENAME TO `sales`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_store_id` ON `sales` (`store_id`)")

        // 3. Create indices for tenant isolation on other tables
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_balance_history_store_id` ON `balance_history` (`store_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_store_id` ON `audit_logs` (`store_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_store_id_username` ON `users` (`store_id`, `username`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_users_store_id` ON `users` (`store_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_events_store_id` ON `sync_events` (`store_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_security_events_store_id` ON `security_events` (`store_id`)")
    }
}

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
    version = 4,
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
                    .addMigrations(MIGRATION_3_4)
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
            val defaultStore = "STR-LBN-NAB-001"

            // 1. Seed Inventory
            if (db.productDao().count(defaultStore) == 0) {
                val initialProducts = listOf(
                    ProductEntity("P001", "Wireless Mouse", 25.99, 4892, 304, "2026-09-21", "2026-05-15", "Electronics", store_id = defaultStore),
                    ProductEntity("P002", "Mechanical Keyboard", 79.99, 4895, 226, "2026-09-21", "2026-05-10", "Electronics", store_id = defaultStore),
                    ProductEntity("P003", "Standing Desk", 299.50, 4897, 133, "2026-09-17", "2026-04-20", "Furniture", store_id = defaultStore),
                    ProductEntity("P004", "Ergonomic Chair", 199.99, 4896, 169, "2026-09-17", "2026-05-01", "Furniture", store_id = defaultStore),
                    ProductEntity("P005", "USB-C Hub", 19.99, 4898, 312, "2026-09-17", "2026-05-25", "Electronics", store_id = defaultStore)
                )
                db.productDao().insertAll(initialProducts)
            }

            // 2. Seed Balance History
            if (db.balanceDao().count(defaultStore) == 0) {
                val balanceEntries = listOf(
                    BalanceEntity(date = "2026-06-01", budget_starting = 5000.0, money_in = 1500.0, money_out = 0.0, reason = "Monthly Base Capital", store_id = defaultStore),
                    BalanceEntity(date = "2026-06-02", budget_starting = 6500.0, money_in = 0.0, money_out = 45.0, reason = "Store Supplies", store_id = defaultStore),
                    BalanceEntity(date = "2026-06-03", budget_starting = 6455.0, money_in = 0.0, money_out = 120.5, reason = "Utilities Bill", store_id = defaultStore),
                    BalanceEntity(date = "2026-06-04", budget_starting = 6334.5, money_in = 250.0, money_out = 0.0, reason = "Special Order Deposit", store_id = defaultStore),
                    BalanceEntity(date = "2026-06-05", budget_starting = 6584.5, money_in = 0.0, money_out = 15.99, reason = "Receipt Paper & Labels", store_id = defaultStore),
                    BalanceEntity(date = "2026-09-17", budget_starting = 6568.51, money_in = 51.98, money_out = 0.0, reason = "POS Checkout (Wireless Mouse x2)", store_id = defaultStore),
                    BalanceEntity(date = "2026-09-17", budget_starting = 6620.49, money_in = 0.0, money_out = 50.0, reason = "Cleaning Services", store_id = defaultStore),
                    BalanceEntity(date = "2026-09-17", budget_starting = 6570.49, money_in = 100.0, money_out = 0.0, reason = "Register Float Deposit", store_id = defaultStore),
                    BalanceEntity(date = "2026-09-21", budget_starting = 6670.49, money_in = 105.98, money_out = 0.0, reason = "POS Checkout (Mechanical Keyboard + Mouse)", store_id = defaultStore)
                )
                db.balanceDao().insertAll(balanceEntries)
            }

            // 3. Seed initial Sales
            if (db.saleDao().count(defaultStore) == 0) {
                val initialSales = listOf(
                    SaleEntity("S101", "P001", "Wireless Mouse", 2, 25.99, "2026-09-17", "C101", store_id = defaultStore),
                    SaleEntity("S102", "P002", "Mechanical Keyboard", 1, 79.99, "2026-09-21", "C102", store_id = defaultStore),
                    SaleEntity("S103", "P005", "USB-C Hub", 1, 19.99, "2026-09-21", "C103", store_id = defaultStore)
                )
                db.saleDao().insertAll(initialSales)
            }

            // 4. Initial Audit Log
            db.auditLogDao().insert(
                AuditLogEntity(
                    action = "SYSTEM_INIT",
                    details = "Supermarket POS & Audit Database initialized successfully with tenant isolation",
                    store_id = defaultStore
                )
            )

            // 5. Initial Cloud Sync State Event
            db.syncEventDao().insert(
                SyncEventEntity(
                    sync_id = "SYNC-INIT-001",
                    store_id = defaultStore,
                    device_id = "TERM-NAB-01",
                    user_id = "system",
                    operation = "INITIAL_REPLICATION",
                    entity_type = "STORE_SETTINGS",
                    entity_id = defaultStore,
                    status = "SUCCESS"
                )
            )
        }
    }
}
