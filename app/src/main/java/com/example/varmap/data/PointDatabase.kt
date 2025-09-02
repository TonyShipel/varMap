package com.example.varmap.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PointEntity::class], version = 2, exportSchema = false)
abstract class PointDatabase : RoomDatabase() {
    abstract fun pointDao(): PointDao

    companion object {
        @Volatile private var INSTANCE: PointDatabase? = null
        fun getDatabase(context: Context): PointDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PointDatabase::class.java,
                    "points.db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
