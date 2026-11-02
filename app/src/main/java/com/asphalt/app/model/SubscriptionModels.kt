package com.asphalt.app.model

import com.asphalt.app.data.TrustedClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * مدة حصة العمل النشطة الواحدة: 24 ساعة كاملة تبدأ من لحظة تشغيل القارئ
 */
const val SESSION_DURATION_MS = 24L * 3600L * 1000L

/**
 * أنواع باقات الاشتراك في أسفلت:
 * - شهري (30 يوم) مع خصم توفير كبير
 * - أسبوعي (7 أيام)
 * - مخصص بالأيام (من 1 إلى 90 يوم حسب رغبة السائق وأيام عمله)
 */
enum class SubscriptionPlanType(
    val titleArabic: String,
    val subtitleArabic: String,
    val badge: String
) {
    MONTHLY("باقة الشهر الكامل", "30 يوماً من الحساب الفوري والتقييم الذكي", "الأكثر توفيراً (خصم 50%)"),
    WEEKLY("باقة الأسبوع", "7 أيام لتجربة العمل الاحترافي", "الأكثر شعبية (خصم 30%)"),
    CUSTOM_DAYS("باقة الأيام المخصصة", "اختر عدد الأيام التي تعمل بها بدقة", "مرونة كاملة")
}

enum class SubscriptionStatus(val labelArabic: String) {
    ACTIVE("نشط"),
    EXPIRING_SOON("قريب الانتهاء"),
    EXPIRED("منتهي"),
    TRIAL("فترة تجريبية مجانية")
}

/**
 * نتيجة طلب تفعيل أو إيقاف قارئ الطلبات وفق نظام الـ 24 ساعة النشطة
 */
sealed class SessionActivationResult {
    data class NewSessionStarted(val remainingDays: Int, val sessionEndTime: Long) : SessionActivationResult()
    data class ExistingSessionContinued(val remainingMillis: Long) : SessionActivationResult()
    data object Paused : SessionActivationResult()
    data class InsufficientDays(val messageArabic: String) : SessionActivationResult()
    /** A new 24h session must be started (and a day deducted) by the server. */
    data object NeedsServerStart : SessionActivationResult()
}

/**
 * تسعير الاشتراك المحسوب لحظياً في الوقت الفعلي
 */
data class SubscriptionPricingQuote(
    val planType: SubscriptionPlanType,
    val daysCount: Int,
    val basePricePerDay: Double,
    val effectivePricePerDay: Double,
    val grossTotal: Double,
    val discountPct: Int,
    val finalTotal: Double,
    val estimatedExpiryDateFormatted: String
)

/**
 * تفاصيل اشتراك السائق الحالي وحالته:
 * نظام الـ 24 ساعة النشطة:
 * - الأيام لا تنقص بالتقويم العادي
 * - يتم خصم يوم واحد فقط عندما يقوم السائق بتشغيل قارئ الطلبات
 * - تبدأ حصة عمل دقيقة مدتها 24 ساعة من لحظة التشغيل
 * - يمكن للسائق إيقاف وتشغيل القارئ أثناء الـ 24 ساعة دون أي خصم إضافي
 * - لا يتم خصم يوم جديد إلا بعد انتهاء الـ 24 ساعة وإعادة تشغيل القارئ يدوياً
 */
data class DriverSubscription(
    val planType: SubscriptionPlanType = SubscriptionPlanType.MONTHLY,
    val remainingDays: Int = 0, // الرصيد يأتي من الخادم فقط (التجربة المجانية تُمنح من Firestore مرة واحدة)
    val totalDaysPurchased: Int = 0,
    val activeSessionStartTime: Long? = null, // وقت بدء حصة الـ 24 ساعة الحالية (مللي ثانية)
    val activeSessionEndTime: Long? = null,   // وقت انتهاء حصة الـ 24 ساعة الحالية
    val amountPaid: Double = 0.0,
    val autoRenew: Boolean = true,
    val isTrialPlan: Boolean = false
) {
    /** هل توجد حصة عمل 24 ساعة نشطة حالياً في هذه اللحظة؟ */
    fun isSessionActive(currentTime: Long = TrustedClock.now()): Boolean {
        val end = activeSessionEndTime ?: return false
        return currentTime < end
    }

    /** المللي ثانية المتبقية في الحصة النشطة الحالية (0 إذا لم تكن هناك حصة نشطة) */
    fun getRemainingSessionMillis(currentTime: Long = TrustedClock.now()): Long {
        val end = activeSessionEndTime ?: return 0L
        return (end - currentTime).coerceAtLeast(0L)
    }

    /** هل السائق لديه رصيد أيام متبقي لبدء حصص جديدة؟ */
    val hasRemainingBalance: Boolean
        get() = remainingDays > 0

    val isCurrentlyActive: Boolean
        get() = isSessionActive() || remainingDays > 0

    val daysRemaining: Int
        get() = remainingDays

    /** نص منسق للعد التنازلي للحصة النشطة بصيغة HH:mm:ss */
    fun formatSessionCountdown(currentTime: Long = TrustedClock.now()): String {
        val remainingMs = getRemainingSessionMillis(currentTime)
        if (remainingMs <= 0L) return "00:00:00"
        val totalSec = remainingMs / 1000L
        val hours = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /** نسبة التقدم المنقضي من الـ 24 ساعة (من 0f إلى 1f) */
    fun sessionProgress(currentTime: Long = TrustedClock.now()): Float {
        val start = activeSessionStartTime ?: return 0f
        val end = activeSessionEndTime ?: return 0f
        val total = (end - start).toFloat()
        if (total <= 0f) return 0f
        val elapsed = (currentTime - start).toFloat()
        return (elapsed / total).coerceIn(0f, 1f)
    }

    /** تاريخ وتوقيت انتهاء الحصة الحالية بتنسيق مقروء */
    val sessionEndTimeFormatted: String
        get() {
            val end = activeSessionEndTime ?: return "لا توجد حصة نشطة"
            val sdf = SimpleDateFormat("h:mm a - d MMMM", Locale.forLanguageTag("ar"))
            return sdf.format(Date(end))
        }

    val expiryDateFormatted: String
        get() = if (isSessionActive()) {
            "حصة نشطة تنتهي $sessionEndTimeFormatted (متبقي $remainingDays يوم)"
        } else {
            "رصيد متاح: $remainingDays يوم عمل (24 ساعة لكل يوم)"
        }

    val status: SubscriptionStatus
        get() {
            return when {
                isSessionActive() -> SubscriptionStatus.ACTIVE
                remainingDays > 2 -> if (isTrialPlan) SubscriptionStatus.TRIAL else SubscriptionStatus.ACTIVE
                remainingDays in 1..2 -> SubscriptionStatus.EXPIRING_SOON
                else -> SubscriptionStatus.EXPIRED
            }
        }

    val calculatedStatus: SubscriptionStatus
        get() = status
}

/**
 * حاسبة تكاليف الاشتراكات الفورية في الوقت الفعلي
 */
object SubscriptionCalculator {
    const val BASE_DAILY_RATE = 10.0 // 10 جنيه في اليوم كسعر أساسي بدون خصم

    fun calculateQuote(planType: SubscriptionPlanType, customDays: Int = 1): SubscriptionPricingQuote {
        val days = when (planType) {
            SubscriptionPlanType.MONTHLY -> 30
            SubscriptionPlanType.WEEKLY -> 7
            SubscriptionPlanType.CUSTOM_DAYS -> customDays.coerceIn(1, 90)
        }

        val (effectiveRatePerDay, discountPct) = when (planType) {
            SubscriptionPlanType.MONTHLY -> Pair(5.0, 50)  // 150 ج.م لـ 30 يوم (5 ج/يوم)
            SubscriptionPlanType.WEEKLY -> Pair(7.0, 30)   // 49 ج.م لـ 7 أيام (7 ج/يوم)
            SubscriptionPlanType.CUSTOM_DAYS -> when {
                days >= 30 -> Pair(5.0, 50)
                days >= 15 -> Pair(6.0, 40)
                days >= 7 -> Pair(7.0, 30)
                days >= 4 -> Pair(8.0, 20)
                else -> Pair(10.0, 0)
            }
        }

        val gross = days * BASE_DAILY_RATE
        val finalTotal = days * effectiveRatePerDay
        val expiryTime = System.currentTimeMillis() + (days * 24L * 3600L * 1000L)
        val sdf = SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("ar"))

        return SubscriptionPricingQuote(
            planType = planType,
            daysCount = days,
            basePricePerDay = BASE_DAILY_RATE,
            effectivePricePerDay = effectiveRatePerDay,
            grossTotal = gross,
            discountPct = discountPct,
            finalTotal = finalTotal,
            estimatedExpiryDateFormatted = sdf.format(Date(expiryTime))
        )
    }
}
