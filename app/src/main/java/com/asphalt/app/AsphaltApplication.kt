package com.asphalt.app

import android.app.Application
import com.asphalt.app.data.AsphaltRepository
import com.asphalt.app.service.AsphaltOverlayManager
import com.asphalt.app.ui.theme.ThemeState

class AsphaltApplication : Application() {

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeState.updateSystemDark(this)
    }

    lateinit var overlayManager: AsphaltOverlayManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase if not already initialized
        if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(this)
        }

        com.asphalt.app.data.TrustedClock.init(this)
        initAppCheck()

        // Initialize Repository + theme (day/night) before any screen is drawn
        val repository = AsphaltRepository.getInstance(this)
        ThemeState.mode = repository.preferences.themeModeFlow.value
        ThemeState.updateSystemDark(this)

        // Initialize Overlay Manager
        overlayManager = AsphaltOverlayManager(this)
        overlayManager.startListening()

        // NOTE: the foreground service is started from MainActivity / the accessibility service
        // (inside try/catch). Starting it here crashes on Android 12+ whenever the system starts
        // the process in the background (e.g. to bind the accessibility service).
    }

    /**
     * App Check: Firestore / Auth only accept requests from the genuine, Play-signed Asphalt app
     * (blocks scripted clients and re-signed/modified APKs from calling the backend directly).
     * Debug builds use the debug provider (register the printed token in the Firebase console).
     */
    private fun initAppCheck() {
        try {
            AppCheckInstaller.install()
        } catch (e: Exception) {
            android.util.Log.w("AsphaltApp", "App Check init failed", e)
        }
    }
}
