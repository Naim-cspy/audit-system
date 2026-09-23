package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AuditLogEntity
import com.example.data.model.BalanceEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SaleEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM inventory ORDER BY product_name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM inventory WHERE UPPER(product_id) = UPPER(:id) OR UPPER(product_name) = UPPER(:id) LIMIT 1")
    fun getProductById(id: String): ProductEntity?

    @Query("SELECT * FROM inventory WHERE LOWER(product_name) LIKE '%' || LOWER(:query) || '%' OR UPPER(product_id) LIKE '%' || UPPER(:query) || '%' ORDER BY product_name ASC")
    fun searchProducts(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM inventory WHERE product_amount_left <= :threshold ORDER BY product_amount_left ASC")
    fun getStockWarnings(threshold: Int): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(products: List<ProductEntity>): List<Long>

    @Update
    fun update(product: ProductEntity): Int

    @Query("UPDATE inventory SET product_price = :newPrice, updated_at = :updatedAt WHERE UPPER(product_id) = UPPER(:productId)")
    fun updatePrice(productId: String, newPrice: Double, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM inventory WHERE UPPER(product_id) = UPPER(:productId)")
    fun deleteById(productId: String): Int

    @Query("SELECT COUNT(*) FROM inventory")
    fun count(): Int
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY created_at DESC LIMIT :limit")
    fun getRecentSales(limit: Int = 10): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales ORDER BY created_at DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(sale: SaleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(sales: List<SaleEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM sales")
    fun count(): Int
}

@Dao
interface BalanceDao {
    @Query("SELECT * FROM balance_history ORDER BY id ASC")
    fun getAllHistory(): Flow<List<BalanceEntity>>

    @Query("SELECT * FROM balance_history ORDER BY id DESC")
    fun getAllHistoryDesc(): Flow<List<BalanceEntity>>

    @Query("SELECT * FROM balance_history ORDER BY id DESC LIMIT 1")
    fun getLatest(): BalanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(balance: BalanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(balances: List<BalanceEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM balance_history")
    fun count(): Int
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY id DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(log: AuditLogEntity): Long
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY id ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    fun getByUsername(username: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(user: UserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(users: List<UserEntity>): List<Long>

    @Query("DELETE FROM users WHERE LOWER(username) = LOWER(:username)")
    fun deleteByUsername(username: String): Int

    @Query("UPDATE users SET password_hash = :newHash WHERE LOWER(username) = LOWER(:username)")
    fun updatePassword(username: String, newHash: String): Int

    @Query("SELECT COUNT(*) FROM users")
    fun count(): Int
}
