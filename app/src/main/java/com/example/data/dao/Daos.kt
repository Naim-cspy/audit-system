package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AuditLogEntity
import com.example.data.model.BalanceEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SaleEntity
import com.example.data.model.SecurityEventEntity
import com.example.data.model.SyncEventEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM inventory WHERE store_id = :storeId ORDER BY product_name ASC")
    fun getAllProducts(storeId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM inventory WHERE store_id = :storeId AND (UPPER(product_id) = UPPER(:id) OR UPPER(product_name) = UPPER(:id)) LIMIT 1")
    fun getProductById(storeId: String, id: String): ProductEntity?

    @Query("SELECT * FROM inventory WHERE store_id = :storeId AND (LOWER(product_name) LIKE '%' || LOWER(:query) || '%' OR UPPER(product_id) LIKE '%' || UPPER(:query) || '%') ORDER BY product_name ASC")
    fun searchProducts(storeId: String, query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM inventory WHERE store_id = :storeId AND product_amount_left <= :threshold ORDER BY product_amount_left ASC")
    fun getStockWarnings(storeId: String, threshold: Int): Flow<List<ProductEntity>>

    @Query("SELECT * FROM inventory WHERE store_id = :storeId AND sync_status = 'PENDING'")
    fun getPendingProducts(storeId: String): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(products: List<ProductEntity>): List<Long>

    @Update
    fun update(product: ProductEntity): Int

    @Query("UPDATE inventory SET product_price = :newPrice, updated_at = :updatedAt WHERE store_id = :storeId AND UPPER(product_id) = UPPER(:productId)")
    fun updatePrice(storeId: String, productId: String, newPrice: Double, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM inventory WHERE store_id = :storeId AND UPPER(product_id) = UPPER(:productId)")
    fun deleteById(storeId: String, productId: String): Int

    @Delete
    fun delete(product: ProductEntity): Int

    @Query("SELECT COUNT(*) FROM inventory WHERE store_id = :storeId")
    fun count(storeId: String): Int

    @Query("SELECT COUNT(*) FROM inventory")
    fun totalCount(): Int
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales WHERE store_id = :storeId ORDER BY created_at DESC LIMIT :limit")
    fun getRecentSales(storeId: String, limit: Int = 10): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE store_id = :storeId ORDER BY created_at DESC")
    fun getAllSales(storeId: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE store_id = :storeId AND sync_status = 'PENDING'")
    fun getPendingSales(storeId: String): List<SaleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(sale: SaleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(sales: List<SaleEntity>): List<Long>

    @Update
    fun update(sale: SaleEntity): Int

    @Query("SELECT COUNT(*) FROM sales WHERE store_id = :storeId")
    fun count(storeId: String): Int
}

@Dao
interface BalanceDao {
    @Query("SELECT * FROM balance_history WHERE store_id = :storeId ORDER BY id ASC")
    fun getAllHistory(storeId: String): Flow<List<BalanceEntity>>

    @Query("SELECT * FROM balance_history WHERE store_id = :storeId ORDER BY id DESC")
    fun getAllHistoryDesc(storeId: String): Flow<List<BalanceEntity>>

    @Query("SELECT * FROM balance_history WHERE store_id = :storeId ORDER BY id DESC LIMIT 1")
    fun getLatestBalance(storeId: String): BalanceEntity?

    @Query("SELECT * FROM balance_history WHERE store_id = :storeId AND sync_status = 'PENDING'")
    fun getPendingLedger(storeId: String): List<BalanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(balance: BalanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(balances: List<BalanceEntity>): List<Long>

    @Update
    fun update(balance: BalanceEntity): Int

    @Query("SELECT COUNT(*) FROM balance_history WHERE store_id = :storeId")
    fun count(storeId: String): Int
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs WHERE store_id = :storeId ORDER BY id DESC LIMIT :limit")
    fun getRecentLogs(storeId: String, limit: Int = 50): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE store_id = :storeId ORDER BY id DESC")
    fun getAllLogs(storeId: String): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(log: AuditLogEntity): Long
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE store_id = :storeId ORDER BY id ASC")
    fun getAllUsers(storeId: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE store_id = :storeId AND LOWER(username) = LOWER(:username) LIMIT 1")
    fun getByUsername(storeId: String, username: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username)")
    fun findUsersByUsername(username: String): List<UserEntity>

    @Query("SELECT COUNT(*) FROM users")
    fun totalCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(user: UserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(users: List<UserEntity>): List<Long>

    @Query("DELETE FROM users WHERE store_id = :storeId AND LOWER(username) = LOWER(:username)")
    fun deleteByUsername(storeId: String, username: String): Int

    @Query("UPDATE users SET password_hash = :newHash, salt = :newSalt WHERE store_id = :storeId AND LOWER(username) = LOWER(:username)")
    fun updatePassword(storeId: String, username: String, newHash: String, newSalt: String): Int

    @Query("SELECT COUNT(*) FROM users WHERE store_id = :storeId")
    fun count(storeId: String): Int
}

@Dao
interface SyncEventDao {
    @Query("SELECT * FROM sync_events WHERE store_id = :storeId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSyncEvents(storeId: String, limit: Int = 40): Flow<List<SyncEventEntity>>

    @Query("SELECT * FROM sync_events WHERE store_id = :storeId AND status = :status ORDER BY timestamp DESC LIMIT :limit")
    fun getByStatus(storeId: String, status: String, limit: Int = 40): Flow<List<SyncEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(event: SyncEventEntity): Long

    @Query("SELECT COUNT(*) FROM sync_events WHERE store_id = :storeId AND (status = 'FAILED' OR status = 'REJECTED')")
    fun getFailedCount(storeId: String): Flow<Int>
}

@Dao
interface SecurityEventDao {
    @Query("SELECT * FROM security_events WHERE store_id = :storeId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSecurityEvents(storeId: String, limit: Int = 40): Flow<List<SecurityEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(event: SecurityEventEntity): Long
}
