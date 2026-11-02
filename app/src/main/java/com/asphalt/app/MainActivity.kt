package com.asphalt.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.asphalt.app.service.AsphaltForegroundService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asphalt.app.model.DriverProfile
import com.asphalt.app.ui.MainViewModel
import com.asphalt.app.ui.components.FloatingJudgmentOverlayContent
import com.asphalt.app.ui.components.SimulatorDialog
import com.asphalt.app.ui.screens.*
import com.asphalt.app.ui.theme.AppPrimary
import com.asphalt.app.ui.theme.ThemeState
import com.asphalt.app.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.updateSystemDark(this)
        enableEdgeToEdge()

        // Android 13+: without this the persistent notification is silently hidden
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { }.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Activity is in the foreground here, so starting the foreground service is always allowed
        AsphaltForegroundService.start(this)

        setContent {
            MyApplicationTheme {
                // Ensure complete Arabic RTL layout
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

                    DisposableEffect(Unit) {
                        val listener = FirebaseAuth.AuthStateListener { auth ->
                            currentUser = auth.currentUser
                            if (auth.currentUser != null) {
                                viewModel.onUserAuthenticated(auth.currentUser!!.uid)
                            }
                        }
                        Firebase.auth.addAuthStateListener(listener)
                        onDispose {
                            Firebase.auth.removeAuthStateListener(listener)
                        }
                    }

                    var phoneVerified by remember(currentUser?.uid) { mutableStateOf(!currentUser?.phoneNumber.isNullOrBlank()) }

                    if (currentUser == null) {
                        // Render login UI with Google Sign-In button
                        AuthScreen(
                            onAuthSuccess = {
                                currentUser = Firebase.auth.currentUser
                                if (currentUser != null) {
                                    viewModel.onUserAuthenticated(currentUser!!.uid)
                                }
                            }
                        )
                    } else if (!phoneVerified) {
                        PhoneVerifyScreen(
                            onVerified = {
                                phoneVerified = true
                                currentUser?.uid?.let { viewModel.onUserAuthenticated(it) }
                            },
                            onSignOut = {
                                viewModel.onSignedOut()
                                signOut(this@MainActivity, CredentialManager.create(this@MainActivity), {}, lifecycleScope)
                            }
                        )
                    } else {
                        val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
                        var showSetupWizard by remember { mutableStateOf(false) }

                        if (!hasCompletedOnboarding) {
                            if (!showSetupWizard) {
                                val initialProfile by viewModel.driverProfile.collectAsStateWithLifecycle()
                                OnboardingScreen(
                                    initialProfile = initialProfile,
                                    onComplete = { profile ->
                                        viewModel.updateProfile(profile)
                                        showSetupWizard = true
                                    }
                                )
                            } else {
                                SetupWizardScreen(
                                    onFinishSetup = {
                                        val currentProfile = viewModel.driverProfile.value
                                        viewModel.completeOnboarding(currentProfile)
                                        showSetupWizard = false
                                    }
                                )
                            }
                        } else if (showSetupWizard) {
                            SetupWizardScreen(
                                onFinishSetup = {
                                    showSetupWizard = false
                                }
                            )
                        } else {
                            MainAppContent(
                                viewModel = viewModel,
                                onOpenSetupWizard = { showSetupWizard = true }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainAppContent(
    viewModel: MainViewModel,
    onOpenSetupWizard: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isReaderActive by viewModel.isReaderActive.collectAsStateWithLifecycle()
    val profile by viewModel.driverProfile.collectAsStateWithLifecycle()
    val rules by viewModel.driverRules.collectAsStateWithLifecycle()
    val todaySummary by viewModel.todaySummary.collectAsStateWithLifecycle()
    val allZones by viewModel.allZones.collectAsStateWithLifecycle()
    val latestJudgment by viewModel.latestJudgment.collectAsStateWithLifecycle()
    val isOverlayVisible by viewModel.isOverlayVisible.collectAsStateWithLifecycle()
    val showSimulator by viewModel.showSimulatorDialog.collectAsStateWithLifecycle()
    val subscription by viewModel.subscription.collectAsStateWithLifecycle()
    val sessionCountdown by viewModel.sessionCountdown.collectAsStateWithLifecycle()
    val sessionProgress by viewModel.sessionProgress.collectAsStateWithLifecycle()
    val isSessionActive by viewModel.isSessionActive.collectAsStateWithLifecycle()
    val showInsufficientDaysDialog by viewModel.showInsufficientDaysDialog.collectAsStateWithLifecycle()
    val activationBannerMessage by viewModel.activationBannerMessage.collectAsStateWithLifecycle()

    var overlayExpanded by remember { mutableStateOf(true) }
    var showSubscriptionScreen by remember { mutableStateOf(false) }
    var showAdminPanel by remember { mutableStateOf(false) }
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val allDrivers by viewModel.allDriversState.collectAsStateWithLifecycle()
    val allRides by viewModel.allRidesState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { androidx.credentials.CredentialManager.create(context) }

    if (showAdminPanel && isAdmin) {
        AdminPanelScreen(
            drivers = allDrivers,
            rides = allRides,
            onAddDaysToDriver = { driverId, days ->
                viewModel.adminAddDays(driverId, days)
            },
            onBack = { showAdminPanel = false }
        )
    } else if (showSubscriptionScreen) {
        com.asphalt.app.ui.screens.SubscriptionScreen(
            currentSubscription = subscription,
            driverProfile = profile,
            driverEmail = Firebase.auth.currentUser?.email ?: "",
            driverUid = Firebase.auth.currentUser?.uid ?: "",
            sessionCountdown = sessionCountdown,
            isSessionActive = isSessionActive,
            sessionProgress = sessionProgress,
            onBack = { showSubscriptionScreen = false }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { viewModel.setSelectedTab(0) },
                        icon = {
                            Icon(
                                if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "الرئيسية"
                            )
                        },
                        label = { Text("الرئيسية") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = AppPrimary, selectedTextColor = AppPrimary),
                        modifier = Modifier.testTag("tab_home")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { viewModel.setSelectedTab(1) },
                        icon = {
                            Icon(
                                if (selectedTab == 1) Icons.Filled.Tune else Icons.Outlined.Tune,
                                contentDescription = "قواعدي"
                            )
                        },
                        label = { Text("قواعدي") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = AppPrimary, selectedTextColor = AppPrimary),
                        modifier = Modifier.testTag("tab_rules")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { viewModel.setSelectedTab(2) },
                        icon = {
                            Icon(
                                if (selectedTab == 2) Icons.Filled.Place else Icons.Outlined.Place,
                                contentDescription = "المناطق"
                            )
                        },
                        label = { Text("المناطق") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = AppPrimary, selectedTextColor = AppPrimary),
                        modifier = Modifier.testTag("tab_zones")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { viewModel.setSelectedTab(3) },
                        icon = {
                            Icon(
                                if (selectedTab == 3) Icons.Filled.Analytics else Icons.Outlined.Analytics,
                                contentDescription = "التحليل"
                            )
                        },
                        label = { Text("التحليل") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = AppPrimary, selectedTextColor = AppPrimary),
                        modifier = Modifier.testTag("tab_analysis")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { viewModel.setSelectedTab(4) },
                        icon = {
                            Icon(
                                if (selectedTab == 4) Icons.Filled.MoreHoriz else Icons.Outlined.MoreHoriz,
                                contentDescription = "المزيد"
                            )
                        },
                        label = { Text("المزيد") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = AppPrimary, selectedTextColor = AppPrimary),
                        modifier = Modifier.testTag("tab_more")
                    )
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    0 -> HomeScreen(
                        profile = profile,
                        rules = rules,
                        summary = todaySummary,
                        isReaderActive = isReaderActive,
                        subscription = subscription,
                        sessionCountdown = sessionCountdown,
                        sessionProgress = sessionProgress,
                        isSessionActive = isSessionActive,
                        bannerMessage = activationBannerMessage,
                        onClearBanner = { viewModel.clearActivationBanner() },
                        showInsufficientDaysDialog = showInsufficientDaysDialog,
                        onDismissInsufficientDaysDialog = { viewModel.dismissInsufficientDaysDialog() },
                        onNavigateToSubscription = { showSubscriptionScreen = true },
                        onToggleReader = { viewModel.toggleReader(it) },
                        onToggleGoingHome = { viewModel.toggleGoingHomeMode() },
                        onToggleGoingDest = { viewModel.toggleGoingDestMode() },
                        onNavigateToRules = { viewModel.setSelectedTab(1) },
                        onNavigateToZones = { viewModel.setSelectedTab(2) },
                        onOpenSimulator = { viewModel.setShowSimulator(true) },
                        isDark = ThemeState.isDark,
                        onToggleTheme = { viewModel.toggleTheme() }
                    )
                    1 -> RulesScreen(
                        currentRules = rules,
                        onSaveRules = { viewModel.updateRules(it) }
                    )
                    2 -> ZonesScreen(
                        zones = allZones,
                        currentGovernorate = profile.governorate,
                        onAddZone = { name, gov, type, notes ->
                            viewModel.addZone(name, gov, type, notes)
                        },
                        onDeleteZone = { viewModel.deleteZone(it) }
                    )
                    3 -> TripAnalysisScreen(
                        driverRules = rules,
                        latestJudgment = latestJudgment
                    )
                    4 -> MoreScreen(
                        isDark = ThemeState.isDark,
                        subscription = subscription,
                        onNavigateToSubscription = { showSubscriptionScreen = true },
                        isAdmin = isAdmin,
                        onNavigateToAdminPanel = { showAdminPanel = true },
                        onSignOut = {
                            viewModel.onSignedOut()
                            signOut(context, credentialManager, onSignOutComplete = {}, scope = coroutineScope)
                        },
                        onToggleTheme = { viewModel.toggleTheme() },
                        captureProvider = { viewModel.lastCaptureText() },
                        onOpenSetupWizard = onOpenSetupWizard
                    )
                }
            }
        }

        // Live In-App Floating Overlay (visible when an order is evaluated)
        if (isOverlayVisible && latestJudgment != null) {
            AnimatedVisibility(
                visible = isOverlayVisible,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp)
            ) {
                FloatingJudgmentOverlayContent(
                    judgment = latestJudgment!!,
                    isExpanded = overlayExpanded,
                    opacity = rules.overlayOpacity,
                    scale = rules.overlayScale,
                    onToggleExpand = { overlayExpanded = !overlayExpanded },
                    onDismiss = { viewModel.dismissOverlay() }
                )
            }
        }
    }
}

    // Live Simulator Dialog
    if (showSimulator) {
        SimulatorDialog(
            onDismiss = { viewModel.setShowSimulator(false) },
            onSimulate = { app, fare, pickup, trip, rating, trips, age, payment, stops, pickupAddr, dropoffAddr, note ->
                viewModel.simulateCard(
                    app = app,
                    fare = fare,
                    pickupKm = pickup,
                    tripKm = trip,
                    passengerRating = rating,
                    passengerTrips = trips,
                    orderAgeSec = age,
                    paymentMethod = payment,
                    stopsCount = stops,
                    pickupAddr = pickupAddr,
                    dropoffAddr = dropoffAddr,
                    note = note
                )
            }
        )
    }
}
