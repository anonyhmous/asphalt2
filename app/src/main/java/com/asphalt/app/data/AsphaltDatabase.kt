package com.asphalt.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ZoneEntity::class,
        TripHistoryEntity::class,
        ProblemReportEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AsphaltDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao
    abstract fun tripHistoryDao(): TripHistoryDao
    abstract fun problemReportDao(): ProblemReportDao

    companion object {
        @Volatile
        private var INSTANCE: AsphaltDatabase? = null

        fun getInstance(context: Context): AsphaltDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AsphaltDatabase::class.java,
                    "asphalt_captain_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
