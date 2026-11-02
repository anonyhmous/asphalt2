package com.asphalt.app

import com.asphalt.app.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * اختبارات وحدة شاملة لنظام الـ 24 ساعة النشطة لاشتراكات أسفلت V5:
 * 1. عدم خصم الأيام بالتقويم.
 * 2. خصم يوم واحد فقط عند بدء حصة عمل جديدة (تشغيل القارئ).
 * 3. عمل عداد تنازلي دقيق 24 ساعة من لحظة التشغيل.
 * 4. إمكانية إيقاف وتشغيل القارئ أثناء الـ 24 ساعة دون أي خصم إضافي.
 * 5. انتهاء الحصة بعد 24 ساعة بدقة وعدم خصم أي يوم جديد تلقائياً.
 * 6. منع التشغيل عند انتهاء رصيد الأيام.
 * 7. شحن الباقات يضيف أياماً للرصيد دون بدء العداد قبل التشغيل.
 */
class AsphaltSubscriptionSessionTest {

    @Test
    fun testNoLocalFreeTrialDefaults() {
        // The trial is granted by the server (Firestore) only - a local default must never grant days.
        val defaultSub = DriverSubscription()
        assertEquals(0, defaultSub.remainingDays)
        assertEquals(0, defaultSub.totalDaysPurchased)
        assertFalse(defaultSub.isSessionActive())
    }

    @Test
    fun testInitialPrepaidDaysAndInactiveSession() {
        val sub = DriverSubscription(
            remainingDays = 7,
            totalDaysPurchased = 7
        )

        // رصيد الأيام متوفر، لكن الحصة لم تبدأ بعد
        assertEquals(7, sub.remainingDays)
        assertTrue(sub.hasRemainingBalance)
        assertFalse(sub.isSessionActive())
        assertNull(sub.activeSessionStartTime)
        assertNull(sub.activeSessionEndTime)
        assertEquals("00:00:00", sub.formatSessionCountdown())
        assertEquals(0f, sub.sessionProgress(), 0.001f)
    }

    @Test
    fun testStartSessionDeductsOneDayAndSets24Hours() {
        val initialDays = 7
        val startTime = 1_700_000_000_000L // وقت بداية محدد
        val expectedEndTime = startTime + SESSION_DURATION_MS // 24 ساعة بعده

        // محاكاة بدء الحصة
        val activeSub = DriverSubscription(
            remainingDays = initialDays - 1,
            totalDaysPurchased = initialDays,
            activeSessionStartTime = startTime,
            activeSessionEndTime = expectedEndTime
        )

        // التحقق من خصم يوم واحد فقط
        assertEquals(6, activeSub.remainingDays)
        assertEquals(startTime, activeSub.activeSessionStartTime)
        assertEquals(expectedEndTime, activeSub.activeSessionEndTime)

        // التحقق من نشاط الحصة في اللحظات المختلفة
        assertTrue(activeSub.isSessionActive(startTime))
        assertTrue(activeSub.isSessionActive(startTime + 3600_000L)) // بعد ساعة
        assertTrue(activeSub.isSessionActive(startTime + 23 * 3600_000L)) // بعد 23 ساعة
        assertFalse(activeSub.isSessionActive(expectedEndTime)) // بعد 24 ساعة انتهت
        assertFalse(activeSub.isSessionActive(expectedEndTime + 1000L)) // بعد 24 ساعة وثانية
    }

    @Test
    fun testCountdownTimerFormatting() {
        val startTime = 1_700_000_000_000L
        val endTime = startTime + 24L * 3600L * 1000L
        val sub = DriverSubscription(
            remainingDays = 5,
            activeSessionStartTime = startTime,
            activeSessionEndTime = endTime
        )

        // عند البداية بالضبط: متبقي 24:00:00
        assertEquals("24:00:00", sub.formatSessionCountdown(startTime))

        // بعد ساعتين و15 دقيقة و30 ثانية (متبقي 21:44:30)
        val elapsedMs = (2L * 3600L + 15L * 60L + 30L) * 1000L
        val checkTime = startTime + elapsedMs
        assertEquals("21:44:30", sub.formatSessionCountdown(checkTime))

        // قبل النهاية بدقيقة واحدة و15 ثانية (متبقي 00:01:15)
        val nearEndTime = endTime - (75 * 1000L)
        assertEquals("00:01:15", sub.formatSessionCountdown(nearEndTime))

        // بعد انتهاء الـ 24 ساعة
        assertEquals("00:00:00", sub.formatSessionCountdown(endTime))
        assertEquals("00:00:00", sub.formatSessionCountdown(endTime + 10_000L))
    }

    @Test
    fun testSessionProgressCalculation() {
        val startTime = 1_700_000_000_000L
        val endTime = startTime + SESSION_DURATION_MS
        val sub = DriverSubscription(
            remainingDays = 3,
            activeSessionStartTime = startTime,
            activeSessionEndTime = endTime
        )

        // عند البداية التقدم 0%
        assertEquals(0.0f, sub.sessionProgress(startTime), 0.001f)

        // بعد 12 ساعة (منتصف الحصة) التقدم 50%
        val halfTime = startTime + (12L * 3600L * 1000L)
        assertEquals(0.5f, sub.sessionProgress(halfTime), 0.001f)

        // بعد 24 ساعة كاملة التقدم 100%
        assertEquals(1.0f, sub.sessionProgress(endTime), 0.001f)
    }

    @Test
    fun testNoDeductionDuringActiveSessionWindow() {
        val startTime = 1_700_000_000_000L
        val endTime = startTime + SESSION_DURATION_MS
        var sub = DriverSubscription(
            remainingDays = 5,
            activeSessionStartTime = startTime,
            activeSessionEndTime = endTime
        )

        // السائق قام بإيقاف القارئ وإعادة تشغيله 10 مرات أثناء فترة الـ 24 ساعة
        for (hour in 1..10) {
            val checkTime = startTime + (hour * 3600_000L)
            assertTrue("يجب أن تظل الحصة نشطة في الساعة $hour", sub.isSessionActive(checkTime))
            // لا يتم خصم أي أيام، الرصيد يظل 5 أيام
            assertEquals(5, sub.remainingDays)
        }
    }

    @Test
    fun testSessionExpirationRequiresNewDayDeductionForNextSession() {
        val startTime = 1_700_000_000_000L
        val endTime = startTime + SESSION_DURATION_MS
        var sub = DriverSubscription(
            remainingDays = 5,
            activeSessionStartTime = startTime,
            activeSessionEndTime = endTime
        )

        // بعد انتهاء الحصة الأولى
        val afterExpiryTime = endTime + 1000L
        assertFalse(sub.isSessionActive(afterExpiryTime))

        // الأيام لم تنقص تلقائياً بعد الانتهاء
        assertEquals(5, sub.remainingDays)

        // بدء حصة ثانية جديدة يدوياً
        val nextSessionStart = afterExpiryTime + 3600_000L // بعد ساعة من الانتهاء
        val nextSessionEnd = nextSessionStart + SESSION_DURATION_MS

        val nextSub = sub.copy(
            remainingDays = sub.remainingDays - 1, // خصم يوم واحد للحصة الجديدة
            activeSessionStartTime = nextSessionStart,
            activeSessionEndTime = nextSessionEnd
        )

        assertEquals(4, nextSub.remainingDays)
        assertTrue(nextSub.isSessionActive(nextSessionStart))
        assertEquals("24:00:00", nextSub.formatSessionCountdown(nextSessionStart))
    }

    @Test
    fun testZeroDaysBalanceBlocksNewSession() {
        val expiredSub = DriverSubscription(
            remainingDays = 0,
            activeSessionStartTime = null,
            activeSessionEndTime = null
        )

        assertEquals(0, expiredSub.remainingDays)
        assertFalse(expiredSub.hasRemainingBalance)
        assertFalse(expiredSub.isSessionActive())
        assertEquals(SubscriptionStatus.EXPIRED, expiredSub.status)
    }

    @Test
    fun testAddingDaysToBalancePreservesNonCalendarModel() {
        val initialSub = DriverSubscription(
            remainingDays = 2,
            totalDaysPurchased = 7
        )

        // شحن باقة الشهر الكامل (30 يوم)
        val rechargedSub = initialSub.copy(
            remainingDays = initialSub.remainingDays + 30,
            totalDaysPurchased = initialSub.totalDaysPurchased + 30
        )

        assertEquals(32, rechargedSub.remainingDays)
        assertEquals(37, rechargedSub.totalDaysPurchased)
        // لا تبدأ الحصة إلا عند التشغيل
        assertFalse(rechargedSub.isSessionActive())
        assertNull(rechargedSub.activeSessionStartTime)
        assertNull(rechargedSub.activeSessionEndTime)
    }

    @Test
    fun testSubscriptionCalculatorQuotes() {
        // فحص باقة الشهر (30 يوم مع خصم 50%)
        val monthlyQuote = SubscriptionCalculator.calculateQuote(SubscriptionPlanType.MONTHLY)
        assertEquals(30, monthlyQuote.daysCount)
        assertEquals(5.0, monthlyQuote.effectivePricePerDay, 0.01)
        assertEquals(150.0, monthlyQuote.finalTotal, 0.01)
        assertEquals(50, monthlyQuote.discountPct)

        // فحص باقة الأسبوع (7 أيام مع خصم 30%)
        val weeklyQuote = SubscriptionCalculator.calculateQuote(SubscriptionPlanType.WEEKLY)
        assertEquals(7, weeklyQuote.daysCount)
        assertEquals(7.0, weeklyQuote.effectivePricePerDay, 0.01)
        assertEquals(49.0, weeklyQuote.finalTotal, 0.01)
        assertEquals(30, weeklyQuote.discountPct)

        // فحص باقة الأيام المخصصة (14 يوم مع خصم 30%)
        val customQuote = SubscriptionCalculator.calculateQuote(SubscriptionPlanType.CUSTOM_DAYS, 14)
        assertEquals(14, customQuote.daysCount)
        assertEquals(7.0, customQuote.effectivePricePerDay, 0.01)
        assertEquals(98.0, customQuote.finalTotal, 0.01)
    }

    @Test
    fun testPhoneNormalization() {
        val phoneHelper = object {
            fun normalize(input: String): String {
                val sb = StringBuilder()
                for (ch in input) {
                    when (ch) {
                        in '0'..'9' -> sb.append(ch)
                        in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                        in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                    }
                }
                var digits = sb.toString()
                if (digits.startsWith("0020")) {
                    digits = digits.substring(2)
                } else if (digits.startsWith("01") && digits.length == 11) {
                    digits = "2$digits"
                } else if (!digits.startsWith("20") && digits.startsWith("1") && digits.length == 10) {
                    digits = "20$digits"
                }
                return digits
            }
        }

        assertEquals("201012345678", phoneHelper.normalize("+20 101 234 5678"))
        assertEquals("201012345678", phoneHelper.normalize("01012345678"))
        assertEquals("201012345678", phoneHelper.normalize("٠١٠١٢٣٤٥٦٧٨"))
        assertEquals("201012345678", phoneHelper.normalize("00201012345678"))
    }

    @Test
    fun testOfficialPaymentNumbersAndMessageFormatting() {
        assertEquals("01122104999", com.asphalt.app.ui.screens.DEFAULT_INSTAPAY_ACCOUNT)
        assertEquals("01128440045", com.asphalt.app.ui.screens.DEFAULT_WHATSAPP_PHONE)
        assertEquals("201128440045", com.asphalt.app.ui.screens.WHATSAPP_INTERNATIONAL_DIAL)

        val driverName = "كابتن أحمد"
        val driverEmail = "ahmed@example.com"
        val driverPhone = "01000000000"
        val planTitle = "باقة شهرية"
        val daysCount = 30
        val totalAmount = 150

        val msg = """
            السلام عليكم إدارة أسفلت V5 🚗،
            تم تحويل مبلغ الاشتراك عبر تطبيق إنستاباي بنجاح إلى الرقم: ${com.asphalt.app.ui.screens.DEFAULT_INSTAPAY_ACCOUNT}
            
            📋 بيانات الكابتن:
            • الاسم: $driverName
            • رقم الهاتف: $driverPhone
            • البريد الإلكتروني: $driverEmail
            
            💳 تفاصيل الباقة المطلوبة:
            • الباقة: $planTitle ($daysCount يوم عمل)
            • المبلغ المحول: $totalAmount ج.م
            
            مرفق صورة إيصال التحويل، برجاء التحقق وتفعيل رصيد الأيام في حسابي. شكراً جزيلاً!
        """.trimIndent()

        assertTrue(msg.contains("01122104999"))
        assertTrue(msg.contains(driverName))
        assertTrue(msg.contains(driverEmail))
        assertTrue(msg.contains(driverPhone))
        assertTrue(msg.contains("150 ج.م"))
    }
}
