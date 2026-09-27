package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.PendingSyncDeletionEntity

@Dao
interface PendingSyncDeletionDao {

    @Query("SELECT * FROM pending_sync_deletions WHERE store_id = :storeId ORDER BY timestamp ASC")
    fun getPendingDeletions(storeId: String): List<PendingSyncDeletionEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM pending_sync_deletions WHERE store_id = :storeId AND entity_type = :entityType AND entity_id = :entityId)")
    fun isPendingDeletion(storeId: String, entityType: String, entityId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(deletion: PendingSyncDeletionEntity): Long

    @Delete
    fun delete(deletion: PendingSyncDeletionEntity): Int

    @Query("DELETE FROM pending_sync_deletions WHERE store_id = :storeId AND entity_type = :entityType AND entity_id = :entityId")
    fun deleteById(storeId: String, entityType: String, entityId: String): Int

    @Query("DELETE FROM pending_sync_deletions WHERE store_id = :storeId")
    fun clearForStore(storeId: String): Int
}
