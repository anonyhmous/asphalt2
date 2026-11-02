package com.asphalt.app.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class DriverFirebaseDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val driverName: String = "",
    val phone: String = "",
    val email: String = "",
    val deviceId: String = "",
    val trialClaimed: Boolean = false,
    val governorate: String = "القاهرة",
    val engineCc: Int = 150,
    val vehicleType: String = "MOTORCYCLE",
    val remainingDays: Int = 0,
    val activeSessionStartTime: Timestamp? = null,
    val activeSessionEndTime: Timestamp? = null,
    val isSessionActive: Boolean = false,
    val isReaderActive: Boolean = false,
    val currentPlan: String = "FREE_TRIAL",
    val totalSessionsStarted: Int = 0,
    val totalRidesScanned: Int = 0,
    val totalNetProfit: Double = 0.0,
    val role: String = "driver",
    @ServerTimestamp val lastActiveTimestamp: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RideFirebaseDoc(
    @DocumentId val id: String = "",
    val driverId: String = "",
    val driverName: String = "",
    val app: String = "",
    val fare: Double = 0.0,
    val netProfit: Double = 0.0,
    val verdict: String = "",
    val pickupKm: Double = 0.0,
    val tripKm: Double = 0.0,
    val reasonsSummary: String = "",
    val pickupAddress: String = "",
    val dropoffAddress: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
