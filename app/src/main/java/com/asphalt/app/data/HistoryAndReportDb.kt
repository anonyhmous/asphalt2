package com.asphalt.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trip_history")
data class TripHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val app: String,
    val fare: Double,
    val netProfit: Double,
    val verdict: String,
    val pickupKm: Double,
    val tripKm: Double,
    val reasonsSummary: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface TripHistoryDao {
    @Query("SELECT * FROM trip_history ORDER BY timestamp DESC")
    fun getAllTrips(): Flow<List<TripHistoryEntity>>

    @Query("SELECT COUNT(*) FROM trip_history WHERE timestamp >= :sinceTimestamp")
    suspend fun countTripsSince(sinceTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM trip_history WHERE timestamp >= :sinceTimestamp AND verdict = 'ACCEPT'")
    suspend fun countAcceptedTripsSince(sinceTimestamp: Long): Int

    @Query("SELECT SUM(netProfit) FROM trip_history WHERE timestamp >= :sinceTimestamp AND verdict = 'ACCEPT'")
    suspend fun sumNetProfitSince(sinceTimestamp: Long): Double?

    // Real running costs (fuel + maintenance + commission) of trips the driver was told to reject
    @Query("SELECT SUM(fare - netProfit) FROM trip_history WHERE timestamp >= :sinceTimestamp AND verdict = 'REJECT'")
    suspend fun sumAvoidedCostSince(sinceTimestamp: Long): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripHistoryEntity): Long

    @Query("DELETE FROM trip_history")
    suspend fun clearHistory()
}

@Entity(tableName = "problem_reports")
data class ProblemReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val description: String,
    val attachedMetrics: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Dao
interface ProblemReportDao {
    @Query("SELECT * FROM problem_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<ProblemReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ProblemReportEntity): Long

    @Query("UPDATE problem_reports SET isSynced = 1 WHERE id = :id")
    suspend fun markReportSynced(id: Long)
}
