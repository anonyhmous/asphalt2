package com.asphalt.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asphalt.app.data.AsphaltRepository
import com.asphalt.app.model.*
import com.asphalt.app.ui.theme.ThemeState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository: AsphaltRepository = AsphaltRepository.getInstance(application)

    val isReaderActive: StateFlow<Boolean> = repository.preferences.isReaderActiveFlow
    val driverProfile: StateFlow<DriverProfile> = repository.preferences.profileFlow
    val driverRules: StateFlow<DriverRules> = repository.preferences.rulesFlow
    val themeMode: StateFlow<Int> = repository.preferences.themeModeFlow
    val todaySummary: StateFlow<ShiftSummary> = repository.todaySummary
    val latestJudgment: StateFlow<JudgmentResult?> = repository.latestJudgment
    val isOverlayVisible: StateFlow<Boolean> = repository.isOverlayVisible
    val hasCompletedOnboarding: StateFlow<Boolean> = repository.preferences.hasCompletedOnboarding
    val subscription: StateFlow<DriverSubscription> = repository.preferences.subscriptionFlow

    // محرك العداد التنازلي الدقيق للـ 24 ساعة (تحديث كل ثانية)
    private val _sessionCountdown = MutableStateFlow("00:00:00")
    val sessionCountdown: StateFlow<String> = _sessionCountdown.asStateFlow()

    private val _sessionProgress = MutableStateFlow(0f)
    val sessionProgress: StateFlow<Float> = _sessionProgress.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    // إشعار أو حوار عند محاولة التشغيل بدون رصيد أيام كافٍ
    private val _showInsufficientDaysDialog = MutableStateFlow(false)
    val showInsufficientDaysDialog: StateFlow<Boolean> = _showInsufficientDaysDialog.asStateFlow()

    // رسالة تنبيه منبثقة أو حالة آخر تفعيل
    private val _activationBannerMessage = MutableStateFlow<String?>(null)
    val activationBannerMessage: StateFlow<String?> = _activationBannerMessage.asStateFlow()

    // Firebase Auth State & UID
    private val _currentUserId = MutableStateFlow(Firebase.auth.currentUser?.uid)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    // Admin rights come from the server (admins/{uid} document); never from a local flag.
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    // Subscribed ONLY for admins (Firestore rules reject everybody else).
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allDriversState: StateFlow<List<DriverFirebaseDoc>> = _isAdmin
        .flatMapLatest { admin -> if (admin) repository.firebaseSyncRepository.observeAllDrivers() else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allRidesState: StateFlow<List<RideFirebaseDoc>> = _isAdmin
        .flatMapLatest { admin -> if (admin) repository.firebaseSyncRepository.observeAllRides(50) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferredZones: StateFlow<List<ZoneItem>> = repository.preferredZones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val avoidedZones: StateFlow<List<ZoneItem>> = repository.avoidedZones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allZones: StateFlow<List<ZoneItem>> = repository.allZones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _showSimulatorDialog = MutableStateFlow(false)
    val showSimulatorDialog: StateFlow<Boolean> = _showSimulatorDialog.asStateFlow()

    // Must be declared BEFORE the init block (property initialisers run in textual order).
    private var cloudJob: kotlinx.coroutines.Job? = null
    private var cloudUid: String? = null

    init {
        // تشغيل محرك العداد التنازلي كل ثانية لحساب الوقت المتبقي في الـ 24 ساعة
        startSessionCountdownEngine()
        initCloudSync()
    }

    fun onSignedOut() {
        cloudJob?.cancel(); cloudJob = null; cloudUid = null
        _isAdmin.value = false
        _currentUserId.value = null
        repository.preferences.resetSubscription()
    }

    private fun initCloudSync() {
        val user = Firebase.auth.currentUser
        if (user != null) onUserAuthenticated(user.uid)
    }

    /** Called after Google sign-in AND again after the phone has been verified/linked. */
    fun onUserAuthenticated(uid: String) {
        _currentUserId.value = uid
        // Without a verified phone the Firestore rules refuse everything: wait for PhoneVerifyScreen.
        if (Firebase.auth.currentUser?.phoneNumber.isNullOrBlank()) return
        if (cloudJob?.isActive == true && cloudUid == uid) return
        cloudJob?.cancel()
        cloudUid = uid
        // Local cache belongs to ONE account: another Google account never inherits days/session.
        repository.preferences.bindToUser(uid)
        cloudJob = viewModelScope.launch {
            _isAdmin.value = repository.firebaseSyncRepository.isAdmin(uid)
            registerProfile(driverProfile.value)
            launch { keepServerClockSynced(uid) }
            repository.firebaseSyncRepository.observeDriver(uid).collect { cloudDoc ->
                if (cloudDoc != null) {
                    repository.preferences.syncSubscriptionFromCloud(
                        remainingDays = cloudDoc.remainingDays,
                        activeSessionStartMs = cloudDoc.activeSessionStartTime?.toDate()?.time,
                        currentPlan = cloudDoc.currentPlan
                    )
                }
            }
        }
    }

    /** Re-anchors the trusted (server) clock at start and every 10 minutes while online. */
    private suspend fun keepServerClockSynced(uid: String) {
        while (true) {
            repository.firebaseSyncRepository.fetchServerTimeMs(uid)?.let { com.asphalt.app.data.TrustedClock.anchor(it) }
            delay(10 * 60_000L)
        }
    }

    private fun getDeviceId(): String = runCatching {
        android.provider.Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }.getOrNull().orEmpty()

    /** One implementation of the profile -> cloud mapping (was copy-pasted 3 times). */
    private suspend fun registerProfile(profile: DriverProfile) {
        val rules = driverRules.value
        val cc = when (rules.engineSize) {
            EngineSize.MOTO_200 -> 200
            EngineSize.MOTO_250 -> 250
            else -> 150
        }
        val result = repository.firebaseSyncRepository.registerOrUpdateProfile(
            name = profile.name,
            governorate = profile.governorate,
            engineCc = cc,
            vehicleType = if (rules.engineSize.isMotorcycle) "MOTORCYCLE" else "CAR",
            deviceId = getDeviceId()
        )
        if (result is com.asphalt.app.data.AsphaltFirebaseSyncRepository.RegisterResult.Failed) {
            _activationBannerMessage.value = result.message
        }
    }

    private fun startSessionCountdownEngine() {
        viewModelScope.launch {
            while (isActive) {
                val now = com.asphalt.app.data.TrustedClock.now()
                val currentSub = repository.preferences.subscriptionFlow.value

                if (currentSub.isSessionActive(now)) {
                    _isSessionActive.value = true
                    _sessionCountdown.value = currentSub.formatSessionCountdown(now)
                    _sessionProgress.value = currentSub.sessionProgress(now)
                } else {
                    _isSessionActive.value = false
                    _sessionCountdown.value = "00:00:00"
                    _sessionProgress.value = 0f

                    // لو كانت الحصة نشطة وانتهت الآن
                    if (currentSub.activeSessionEndTime != null && now >= currentSub.activeSessionEndTime) {
                        repository.preferences.checkAndExpireActiveSession(now)
                        _activationBannerMessage.value = "انتهت حصة الـ 24 ساعة السابقة. يمكنك بدء حصة جديدة عند استعدادك للعمل."

                        val uid = _currentUserId.value ?: Firebase.auth.currentUser?.uid
                        if (uid != null) {
                            repository.firebaseSyncRepository.setReaderState(uid, readerActive = false, sessionActive = false)
                        }
                    }
                }
                delay(1000L)
            }
        }
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setShowSimulator(show: Boolean) {
        _showSimulatorDialog.value = show
    }

    /**
     * تشغيل أو إيقاف قارئ الطلبات وفق نظام الـ 24 ساعة الحصري:
     * - خصم يوم واحد فقط عند بدء حصة جديدة
     * - عدم خصم أي يوم طالما الـ 24 ساعة جارية
     * - منع التشغيل عند انتهاء رصيد الأيام
     * - مزامنة فورية مع قاعدة بيانات Firebase Cloud
     */
    fun toggleReader(active: Boolean) {
        viewModelScope.launch {
            val result = if (active) repository.startReader() else {
                repository.stopReader()
                SessionActivationResult.Paused
            }
            when (result) {
                is SessionActivationResult.NewSessionStarted -> {
                    _showInsufficientDaysDialog.value = false
                    _activationBannerMessage.value = "تم بدء حصة 24 ساعة عمل جديدة بنجاح! متبقي في رصيدك ${result.remainingDays} يوم."
                }
                is SessionActivationResult.ExistingSessionContinued -> {
                    _showInsufficientDaysDialog.value = false
                    _activationBannerMessage.value = "تم تشغيل القارئ ضمن حصة الـ 24 ساعة الحالية (بدون أي خصم من رصيدك)."
                }
                is SessionActivationResult.Paused -> {
                    _activationBannerMessage.value = "تم إيقاف القارئ مؤقتاً. حصة الـ 24 ساعة مستمرة ولا تُخصم أي أيام أخرى."
                }
                is SessionActivationResult.InsufficientDays -> {
                    _showInsufficientDaysDialog.value = true
                    _activationBannerMessage.value = result.messageArabic
                }
                is SessionActivationResult.NeedsServerStart -> Unit // handled inside startReader()
            }
        }
    }

    fun setReaderActive(active: Boolean) {
        toggleReader(active)
    }

    fun dismissInsufficientDaysDialog() {
        _showInsufficientDaysDialog.value = false
    }

    fun clearActivationBanner() {
        _activationBannerMessage.value = null
    }

    fun completeOnboarding(profile: DriverProfile) {
        repository.preferences.updateProfile(profile)
        repository.preferences.setCompletedOnboarding(true)
        viewModelScope.launch { registerProfile(profile) }
    }

    fun updateRules(rules: DriverRules) {
        repository.preferences.updateRules(rules)
    }

    // NOTE: there is intentionally NO client-side "activate subscription / add days" any more.
    // Days are credited only by an admin (Firestore rules) after the InstaPay receipt is verified.

    fun adminAddDays(driverId: String, days: Int, amountPaid: Double = 0.0) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                repository.firebaseSyncRepository.adminAddSubscriptionDays(driverId, days, amountPaid)
            } catch (e: Exception) {
                _activationBannerMessage.value = "تعذر شحن الأيام: ${e.localizedMessage ?: "خطأ غير معروف"}"
            }
        }
    }

    fun updateProfile(profile: DriverProfile) {
        repository.preferences.updateProfile(profile)
        viewModelScope.launch { registerProfile(profile) }
    }

    fun toggleGoingHomeMode() {
        val current = driverRules.value
        val updated = current.copy(isGoingHomeMode = !current.isGoingHomeMode, isGoingDestMode = false)
        repository.preferences.updateRules(updated)
    }

    fun toggleGoingDestMode() {
        val current = driverRules.value
        val updated = current.copy(isGoingDestMode = !current.isGoingDestMode, isGoingHomeMode = false)
        repository.preferences.updateRules(updated)
    }

    fun addZone(name: String, governorate: String, type: ZoneType, notes: String) {
        viewModelScope.launch {
            repository.addZone(name, governorate, type, notes)
        }
    }

    fun deleteZone(zone: ZoneItem) {
        viewModelScope.launch {
            repository.deleteZone(zone)
        }
    }

    /** تحويل نهاري <-> ليلي: بيثبّت الاختيار ويفضل محفوظ. */
    fun toggleTheme() {
        val newMode = if (ThemeState.isDark) 1 else 2
        ThemeState.mode = newMode
        repository.preferences.setThemeMode(newMode)
    }

    /** بيرجّع آخر نص اتقرا من الشاشة (للتشخيص). */
    fun lastCaptureText(): String = repository.lastCapture

    fun dismissOverlay() {
        repository.dismissOverlay(byUser = true)
    }

    /**
     * Interactive Live Simulator:
     * Injects a mock ride offer card into the judgment engine so driver can test
     * Uber, DiDi, inDrive cards, calculation rules, and floating overlay in real time.
     */
    fun simulateCard(
        app: SupportedApp,
        fare: Double,
        pickupKm: Double,
        tripKm: Double,
        passengerRating: Double = 4.88,
        passengerTrips: Int = 42,
        orderAgeSec: Int = 12,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        stopsCount: Int = 1,
        pickupAddr: String = "المهندسين - شارع جامعة الدول",
        dropoffAddr: String = "التجمع الخامس - شارع التسعين",
        note: String? = null
    ) {
        val order = OrderDetails(
            app = app,
            rawText = "طلب $app - $fare ج.م - استلام $pickupKm كم - رحلة $tripKm كم",
            fare = fare,
            pickupDistanceKm = pickupKm,
            tripDistanceKm = tripKm,
            passengerRating = passengerRating,
            passengerTripsCount = passengerTrips,
            orderAgeSeconds = orderAgeSec,
            paymentMethod = paymentMethod,
            stopsCount = stopsCount,
            pickupAddress = pickupAddr,
            dropoffAddress = dropoffAddr,
            passengerNote = note
        )
        repository.processOrder(order, record = false)
    }
}
