package com.asphalt.app.data

import android.content.Context
import com.asphalt.app.R
import com.asphalt.app.model.*
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * All cloud access. The cloud (Firestore + firestore.rules) is the ONLY authority for:
 *   - the 3 free trial days (one per verified phone AND per device, claimed atomically),
 *   - the day balance, and
 *   - the start time of every 24h session (server timestamp).
 * The client merely requests these changes; firestore.rules validate them with request.time.
 */
class AsphaltFirebaseSyncRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id))
    )

    private val auth get() = Firebase.auth

    // ------------------------------------------------------------------ results
    sealed class RegisterResult {
        data class Ok(val trialGranted: Boolean) : RegisterResult()
        data class Failed(val message: String) : RegisterResult()
    }

    sealed class StartResult {
        data class Started(val remainingDays: Int, val startMs: Long) : StartResult()
        data object NoBalance : StartResult()
        data class Failed(val message: String) : StartResult()
    }

    // ------------------------------------------------------------------ reads
    fun observeDriver(driverId: String): Flow<DriverFirebaseDoc?> =
        db.collection("drivers").document(driverId).snapshots()
            .map { s -> if (s.exists() && !s.metadata.hasPendingWrites()) s.toObject(DriverFirebaseDoc::class.java) else null }
            .catch { e -> handleFirestoreError(e as? Exception ?: Exception(e), OperationType.GET, "drivers/$driverId") }

    suspend fun isAdmin(uid: String): Boolean = try {
        db.collection("admins").document(uid).get().await().exists()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

    /** Admin-only streams. Non-admins never subscribe (rules would reject and kill the flow). */
    fun observeAllDrivers(): Flow<List<DriverFirebaseDoc>> =
        db.collection("drivers").snapshots()
            .map { it.toObjects(DriverFirebaseDoc::class.java) }
            .catch { e -> handleFirestoreError(e as? Exception ?: Exception(e), OperationType.LIST, "drivers"); emit(emptyList()) }

    fun observeAllRides(limitCount: Long = 50): Flow<List<RideFirebaseDoc>> =
        db.collection("rides").orderBy("createdAt", Query.Direction.DESCENDING).limit(limitCount).snapshots()
            .map { it.toObjects(RideFirebaseDoc::class.java) }
            .catch { e -> handleFirestoreError(e as? Exception ?: Exception(e), OperationType.LIST, "rides"); emit(emptyList()) }

    // ------------------------------------------------------------ trusted time
    /**
     * Writes a server timestamp and reads it back from the server, so the app knows the real time
     * independent of the phone's clock. Returns null when offline.
     */
    suspend fun fetchServerTimeMs(uid: String): Long? = try {
        val ref = db.collection("drivers").document(uid)
        ref.update(mapOf("lastActiveTimestamp" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())).await()
        ref.get(Source.SERVER).await().getTimestamp("lastActiveTimestamp")?.toDate()?.time
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    // --------------------------------------------------------- registration
    /**
     * Creates the driver profile once (idempotent). Requires a verified phone linked to the Google
     * account. The trial is granted ONLY if neither this phone nor this device ever claimed one;
     * the claim documents and the driver document are written in one atomic batch which
     * firestore.rules cross-check (existsAfter / getAfter).
     * The local balance is deliberately NOT an input any more.
     */
    suspend fun registerOrUpdateProfile(
        name: String, governorate: String, engineCc: Int, vehicleType: String, deviceId: String
    ): RegisterResult {
        val user = auth.currentUser ?: return RegisterResult.Failed("غير مسجل الدخول")
        val phone = user.phoneNumber
        val email = user.email
        if (phone.isNullOrBlank() || email.isNullOrBlank()) return RegisterResult.Failed("يجب ربط رقم الهاتف وحساب Google أولاً")
        val uid = user.uid
        val driverRef = db.collection("drivers").document(uid)
        return try {
            val existing = driverRef.get().await()
            if (existing.exists()) {
                driverRef.update(
                    mapOf(
                        "driverName" to name.ifBlank { existing.getString("driverName") ?: "كابتن أسفلت" },
                        "governorate" to governorate,
                        "engineCc" to engineCc,
                        "vehicleType" to vehicleType,
                        "deviceId" to deviceId,
                        "lastActiveTimestamp" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                return RegisterResult.Ok(trialGranted = false)
            }

            val phoneKey = PhoneNumbers.normalize(phone)
            val devKey = "dev_$deviceId"
            val phoneClaimed = db.collection("claimed_trials").document(phoneKey).get().await().exists()
            val deviceClaimed = deviceId.isBlank() || db.collection("claimed_trials").document(devKey).get().await().exists()
            val grantTrial = !phoneClaimed && !deviceClaimed

            val driver = mapOf(
                "userId" to uid,
                "driverName" to name.ifBlank { "كابتن أسفلت" },
                "phone" to phone,
                "email" to email,
                "deviceId" to deviceId,
                "trialClaimed" to true,
                "governorate" to governorate,
                "engineCc" to engineCc,
                "vehicleType" to vehicleType,
                "remainingDays" to if (grantTrial) 3 else 0,
                "isSessionActive" to false,
                "isReaderActive" to false,
                "currentPlan" to if (grantTrial) "FREE_TRIAL_3_DAYS" else "EXPIRED_TRIAL",
                "totalSessionsStarted" to 0,
                "totalRidesScanned" to 0,
                "totalNetProfit" to 0.0,
                "role" to "driver",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastActiveTimestamp" to FieldValue.serverTimestamp()
            )
            val batch = db.batch()
            if (grantTrial) {
                val claim = mapOf("phone" to phoneKey, "userId" to uid, "deviceId" to deviceId, "claimedAt" to FieldValue.serverTimestamp())
                batch.set(db.collection("claimed_trials").document(phoneKey), claim)
                batch.set(db.collection("claimed_trials").document(devKey), claim)
            }
            batch.set(driverRef, driver)
            batch.commit().await()
            RegisterResult.Ok(trialGranted = grantTrial)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, driverRef.path)
            RegisterResult.Failed(e.localizedMessage ?: "تعذر إنشاء الحساب")
        }
    }

    // --------------------------------------------------------------- session
    /**
     * Starts a new 24h session: one transaction deducts exactly one day and stamps the SERVER time.
     * firestore.rules reject anything else (cannot start early, cannot deduct 0, cannot back-date).
     */
    suspend fun startSession(uid: String): StartResult {
        val ref = db.collection("drivers").document(uid)
        return try {
            val noBalance = db.runTransaction { tx ->
                val snap = tx.get(ref)
                val days = (snap.getLong("remainingDays") ?: 0L).toInt()
                if (days <= 0) return@runTransaction true
                val sessions = (snap.getLong("totalSessionsStarted") ?: 0L)
                tx.update(
                    ref,
                    mapOf(
                        "remainingDays" to days - 1,
                        "activeSessionStartTime" to FieldValue.serverTimestamp(),
                        "isSessionActive" to true,
                        "isReaderActive" to true,
                        "totalSessionsStarted" to sessions + 1,
                        "lastActiveTimestamp" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                false
            }.await()
            if (noBalance) return StartResult.NoBalance

            val fresh = ref.get(Source.SERVER).await()
            val startMs = fresh.getTimestamp("activeSessionStartTime")?.toDate()?.time
                ?: return StartResult.Failed("تعذر قراءة وقت بدء الحصة")
            fresh.getTimestamp("lastActiveTimestamp")?.toDate()?.time?.let { TrustedClock.anchor(it) }
            StartResult.Started((fresh.getLong("remainingDays") ?: 0L).toInt(), startMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            StartResult.Failed("تحتاج اتصالاً بالإنترنت لبدء حصة جديدة")
        }
    }

    /** Pause / resume / auto-stop inside a running session. Never touches the balance. */
    suspend fun setReaderState(uid: String, readerActive: Boolean, sessionActive: Boolean) {
        try {
            db.collection("drivers").document(uid).update(
                mapOf(
                    "isReaderActive" to readerActive,
                    "isSessionActive" to sessionActive,
                    "lastActiveTimestamp" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "drivers/$uid")
        }
    }

    // ----------------------------------------------------------------- rides
    suspend fun recordRide(
        driverId: String, driverName: String, app: String, fare: Double, netProfit: Double,
        verdict: String, pickupKm: Double, tripKm: Double, reasonsSummary: String,
        pickupAddress: String = "", dropoffAddress: String = ""
    ) {
        try {
            val batch = db.batch()
            batch.set(
                db.collection("rides").document(),
                mapOf(
                    "driverId" to driverId, "driverName" to driverName.take(100), "app" to app,
                    "fare" to fare, "netProfit" to netProfit, "verdict" to verdict,
                    "pickupKm" to pickupKm, "tripKm" to tripKm,
                    "reasonsSummary" to reasonsSummary.take(500),
                    "pickupAddress" to pickupAddress.take(300), "dropoffAddress" to dropoffAddress.take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("drivers").document(driverId),
                mapOf(
                    "totalRidesScanned" to FieldValue.increment(1),
                    "totalNetProfit" to FieldValue.increment(netProfit),
                    "lastActiveTimestamp" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "rides")
        }
    }

    // ----------------------------------------------------------------- admin
    /** Credits paid days after the admin verified the InstaPay receipt. Audit record written atomically. */
    suspend fun adminAddSubscriptionDays(driverId: String, daysToAdd: Int, amountPaid: Double = 0.0) {
        require(daysToAdd in 1..365) { "daysToAdd must be 1..365" }
        val ref = db.collection("drivers").document(driverId)
        try {
            val snap = ref.get().await()
            val name = snap.getString("driverName") ?: "كابتن"
            val after = ((snap.getLong("remainingDays") ?: 0L) + daysToAdd).toInt()
            val batch = db.batch()
            batch.update(ref, mapOf("remainingDays" to after, "currentPlan" to "PAID", "updatedAt" to FieldValue.serverTimestamp()))
            batch.set(
                db.collection("subscriptions").document(),
                mapOf(
                    "driverId" to driverId, "driverName" to name, "planType" to "ADMIN_ACTIVATION",
                    "daysAdded" to daysToAdd, "amountPaid" to amountPaid, "remainingDaysAfter" to after,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }
}
