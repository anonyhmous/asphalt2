package com.asphalt.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.asphalt.app.MainActivity
import com.asphalt.app.R
import com.asphalt.app.data.AsphaltRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class AsphaltForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: AsphaltRepository
    private lateinit var chimeManager: OpportunityChimeManager
    private var lastActivityTime: Long
        get() = repository.lastActivityTime
        set(value) { repository.lastActivityTime = value }
    private var autoStopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = AsphaltRepository.getInstance(applicationContext)
        chimeManager = OpportunityChimeManager(applicationContext)

        repository.onOpportunityChime = { judgment ->
            val soundOnly = repository.preferences.rulesFlow.value.chimeSoundOnly
            chimeManager.playOpportunityChime(soundOnly)
            updateNotificationWithOffer(judgment.verdict.labelArabic, judgment.grossFare, judgment.netProfit)
        }

        createNotificationChannel()

        // Listen for reader active state
        serviceScope.launch {
            repository.preferences.isReaderActiveFlow.collectLatest { isActive ->
                updateForegroundNotification(isActive)
            }
        }

        // Auto-stop monitor
        startAutoStopMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_READER -> repository.stopReader()
            ACTION_START_READER -> {
                // A new 24h session (and the day deduction) is decided by the server, never locally.
                serviceScope.launch { repository.startReader() }
                lastActivityTime = System.currentTimeMillis()
            }
            ACTION_RECORD_ACTIVITY -> {
                lastActivityTime = System.currentTimeMillis()
            }
        }

        val isActive = repository.preferences.isReaderActiveFlow.value
        val notification = buildNotification(isActive)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun startAutoStopMonitor() {
        autoStopJob?.cancel()
        autoStopJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(60_000) // check every minute
                val hours = repository.preferences.rulesFlow.value.autoStopHours
                if (hours > 0 && repository.preferences.isReaderActiveFlow.value) {
                    val idleTimeMs = System.currentTimeMillis() - lastActivityTime
                    val maxIdleMs = hours * 3600 * 1000L
                    if (idleTimeMs > maxIdleMs) {
                        repository.stopReader()
                    }
                }
            }
        }
    }

    private fun updateForegroundNotification(isActive: Boolean) {
        val notification = buildNotification(isActive)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationWithOffer(verdict: String, fare: Double?, netProfit: Double?) {
        val title = "حكم أسفلت V5: $verdict"
        val text = if (fare != null && netProfit != null) {
            "الأجرة: ${fare.toInt()} ج.م • الصافي بجيبك: ${netProfit.toInt()} ج.م"
        } else {
            "تم فحص الطلب الجديد بدقة"
        }

        val appIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(appIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(isActive: Boolean): Notification {
        val appIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleActionIntent = Intent(this, AsphaltForegroundService::class.java).apply {
            action = if (isActive) ACTION_STOP_READER else ACTION_START_READER
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 1,
            toggleActionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val actionTitle = if (isActive) "وقّف القارئ" else "شغّل القارئ"
        val sub = repository.preferences.subscriptionFlow.value
        val now = System.currentTimeMillis()
        val sessionText = if (sub.isSessionActive(now)) {
            " (حصة 24 س: متبقي ${sub.formatSessionCountdown(now)})"
        } else {
            " (رصيد: ${sub.remainingDays} يوم)"
        }
        val statusText = if (isActive) {
            "القارئ شغّال ومستعد للطلبات$sessionText"
        } else {
            "القارئ متوقف مؤقتاً$sessionText"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("أسفلت V5 - رفيق السائق")
            .setContentText(statusText)
            .setContentIntent(appIntent)
            .addAction(0, actionTitle, togglePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "حالة قارئ أسفلت V5",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار دائم لتشغيل وإيقاف قارئ الطلبات الذكي ومراقبة الخلفية"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        chimeManager.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "asphalt_reader_status_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_STOP_READER = "com.asphalt.app.ACTION_STOP_READER"
        const val ACTION_START_READER = "com.asphalt.app.ACTION_START_READER"
        const val ACTION_RECORD_ACTIVITY = "com.asphalt.app.ACTION_RECORD_ACTIVITY"

        fun start(context: Context) {
            try {
                val intent = Intent(context, AsphaltForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.w("AsphaltFgs", "Could not start foreground service now", e)
            }
        }
    }
}
