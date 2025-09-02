package com.example.varmap.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PointDao {
    @Query("SELECT * FROM points ORDER BY id ASC")
    fun getAllFlow(): Flow<List<PointEntity>>

    @Query("SELECT * FROM points ORDER BY id ASC")
    suspend fun getAll(): List<PointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: PointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<PointEntity>)

    @Query("DELETE FROM points")
    suspend fun clear()
}
