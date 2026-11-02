package com.asphalt.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.asphalt.app.data.AsphaltRepository
import com.asphalt.app.engine.AsphaltCardParser
import com.asphalt.app.engine.AsphaltOcrEngine
import com.asphalt.app.engine.BubbleColor
import com.asphalt.app.engine.OcrOutput
import com.asphalt.app.model.SupportedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * خدمة قراءة الطلبات (أسفلت V5):
 * - بتقرا بس إن درايف وديدي وأوبر.
 * - بتقرا من مصدرين مع بعض: نص الشاشة (Accessibility) + لقطة شاشة بتتقرا بالـ OCR.
 *   الـ OCR أساسي ودايم، لأن أرقام كتير (زي مسافة الرحلة على الخريطة) مبتظهرش كنص.
 * - للقراءة والحكم بس: ما بتضغطش ولا بتقبل ولا بترفض ولا بتفاوض نيابة عن السواق.
 * - اللقطة بتتقرا في الذاكرة وبتتمسح فوراً، ومبتتحفظش.
 * - مفيش حكم بيظهر غير لو الشاشة فعلاً كارت طلب (سعر + مسافة).
 */
class AsphaltAccessibilityService : AccessibilityService() {

    // All reading/parsing runs on ONE background thread. Walking an accessibility tree is a chain of
    // binder calls; doing it on the main thread (as before) froze the UI on low-end phones.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val scanDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val serviceScope = CoroutineScope(SupervisorJob() + scanDispatcher)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scanQueued = java.util.concurrent.atomic.AtomicBoolean(false)
    private val cardParser = AsphaltCardParser()
    private val ocrEngine: AsphaltOcrEngine by lazy { AsphaltOcrEngine() }
    private var repository: AsphaltRepository? = null

    private var lastProcessTime = 0L
    private var lastCardSeenTime = 0L
    private var lastSupportedSeenTime = 0L
    private var hideJob: Job? = null
    private var tickJob: Job? = null

    // آخر مسافات اتقرت للطلب الحالي، عشان قراءة ناقصة ما تمسحش قراءة صح اتقرت قبلها
    private var memPickup: Double? = null
    private var memTrip: Double? = null
    private var memTripMin: Int? = null

    // أرقام سعر الكيلو فوق أزرار اقتراح الأسعار (إن درايف)
    private val priceTags by lazy { AsphaltPriceTagsOverlay(applicationContext) }
    private var lastPriceButtons: List<Pair<Rect, Double>> = emptyList()

    @Volatile private var ocrBusy = false
    @Volatile private var lastOcrAt = 0L

    private fun repo(): AsphaltRepository {
        return repository ?: AsphaltRepository.getInstance(applicationContext).also { repository = it }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
        try {
            repo()
            // الخدمة اتربطت بالنظام، ودي أنسب لحظة نشغّل فيها الخدمة الدايمة
            AsphaltForegroundService.start(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "onServiceConnected failed", e)
        }

        // قراءة دورية مستمرة: الشاشة الثابتة مبتبعتش أحداث، فمن غير ده الكارت كان بيتقفل ومبيرجعش
        tickJob?.cancel()
        tickJob = serviceScope.launch {
            while (isActive) {
                val idle = System.currentTimeMillis() - lastSupportedSeenTime > IDLE_AFTER_MS
                delay(if (idle) TICK_IDLE_MS else TICK_MS)
                try {
                    scan()
                } catch (e: Exception) {
                    Log.e(TAG, "periodic scan failed", e)
                }
            }
        }

        // نقفل الكارت بس لما الطلب يختفي فعلاً أو السواق يسيب التطبيق
        hideJob?.cancel()
        hideJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val r = repo()
                val now = System.currentTimeMillis()
                val appGone = lastSupportedSeenTime > 0L && now - lastSupportedSeenTime > APP_GONE_MS
                val cardGone = lastCardSeenTime > 0L && now - lastCardSeenTime > CARD_GONE_MS
                if (appGone || cardGone) {
                    if (r.isOverlayVisible.value || lastCardSeenTime > 0L) r.dismissOverlay()
                    lastCardSeenTime = 0L
                    resetMemory()
                }
            }
        }
    }

    private fun resetMemory() {
        memPickup = null
        memTrip = null
        memTripMin = null
        lastPriceButtons = emptyList()
        clearTags()
    }

    private fun clearTags() {
        try {
            mainHandler.post { try { priceTags.clear() } catch (e: Exception) { Log.w(TAG, "clear tags failed", e) } }
            repo().extraMaskRects = emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "clear tags failed", e)
        }
    }

    /** بيكتب سعر الكيلو فوق كل زرار اقتراح سعر، بس لو المسافتين (استلام + رحلة) معروفين فعلاً. */
    private fun refreshTags(app: SupportedApp) {
        try {
            val p = memPickup
            val t = memTrip
            if (app != SupportedApp.INDRIVER || p == null || t == null || lastPriceButtons.isEmpty()) {
                clearTags()
                return
            }
            val total = p + t
            if (total < 0.1) {
                clearTags()
                return
            }
            val tags = lastPriceButtons.map { (rect, price) ->
                PriceTag(rect, String.format("%.2f للكيلو", price / total))
            }
            mainHandler.post { try { priceTags.show(tags) } catch (e: Exception) { Log.w(TAG, "show tags failed", e) } }
            repo().extraMaskRects = tags.map { it.bounds }
        } catch (e: Exception) {
            Log.w(TAG, "refresh tags failed", e)
        }
    }

    /** أزرار اقتراح الأسعار: نص "54 EGP" لوحده في الربع التحتاني من الشاشة (مش زرار القبول ولا السعر الكبير). */
    private fun collectPriceButtons(roots: List<AccessibilityNodeInfo>, fare: Double?): List<Pair<Rect, Double>> {
        val out = mutableListOf<Pair<Rect, Double>>()
        val minTop = resources.displayMetrics.heightPixels * 0.75f
        for (root in roots) walkPriceButtons(root, 0, minTop, fare, out)
        return out
    }

    private fun walkPriceButtons(
        node: AccessibilityNodeInfo?, depth: Int, minTop: Float, fare: Double?,
        out: MutableList<Pair<Rect, Double>>
    ) {
        if (node == null || depth > MAX_DEPTH || out.size >= 8) return
        val txt = node.text?.toString()?.trim()
        if (txt != null && node.isVisibleToUser) {
            val m = PRICE_BUTTON.matchEntire(asciiDigits(txt))
            if (m != null) {
                val v = m.groupValues[1].replace(',', '.').toDoubleOrNull()
                val r = Rect()
                node.getBoundsInScreen(r)
                if (v != null && r.top >= minTop && !r.isEmpty && (fare == null || kotlin.math.abs(v - fare) > 0.01)) {
                    out.add(Pair(r, v))
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            walkPriceButtons(child, depth + 1, minTop, fare, out)
            recycleCompat(child)
        }
    }

    /** AccessibilityNodeInfo.recycle() is a no-op on API 33+, but leaks pooled native objects before that. */
    @Suppress("DEPRECATION")
    private fun recycleCompat(node: AccessibilityNodeInfo?) {
        if (node != null && Build.VERSION.SDK_INT < 33) {
            try { node.recycle() } catch (_: Exception) {}
        }
    }

    private fun asciiDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                '\u066B' -> sb.append('.')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            handleEvent(event)
        } catch (e: Exception) {
            // أي خطأ بيخرج من هنا بيقفل التطبيق كله، عشان كده بنمسكه دايماً
            Log.e(TAG, "Error while reading screen", e)
        }
    }

    private fun handleEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (SupportedApp.fromPackage(pkg) == null) return // أي تطبيق تاني بيتتجاهل تماماً
        if (!repo().preferences.isReaderActiveFlow.value) return
        // Coalesce bursts of events: at most one pending scan.
        if (!scanQueued.compareAndSet(false, true)) return
        serviceScope.launch {
            try { scan() } catch (e: Exception) { Log.e(TAG, "scan failed", e) } finally { scanQueued.set(false) }
        }
    }

    /** بتقرا الشاشة (من الأحداث ومن المؤقت الدوري)، ولو فيها كارت طلب بتبعته للحكم. */
    private fun scan() {
        val r = repo()
        if (!r.preferences.isReaderActiveFlow.value) return

        val now = com.asphalt.app.data.TrustedClock.now()
        val sub = r.preferences.subscriptionFlow.value
        if (!sub.isSessionActive(now)) {
            // انتهت الـ 24 ساعة أثناء عمل الخدمة، يتم إيقاف القارئ لحين بدء حصة جديدة يدوياً
            r.preferences.checkAndExpireActiveSession(now)
            return
        }

        val wall = System.currentTimeMillis()
        if (wall - lastProcessTime < THROTTLE_MS) return
        lastProcessTime = wall

        // 1) نص الشاشة من كل نوافذ التطبيقات المدعومة (الكارت غالباً نافذة منفصلة عن الخريطة)
        var app: SupportedApp? = null
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            for (w in windows) {
                val root = w.root ?: continue
                val wApp = SupportedApp.fromPackage(root.packageName?.toString()) ?: continue
                app = wApp
                roots.add(root)
            }
        } catch (e: Exception) {
            Log.w(TAG, "windows scan failed, falling back to active window", e)
        }
        if (roots.isEmpty()) {
            val root = rootInActiveWindow
            val rootApp = SupportedApp.fromPackage(root?.packageName?.toString())
            if (root != null && rootApp != null) {
                app = rootApp
                roots.add(root)
            }
        }
        val currentApp = app ?: return // مفيش تطبيق توصيل قدام السواق
        try {

            lastSupportedSeenTime = wall
            r.lastActivityTime = wall

            // الأول بنقرا العناصر الظاهرة بس (عشان شاشات قديمة ورا الكارت ما تلخبطش)، ولو طلع فاضي نقرا الكل
            val builder = StringBuilder()
            for (root in roots) extractNodeTexts(root, builder, 0, true)
            if (builder.isBlank()) {
                for (root in roots) extractNodeTexts(root, builder, 0, false)
            }
            val screenText = builder.toString().trim()
            r.lastCapture = "[نص الشاشة]\n$screenText"

            // 2) لو النص لوحده كفاية بنعرض الحكم فوراً، والـ OCR بيكمّل الناقص بعدها
            if (screenText.isNotBlank()) evaluate(currentApp, screenText, null)

            // أزرار اقتراح الأسعار (إن درايف بس) عشان نكتب فوقها سعر الكيلو
            lastPriceButtons = if (currentApp == SupportedApp.INDRIVER) {
                try { collectPriceButtons(roots, repo().latestJudgment.value?.grossFare) } catch (e: Exception) { emptyList() }
            } else emptyList()
            refreshTags(currentApp)

            // 3) الـ OCR شغال دايماً (بفاصل زمني) حتى لو النص فاضي
            requestOcr(currentApp, screenText)
        } finally {
            roots.forEach { recycleCompat(it) }
        }
    }

    /**
     * بيحوّل النص + نتيجة الـ OCR لطلب، ولو شكله كارت طلب بيبعته للحكم.
     * المسافات: الاستلام من نص الشاشة (أو الفقاعة الزرقا)، والرحلة من الفقاعة الخضرا.
     * وبنحتفظ بآخر قراءة صح للطلب الحالي عشان قراءة ناقصة ما تمسحهاش.
     */
    private fun evaluate(app: SupportedApp, text: String, ocr: OcrOutput?) {
        val r = repo()

        // شاشة الرحلات تحت بعضها مش كارت رحلة واحدة: مفيش حكم، والكارت القديم يتقفل
        if (app == SupportedApp.INDRIVER && cardParser.looksLikeInDriveList(text)) {
            if (r.isOverlayVisible.value || lastCardSeenTime > 0L) r.dismissOverlay()
            lastCardSeenTime = 0L
            resetMemory()
            return
        }

        val parsed = cardParser.parse(text, app)
        var pickup = parsed.pickupDistanceKm
        var trip = parsed.tripDistanceKm

        var order = parsed
        if (ocr != null) {
            val blue = ocr.bubbles.firstOrNull { it.color == BubbleColor.BLUE }?.km
            val green = ocr.bubbles.firstOrNull { it.color == BubbleColor.GREEN }?.km
            if (green != null) trip = green          // اللون هو المرجع للرحلة
            ocr.bubbles.firstOrNull { it.color == BubbleColor.GREEN }?.minutes?.let { memTripMin = it }
            if (pickup == null) pickup = blue

            // تكملة السعر (ولو مش إن درايف، تخمين المسافات القديم لو مفيش ألوان ولا نص)
            val noColors = blue == null && green == null
            val guess = app != SupportedApp.INDRIVER && noColors && pickup == null && trip == null
            order = cardParser.fillFromOcrNumbers(parsed, ocr.numbers, guess)
            if (guess) {
                pickup = order.pickupDistanceKm
                trip = order.tripDistanceKm
            }
        }

        // طلب جديد (استلام اختلف كتير) = ننسى مسافة الرحلة القديمة
        val oldPickup = memPickup
        if (pickup != null && oldPickup != null && kotlin.math.abs(pickup - oldPickup) > NEW_ORDER_PICKUP_DELTA) {
            memTrip = null
            memTripMin = null
        }
        if (pickup != null) memPickup = pickup
        if (trip != null) memTrip = trip

        order = order.copy(
            pickupDistanceKm = pickup ?: memPickup,
            tripDistanceKm = trip ?: memTrip,
            tripMinutes = memTripMin
        )

        // مش كارت طلب (شاشة رئيسية، أرباح، قوائم...) = مفيش حكم وهمي
        if (!cardParser.looksLikeOrderCard(order)) return

        lastCardSeenTime = System.currentTimeMillis()
        r.processOrder(order)
    }

    // ------------------------------------------------------------------ OCR

    private fun requestOcr(app: SupportedApp, screenText: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return // لقطة الشاشة من الخدمة محتاجة أندرويد 11+

        val now = SystemClock.elapsedRealtime()
        if (ocrBusy && now - lastOcrAt > OCR_STUCK_MS) ocrBusy = false // لو الرد اتأخر قوي نكمّل
        val recentlySawCard = System.currentTimeMillis() - lastCardSeenTime < 10_000L
        val interval = if (recentlySawCard) OCR_FAST_MS else OCR_IDLE_MS
        if (ocrBusy || now - lastOcrAt < interval) return

        ocrBusy = true
        lastOcrAt = now
        try {
            captureScreen(app, screenText)
        } catch (e: Exception) {
            ocrBusy = false
            Log.w(TAG, "screenshot request failed", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureScreen(app: SupportedApp, screenText: String) {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val activePkg = try { rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }
                    if (SupportedApp.fromPackage(activePkg) == null) {
                        try { screenshot.hardwareBuffer.close() } catch (_: Exception) {}
                        ocrBusy = false
                        return
                    }

                    // المعالجة على خلفية المعالج لضمان عدم حدوث أي تهنيج أو لاج على شاشة السواق
                    serviceScope.launch(Dispatchers.Default) {
                        val prepared = try {
                            val bitmap = toBitmap(screenshot) ?: run {
                                ocrBusy = false
                                return@launch
                            }
                            prepareForOcr(bitmap)
                        } catch (e: Exception) {
                            ocrBusy = false
                            Log.w(TAG, "Bitmap preparation failed", e)
                            return@launch
                        }

                        ocrEngine.recognize(prepared) { output ->
                            ocrBusy = false
                            try { prepared.recycle() } catch (_: Exception) {}
                            if (output != null) {
                                serviceScope.launch {
                                    val r = repo()
                                    val bubblesInfo = output.bubbles.joinToString(" | ") {
                                        "${if (it.color == BubbleColor.BLUE) "أزرق" else "أخضر"}=${it.km}"
                                    }
                                    r.lastCapture = "[نص الشاشة]\n$screenText\n\n[OCR]\n${output.text}\n\n[فقاعات ملوّنة] $bubblesInfo"
                                    evaluate(app, screenText + "\n" + output.text, output)
                                    refreshTags(app)
                                }
                            }
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    ocrBusy = false
                    Log.w(TAG, "takeScreenshot failed: $errorCode")
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun toBitmap(result: AccessibilityService.ScreenshotResult): Bitmap? {
        val buffer = result.hardwareBuffer
        try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return null
            val software = hardware.copy(Bitmap.Config.ARGB_8888, false)
            hardware.recycle()
            return software
        } finally {
            buffer.close()
        }
    }

    /**
     * بنصغّر اللقطة (أسرع)، وبنغطي شريط الحالة وكارت أسفلت العايم عشان الـ OCR ما يقراش نفسه.
     * اللقطة الأصلية بتتمسح بعد النسخ.
     */
    private fun prepareForOcr(source: Bitmap): Bitmap {
        val scale = if (source.width > MAX_OCR_WIDTH) MAX_OCR_WIDTH.toFloat() / source.width else 1f
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()

        val black = Paint().apply { color = Color.BLACK }
        // شريط الحالة (الساعة والبطارية) فيه أرقام مش مطلوبة
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.045f, black)
        // أرقام سعر الكيلو اللي كاتبينها فوق الأزرار
        for (b in repo().extraMaskRects) {
            canvas.drawRect(b.left * scale, b.top * scale, b.right * scale, b.bottom * scale, black)
        }
        // كارت أسفلت العايم
        repo().overlayBounds?.let { b ->
            canvas.drawRect(
                b.left * scale, b.top * scale,
                b.right * scale, b.bottom * scale,
                black
            )
        }
        return out
    }

    // ------------------------------------------------------------ node text

    private fun extractNodeTexts(node: AccessibilityNodeInfo?, builder: StringBuilder, depth: Int, visibleOnly: Boolean) {
        if (node == null || depth > MAX_DEPTH || builder.length > MAX_CHARS) return
        try {
            if (visibleOnly && !node.isVisibleToUser) return

            node.text?.let {
                if (it.isNotBlank()) builder.append(it).append("\n")
            }
            node.contentDescription?.let {
                if (it.isNotBlank() && it.toString() != node.text?.toString()) builder.append(it).append("\n")
            }

            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) {
                    extractNodeTexts(child, builder, depth + 1, visibleOnly)
                    recycleCompat(child)
                }
            }
        } catch (_: Exception) {
            // تجاهل أي عقد تم إلغاؤها ديناميكياً أثناء حركة الشاشة
        }
    }

    override fun onInterrupt() {
        // الخدمة اتقاطعت، مفيش حاجة نعملها
    }

    override fun onDestroy() {
        hideJob?.cancel()
        tickJob?.cancel()
        clearTags()
        serviceScope.cancel()
        ocrEngine.close()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AsphaltAccessibility"
        private const val THROTTLE_MS = 500L
        private val PRICE_BUTTON = Regex("^(\\d+(?:[.,]\\d+)?)\\s*EGP$")
        private const val CARD_GONE_MS = 6_000L
        private const val APP_GONE_MS = 3_000L
        private const val TICK_MS = 1_000L
        private const val TICK_IDLE_MS = 3_000L
        private const val IDLE_AFTER_MS = 10_000L
        private const val NEW_ORDER_PICKUP_DELTA = 0.6
        private const val OCR_FAST_MS = 1_200L
        private const val OCR_IDLE_MS = 2_500L
        private const val OCR_STUCK_MS = 6_000L
        private const val MAX_OCR_WIDTH = 1080
        private const val MAX_DEPTH = 40
        private const val MAX_CHARS = 8_000
    }
}
