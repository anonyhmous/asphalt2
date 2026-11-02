package com.asphalt.app.data

import android.content.Context
import android.content.SharedPreferences
import com.asphalt.app.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AsphaltPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("asphalt_driver_prefs", Context.MODE_PRIVATE)

    private val _rulesFlow = MutableStateFlow(loadRules())
    val rulesFlow: StateFlow<DriverRules> = _rulesFlow.asStateFlow()

    private val _profileFlow = MutableStateFlow(loadProfile())
    val profileFlow: StateFlow<DriverProfile> = _profileFlow.asStateFlow()

    /** 0 = follow phone setting, 1 = light (day), 2 = dark (night) */
    private val _themeModeFlow = MutableStateFlow(prefs.getInt(KEY_THEME_MODE, 0))
    val themeModeFlow: StateFlow<Int> = _themeModeFlow.asStateFlow()

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
        _themeModeFlow.value = mode
    }

    private val _isReaderActiveFlow = MutableStateFlow(prefs.getBoolean(KEY_READER_ACTIVE, false))
    val isReaderActiveFlow: StateFlow<Boolean> = _isReaderActiveFlow.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false))
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private val _subscriptionFlow = MutableStateFlow(loadSubscription())
    val subscriptionFlow: StateFlow<DriverSubscription> = _subscriptionFlow.asStateFlow()

    fun setReaderActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_READER_ACTIVE, active).apply()
        _isReaderActiveFlow.value = active
    }

    fun setCompletedOnboarding(completed: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, completed).apply()
        _hasCompletedOnboarding.value = completed
    }

    /**
     * Pause / resume the reader inside an already running 24h session.
     * Starting a NEW session (which deducts a day) is decided by the server only:
     * this returns [SessionActivationResult.NeedsServerStart] and the caller must use
     * AsphaltRepository.startReader(). The day balance is never modified locally.
     */
    fun requestToggleReader(requestedActive: Boolean): SessionActivationResult {
        val now = TrustedClock.now()
        if (!requestedActive) {
            prefs.edit().putBoolean(KEY_READER_ACTIVE, false).apply()
            _isReaderActiveFlow.value = false
            return SessionActivationResult.Paused
        }
        val currentSub = _subscriptionFlow.value
        if (currentSub.isSessionActive(now)) {
            prefs.edit().putBoolean(KEY_READER_ACTIVE, true).apply()
            _isReaderActiveFlow.value = true
            return SessionActivationResult.ExistingSessionContinued(currentSub.getRemainingSessionMillis(now))
        }
        prefs.edit().putBoolean(KEY_READER_ACTIVE, false).apply()
        _isReaderActiveFlow.value = false
        return SessionActivationResult.NeedsServerStart
    }

    /** Called after the server confirmed a new session (transaction succeeded). */
    fun applyStartedSession(remainingDays: Int, startMs: Long) {
        val updated = _subscriptionFlow.value.copy(
            remainingDays = remainingDays,
            activeSessionStartTime = startMs,
            activeSessionEndTime = startMs + SESSION_DURATION_MS
        )
        prefs.edit().apply {
            putInt(KEY_SUB_REMAINING_DAYS, remainingDays)
            putLong(KEY_SUB_SESSION_START, startMs)
            putBoolean(KEY_READER_ACTIVE, true)
            apply()
        }
        _subscriptionFlow.value = updated
        _isReaderActiveFlow.value = true
    }

    /** Session over (24h elapsed on the trusted clock): stop the reader; no day is deducted. */
    fun checkAndExpireActiveSession(currentTime: Long = TrustedClock.now()): Boolean {
        val currentSub = _subscriptionFlow.value
        val end = currentSub.activeSessionEndTime
        if (end != null && currentTime >= end) {
            prefs.edit().apply {
                putLong(KEY_SUB_SESSION_START, 0L)
                putBoolean(KEY_READER_ACTIVE, false)
                apply()
            }
            _subscriptionFlow.value = currentSub.copy(activeSessionStartTime = null, activeSessionEndTime = null)
            _isReaderActiveFlow.value = false
            return true
        }
        return false
    }

    /**
     * The cloud document is the source of truth (admin top-ups, trial, session start).
     * The 24h end time is always DERIVED from the server start time and is never read from local storage.
     */
    fun syncSubscriptionFromCloud(
        remainingDays: Int,
        activeSessionStartMs: Long?,
        currentPlan: String
    ) {
        val start = activeSessionStartMs?.takeIf { it > 0L }
        val updated = _subscriptionFlow.value.copy(
            remainingDays = remainingDays,
            activeSessionStartTime = start,
            activeSessionEndTime = start?.plus(SESSION_DURATION_MS),
            isTrialPlan = currentPlan == "FREE_TRIAL_3_DAYS"
        )
        prefs.edit().apply {
            putInt(KEY_SUB_REMAINING_DAYS, remainingDays)
            putLong(KEY_SUB_SESSION_START, start ?: 0L)
            apply()
        }
        _subscriptionFlow.value = updated
        // Cloud says there is no running session (or it ended): the reader must not keep running.
        if (!updated.isSessionActive(TrustedClock.now()) && _isReaderActiveFlow.value) {
            prefs.edit().putBoolean(KEY_READER_ACTIVE, false).apply()
            _isReaderActiveFlow.value = false
        }
    }

    /** Wipes the locally cached balance/session when another account signs in or the user signs out. */
    fun bindToUser(uid: String) {
        val owner = prefs.getString(KEY_OWNER_UID, null)
        if (owner != uid) {
            resetSubscription()
            prefs.edit().putString(KEY_OWNER_UID, uid).apply()
        }
    }

    fun resetSubscription() {
        prefs.edit().apply {
            putInt(KEY_SUB_REMAINING_DAYS, 0)
            putLong(KEY_SUB_SESSION_START, 0L)
            putBoolean(KEY_READER_ACTIVE, false)
            apply()
        }
        _subscriptionFlow.value = DriverSubscription()
        _isReaderActiveFlow.value = false
    }

    fun updateRules(newRules: DriverRules) {
        prefs.edit().apply {
            putFloat(KEY_MIN_FARE, newRules.minFare.toFloat())
            putFloat(KEY_MIN_RATE_KM, newRules.minRatePerKm.toFloat())
            putString(KEY_DISTANCE_UNIT, newRules.distanceUnit.name)
            putFloat(KEY_MAX_PICKUP_KM, newRules.maxPickupDistanceKm.toFloat())
            putFloat(KEY_PICKUP_THRESHOLD_KM, newRules.pickupThresholdKm.toFloat())
            putString(KEY_CALC_RATE_TYPE, newRules.calcRateType.name)
            putString(KEY_DISTANCE_CALCULATION_MODE, newRules.distanceCalculationMode.name)
            putFloat(KEY_FUEL_PRICE, newRules.fuelPricePerLiter.toFloat())
            putString(KEY_ENGINE_SIZE, newRules.engineSize.name)
            putFloat(KEY_MAINTENANCE_KM, newRules.maintenanceCostPerKm.toFloat())
            putFloat(KEY_MIN_RATING, newRules.minPassengerRating.toFloat())
            putInt(KEY_MIN_TRIPS, newRules.minPassengerTrips)
            putInt(KEY_MAX_AGE_SEC, newRules.maxOrderAgeSeconds)
            putInt(KEY_MAX_STOPS, newRules.maxStopsCount)
            putString(KEY_FORBIDDEN_KEYWORDS, newRules.forbiddenKeywords.joinToString("|||"))
            putFloat(KEY_UBER_COMMISSION, newRules.uberCommissionPct.toFloat())
            putFloat(KEY_DIDI_COMMISSION, newRules.didiCommissionPct.toFloat())
            putFloat(KEY_INDRIVER_COMMISSION, newRules.indriveCommissionPct.toFloat())
            putBoolean(KEY_GOING_HOME_MODE, newRules.isGoingHomeMode)
            putString(KEY_HOME_ADDR, newRules.homeAddress)
            putBoolean(KEY_GOING_DEST_MODE, newRules.isGoingDestMode)
            putString(KEY_DEST_ADDR, newRules.destAddress)
            putFloat(KEY_OVERLAY_OPACITY, newRules.overlayOpacity)
            putFloat(KEY_OVERLAY_SCALE, newRules.overlayScale)
            putInt(KEY_AUTO_STOP_HOURS, newRules.autoStopHours)
            putBoolean(KEY_CHIME_SOUND_ONLY, newRules.chimeSoundOnly)
            apply()
        }
        _rulesFlow.value = newRules
    }

    fun updateProfile(profile: DriverProfile) {
        prefs.edit().apply {
            putString(KEY_FIRST_NAME, profile.firstName)
            putString(KEY_LAST_NAME, profile.lastName)
            putString(KEY_PHONE, profile.phoneWithCountryCode)
            putString(KEY_GOVERNORATE, profile.governorate)
            putString(KEY_COUNTRY, profile.country)
            putString(KEY_CURRENCY, profile.currency)
            apply()
        }
        _profileFlow.value = profile
    }

    private fun loadRules(): DriverRules {
        return DriverRules(
            minFare = prefs.getFloat(KEY_MIN_FARE, 35.0f).toDouble(),
            minRatePerKm = prefs.getFloat(KEY_MIN_RATE_KM, 8.0f).toDouble(),
            distanceUnit = DistanceUnit.valueOf(prefs.getString(KEY_DISTANCE_UNIT, DistanceUnit.KILOMETER.name) ?: DistanceUnit.KILOMETER.name),
            maxPickupDistanceKm = prefs.getFloat(KEY_MAX_PICKUP_KM, 3.5f).toDouble(),
            pickupThresholdKm = prefs.getFloat(KEY_PICKUP_THRESHOLD_KM, 1.5f).toDouble(),
            calcRateType = CalcRateType.valueOf(prefs.getString(KEY_CALC_RATE_TYPE, CalcRateType.NET.name) ?: CalcRateType.NET.name),
            distanceCalculationMode = runCatching { DistanceCalculationMode.valueOf(prefs.getString(KEY_DISTANCE_CALCULATION_MODE, DistanceCalculationMode.FULL_DISTANCE.name) ?: DistanceCalculationMode.FULL_DISTANCE.name) }.getOrDefault(DistanceCalculationMode.FULL_DISTANCE),
            fuelPricePerLiter = prefs.getFloat(KEY_FUEL_PRICE, 15.0f).toDouble(),
            engineSize = runCatching { EngineSize.valueOf(prefs.getString(KEY_ENGINE_SIZE, EngineSize.CC_1400_TO_1600.name) ?: EngineSize.CC_1400_TO_1600.name) }.getOrDefault(EngineSize.CC_1400_TO_1600),
            maintenanceCostPerKm = prefs.getFloat(KEY_MAINTENANCE_KM, 1.25f).toDouble(),
            minPassengerRating = prefs.getFloat(KEY_MIN_RATING, 4.60f).toDouble(),
            minPassengerTrips = prefs.getInt(KEY_MIN_TRIPS, 5),
            maxOrderAgeSeconds = prefs.getInt(KEY_MAX_AGE_SEC, 45),
            maxStopsCount = prefs.getInt(KEY_MAX_STOPS, 1),
            forbiddenKeywords = (prefs.getString(KEY_FORBIDDEN_KEYWORDS, null) ?: "سجاير|||دخان|||سلفني|||استنى شوية|||تأخير").split("|||"),
            uberCommissionPct = prefs.getFloat(KEY_UBER_COMMISSION, 25.0f).toDouble(),
            didiCommissionPct = prefs.getFloat(KEY_DIDI_COMMISSION, 22.0f).toDouble(),
            indriveCommissionPct = prefs.getFloat(KEY_INDRIVER_COMMISSION, 12.0f).toDouble(),
            isGoingHomeMode = prefs.getBoolean(KEY_GOING_HOME_MODE, false),
            homeAddress = prefs.getString(KEY_HOME_ADDR, "المعادي - القاهرة") ?: "المعادي - القاهرة",
            isGoingDestMode = prefs.getBoolean(KEY_GOING_DEST_MODE, false),
            destAddress = prefs.getString(KEY_DEST_ADDR, "التجمع الخامس - القاهرة الجديدة") ?: "التجمع الخامس - القاهرة الجديدة",
            overlayOpacity = prefs.getFloat(KEY_OVERLAY_OPACITY, 0.95f),
            overlayScale = prefs.getFloat(KEY_OVERLAY_SCALE, 1.0f),
            autoStopHours = prefs.getInt(KEY_AUTO_STOP_HOURS, 3),
            chimeSoundOnly = prefs.getBoolean(KEY_CHIME_SOUND_ONLY, false)
        )
    }

    private fun loadSubscription(): DriverSubscription {
        // No local trial any more: a fresh install starts at 0 until the server says otherwise.
        val remainingDays = prefs.getInt(KEY_SUB_REMAINING_DAYS, 0).coerceAtLeast(0)
        val sessionStart = prefs.getLong(KEY_SUB_SESSION_START, 0L).let { if (it > 0L) it else null }
        val sub = DriverSubscription(
            remainingDays = remainingDays,
            totalDaysPurchased = 0,
            activeSessionStartTime = sessionStart,
            activeSessionEndTime = sessionStart?.plus(SESSION_DURATION_MS) // derived, never trusted from disk
        )
        val end = sub.activeSessionEndTime
        return if (end != null && TrustedClock.now() >= end) sub.copy(activeSessionStartTime = null, activeSessionEndTime = null) else sub
    }

    private fun loadProfile(): DriverProfile {
        return DriverProfile(
            firstName = prefs.getString(KEY_FIRST_NAME, "كابتن") ?: "كابتن",
            lastName = prefs.getString(KEY_LAST_NAME, "محمد") ?: "محمد",
            phoneWithCountryCode = prefs.getString(KEY_PHONE, "+201012345678") ?: "+201012345678",
            governorate = prefs.getString(KEY_GOVERNORATE, "القاهرة") ?: "القاهرة",
            country = prefs.getString(KEY_COUNTRY, "مصر") ?: "مصر",
            currency = prefs.getString(KEY_CURRENCY, "ج.م") ?: "ج.م"
        )
    }

    companion object {
        private const val KEY_READER_ACTIVE = "key_reader_active"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "key_has_completed_onboarding"
        private const val KEY_MIN_FARE = "key_min_fare"
        private const val KEY_MIN_RATE_KM = "key_min_rate_km"
        private const val KEY_DISTANCE_UNIT = "key_distance_unit"
        private const val KEY_MAX_PICKUP_KM = "key_max_pickup_km"
        private const val KEY_PICKUP_THRESHOLD_KM = "key_pickup_threshold_km"
        private const val KEY_CALC_RATE_TYPE = "key_calc_rate_type"
        private const val KEY_DISTANCE_CALCULATION_MODE = "key_distance_calculation_mode"
        private const val KEY_FUEL_PRICE = "key_fuel_price"
        private const val KEY_ENGINE_SIZE = "key_engine_size"
        private const val KEY_MAINTENANCE_KM = "key_maintenance_km"
        private const val KEY_MIN_RATING = "key_min_rating"
        private const val KEY_MIN_TRIPS = "key_min_trips"
        private const val KEY_MAX_AGE_SEC = "key_max_age_sec"
        private const val KEY_MAX_STOPS = "key_max_stops"
        private const val KEY_FORBIDDEN_KEYWORDS = "key_forbidden_keywords"
        private const val KEY_UBER_COMMISSION = "key_uber_commission"
        private const val KEY_DIDI_COMMISSION = "key_didi_commission"
        private const val KEY_INDRIVER_COMMISSION = "key_indriver_commission"
        private const val KEY_GOING_HOME_MODE = "key_going_home_mode"
        private const val KEY_HOME_ADDR = "key_home_addr"
        private const val KEY_GOING_DEST_MODE = "key_going_dest_mode"
        private const val KEY_DEST_ADDR = "key_dest_addr"
        private const val KEY_OVERLAY_OPACITY = "key_overlay_opacity"
        private const val KEY_OVERLAY_SCALE = "key_overlay_scale"
        private const val KEY_AUTO_STOP_HOURS = "key_auto_stop_hours"
        private const val KEY_CHIME_SOUND_ONLY = "key_chime_sound_only"

        private const val KEY_FIRST_NAME = "key_first_name"
        private const val KEY_LAST_NAME = "key_last_name"
        private const val KEY_PHONE = "key_phone"
        private const val KEY_GOVERNORATE = "key_governorate"
        private const val KEY_COUNTRY = "key_country"
        private const val KEY_CURRENCY = "key_currency"
        private const val KEY_SUB_REMAINING_DAYS = "key_sub_remaining_days"
        private const val KEY_SUB_SESSION_START = "key_sub_session_start"

        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_OWNER_UID = "key_owner_uid"
    }
}
