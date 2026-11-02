package com.asphalt.app.engine

import com.asphalt.app.model.OrderDetails
import com.asphalt.app.model.PaymentMethod
import com.asphalt.app.model.SupportedApp
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Robust multilingual (Arabic & English) parser for extracting ride offer card metrics.
 * Designed to accurately extract numbers without guessing.
 */
class AsphaltCardParser {

    private val fareRegex = Pattern.compile(
        """(?:ج\.م|جنيه|جنيهاً|E£|£|LE|EGP|SAR|AED|USD|ر\.س|\$)?\s*([0-9]+(?:[.,][0-9]{1,2})?)\s*(?:ج\.م|جنيه|جنيهاً|E£|£|LE|EGP|SAR|AED|USD|ر\.س|\$)?""",
        Pattern.CASE_INSENSITIVE
    )

    private val distanceKmRegex = Pattern.compile(
        """([0-9]+(?:[.,][0-9]{1,2})?)\s*(?:كم|كيلومتر|km)""",
        Pattern.CASE_INSENSITIVE
    )

    private val distanceMeterRegex = Pattern.compile(
        """([0-9]+)\s*(?:متر|م\b|m\b)""",
        Pattern.CASE_INSENSITIVE
    )

    private val ratingRegex = Pattern.compile(
        """(?:★|⭐)?\s*([45]\.[0-9]{1,2})\s*(?:★|⭐)?"""
    )

    private val tripsRegex = Pattern.compile(
        """([0-9]{1,5})\s*(?:رحلة|مشوار|trips?|rides?)""",
        Pattern.CASE_INSENSITIVE
    )

    private val orderAgeRegex = Pattern.compile(
        """([0-9]{1,3})\s*(?:ث|ثانية|ثوان|sec|s\b)""",
        Pattern.CASE_INSENSITIVE
    )

    /** Converts Arabic-Indic / Persian digits and Arabic separators to plain ASCII so regexes work on Arabic phones. */
    private fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                '\u066B' -> sb.append('.')
                '\u066C' -> sb.append(',')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * A real ride-offer card shows a price AND at least one distance.
     * Anything else (home screen, earnings, menus) must not produce a judgment.
     */
    fun looksLikeOrderCard(order: OrderDetails): Boolean {
        return order.fare != null && (order.pickupDistanceKm != null || order.tripDistanceKm != null)
    }

    /**
     * بيكمّل الناقص (السعر / المسافات) من أرقام الـ OCR، بالاعتماد على حجم الرقم ومكانه
     * مش على الكلام العربي. الأولوية دايماً لللي اتقرا من نص الشاشة نفسه.
     * ده تخمين منظم: السعر هو أكبر رقم على الشاشة، والمسافات هي الأرقام العشرية بالترتيب من فوق لتحت.
     */
    fun fillFromOcrNumbers(
        order: OrderDetails,
        numbers: List<OcrNumber>,
        guessDistances: Boolean = true
    ): OrderDetails {
        var fare = order.fare
        var pickup = order.pickupDistanceKm
        var trip = order.tripDistanceKm

        if (fare == null) {
            fare = numbers
                .filter { it.value in 20.0..2500.0 }
                .maxWithOrNull(compareBy<OcrNumber>({ it.heightPx }, { -it.topPx }))
                ?.value
        }

        if (guessDistances && pickup == null && trip == null) {
            val distances = numbers
                .filter { n ->
                    n.text.contains('.') &&
                        n.value in 0.1..80.0 &&
                        n.value != fare &&
                        // رقمين بعد العلامة وبين 4 و5 غالباً تقييم راكب (زي 4.92) مش مسافة
                        !(n.text.substringAfter('.').length == 2 && n.value in 4.0..5.0)
                }
                .sortedBy { it.topPx }

            when {
                distances.size >= 2 -> {
                    pickup = distances[0].value
                    trip = distances[1].value
                }
                distances.size == 1 -> {
                    val d = distances[0].value
                    if (d < 4.0) pickup = d else trip = d
                }
            }
        }

        return order.copy(fare = fare, pickupDistanceKm = pickup, tripDistanceKm = trip)
    }

    private fun tenth(v: Double): Int = (v * 10).roundToInt()

    /** كل المسافات (كم أو متر) في سطر واحد، بالكيلومتر. */
    private fun extractLineDistances(line: String): List<Double> {
        val out = mutableListOf<Double>()
        val km = distanceKmRegex.matcher(line)
        while (km.find()) {
            km.group(1)?.replace(",", ".")?.toDoubleOrNull()?.let { out.add(it) }
        }
        val m = distanceMeterRegex.matcher(line)
        while (m.find()) {
            m.group(1)?.toDoubleOrNull()?.let { out.add(it / 1000.0) }
        }
        return out
    }

    /**
     * إن درايف: بيرجع (استلام، رحلة) من النص بس لو واضح.
     * - لو في رقم بعلامة ~ يبقى هو الاستلام، وأي رقم تاني مختلف (واحد بس) هو الرحلة.
     * - من غير علامة ~: لو رقم واحد بس يبقى استلام، ولو اتنين والأكتر تكراراً هو الاستلام.
     * - غير كده بنسيب الاتنين فاضيين والـ OCR الملوّن هو اللي يحدد، ومبنخمنش.
     */
    private fun resolveInDriveDistances(tilde: List<Double>, all: List<Double>): Pair<Double?, Double?> {
        if (all.isEmpty()) return Pair(null, null)
        if (tilde.isNotEmpty()) {
            val p = tilde[0]
            val others = all.filter { abs(it - p) > 0.15 }.distinctBy { tenth(it) }
            val trip = if (others.size == 1) others[0] else null
            return Pair(p, trip)
        }
        val counts = all.groupingBy { tenth(it) }.eachCount()
        if (counts.size == 1) return Pair(all[0], null)
        if (counts.size == 2) {
            val maxCount = counts.values.maxOrNull() ?: return Pair(null, null)
            val topKeys = counts.filterValues { it == maxCount }.keys
            if (topKeys.size == 1) {
                val pKey = topKeys.first()
                val p = all.first { tenth(it) == pKey }
                val t = all.first { tenth(it) != pKey }
                return Pair(p, t)
            }
        }
        return Pair(null, null)
    }

    /**
     * شاشة "الرحلات تحت بعضها" في إن درايف (مش كارت رحلة واحدة): فيها أكتر من رقم استلام بعلامة ~.
     * شاشة الرحلة نفسها فيها كلمات مميزة (اقترح أجرتك / القبول مقابل) فمبنعتبرهاش قائمة أبداً.
     */
    fun looksLikeInDriveList(originalText: String): Boolean {
        val text = normalizeDigits(originalText)
        if (OFFER_MARKERS.any { text.contains(it, ignoreCase = true) }) return false
        val seen = mutableSetOf<Int>()
        for (line in text.split("\n")) {
            if (TILDE_CHARS.none { line.contains(it) }) continue
            for (v in extractLineDistances(line)) seen.add(tenth(v))
        }
        return seen.size >= 2
    }

    fun parse(originalText: String, app: SupportedApp, isListCard: Boolean = false): OrderDetails {
        val rawText = normalizeDigits(originalText)
        var fare: Double? = null
        var pickupKm: Double? = null
        var tripKm: Double? = null
        var rating: Double? = null
        var tripsCount: Int? = null
        var orderAgeSec: Int? = null
        var paymentMethod = PaymentMethod.UNKNOWN
        var stopsCount = 1
        var passengerNote: String? = null
        var pickupAddress: String? = null
        var dropoffAddress: String? = null

        val lines = rawText.split("\n", " • ", " | ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 1. Detect Payment Method
        val fullLower = rawText.lowercase()
        when {
            fullLower.contains("كاش") || fullLower.contains("نقدي") || fullLower.contains("cash") -> {
                paymentMethod = PaymentMethod.CASH
            }
            fullLower.contains("فيزا") || fullLower.contains("بطاقة") || fullLower.contains("card") || fullLower.contains("visa") -> {
                paymentMethod = PaymentMethod.ELECTRONIC
            }
            fullLower.contains("محفظة") || fullLower.contains("wallet") -> {
                paymentMethod = PaymentMethod.WALLET
            }
        }

        // 2. Detect Stops
        if (fullLower.contains("محطتين") || fullLower.contains("محطتان") || fullLower.contains("وقفتين")) {
            stopsCount = 2
        } else if (fullLower.contains("محطة إضافية") || fullLower.contains("stop")) {
            stopsCount = 2
        } else if (fullLower.contains("3 محطات") || fullLower.contains("3 stops")) {
            stopsCount = 3
        }

        // 3. Extract Distances
        val foundDistances = mutableListOf<Double>()
        for (line in lines) {
            val kmMatcher = distanceKmRegex.matcher(line)
            while (kmMatcher.find()) {
                val distStr = kmMatcher.group(1)?.replace(",", ".")
                distStr?.toDoubleOrNull()?.let { foundDistances.add(it) }
            }

            val mMatcher = distanceMeterRegex.matcher(line)
            while (mMatcher.find()) {
                val mStr = mMatcher.group(1)
                mStr?.toDoubleOrNull()?.let { foundDistances.add(it / 1000.0) }
            }
        }

        // إن درايف: الرقم اللي بعلامة ~ جنب السعر هو مسافة الاستلام (مش الرحلة).
        // مسافة الرحلة بتيجي من الفقاعة الخضرا على الخريطة (بنقراها من لون الـ OCR).
        if (app == SupportedApp.INDRIVER) {
            val tildeValues = mutableListOf<Double>()
            for (line in lines) {
                if (TILDE_CHARS.any { line.contains(it) }) tildeValues.addAll(extractLineDistances(line))
            }
            val resolved = resolveInDriveDistances(tildeValues, foundDistances)
            pickupKm = resolved.first
            tripKm = resolved.second
        } else if (foundDistances.isNotEmpty()) {
            // Distribute distances (usually first is pickup, second is trip)
            if (foundDistances.size == 1) {
                val d = foundDistances[0]
                if (d < 4.0) {
                    pickupKm = d
                } else {
                    tripKm = d
                }
            } else {
                pickupKm = foundDistances[0]
                tripKm = foundDistances[1]
            }
        }

        // 4. Extract Fare
        for (line in lines) {
            if (line.contains("ج.م") || line.contains("جنيه") || line.contains("£") || line.contains("EGP") || line.contains("LE") || line.contains("SAR") || line.contains("ر.س")) {
                val fareMatcher = fareRegex.matcher(line)
                if (fareMatcher.find()) {
                    val str = fareMatcher.group(1)?.replace(",", ".")
                    val num = str?.toDoubleOrNull()
                    if (num != null && num > 10.0 && num < 5000.0) {
                        fare = num
                        break
                    }
                }
            }
        }

        // Fallback for fare if no currency symbol attached
        if (fare == null) {
            for (line in lines) {
                val trimmed = line.replace(" ", "")
                if (trimmed.matches(Regex("""^[0-9]+(?:[.,][0-9]{1,2})?$"""))) {
                    val num = trimmed.replace(",", ".").toDoubleOrNull()
                    if (num != null && num in 20.0..2500.0) {
                        fare = num
                        break
                    }
                }
            }
        }

        // 5. Extract Passenger Rating
        for (line in lines) {
            val rMatcher = ratingRegex.matcher(line)
            if (rMatcher.find()) {
                val rVal = rMatcher.group(1)?.toDoubleOrNull()
                if (rVal != null && rVal in 3.0..5.0) {
                    rating = rVal
                    break
                }
            }
        }

        // 6. Extract Passenger Trips
        for (line in lines) {
            val tMatcher = tripsRegex.matcher(line)
            if (tMatcher.find()) {
                tripsCount = tMatcher.group(1)?.toIntOrNull()
                break
            }
        }

        // 7. Extract Order Age
        for (line in lines) {
            val ageMatcher = orderAgeRegex.matcher(line)
            if (ageMatcher.find()) {
                orderAgeSec = ageMatcher.group(1)?.toIntOrNull()
                break
            }
        }

        // 8. Extract Notes & Addresses with prefix detection and UI filter
        val nonAddressKeywords = listOf(
            "ج.م", "جنيه", "جنيها", "egp", "le", "sar", "aed", "usd",
            "كم", "كيلومتر", "km", "متر",
            "★", "⭐", "رحلة", "مشوار", "trips", "rides",
            "اقترح أجرتك", "القبول مقابل", "قبول", "رفض", "تخطي", "مفاوضة", "تأكيد", "إلغاء",
            "accept", "decline", "offer", "skip", "cancel", "confirm",
            "أوبر", "ديدي", "إن درايف", "uber", "didi", "indrive", "uberx", "comfort", "select", "city",
            "طلب جديد", "طلب ركوب", "طلب أوبر", "سيدان", "سكوتر", "موتوسيكل", "توصيل",
            "كاش", "نقدي", "فيزا", "بطاقة", "محفظة", "cash", "card", "visa", "wallet",
            "دقيقة", "دقائق", "ثانية", "ثوان", "min", "sec", "منذ"
        )

        for (line in lines) {
            val lower = line.lowercase()
            when {
                lower.startsWith("من:") || lower.startsWith("الاستلام:") || lower.startsWith("pickup:") || lower.startsWith("from:") -> {
                    pickupAddress = line.substringAfter(':').trim()
                }
                lower.startsWith("إلى:") || lower.startsWith("الوصول:") || lower.startsWith("الوجهة:") || lower.startsWith("dropoff:") || lower.startsWith("to:") || lower.startsWith("destination:") -> {
                    dropoffAddress = line.substringAfter(':').trim()
                }
                lower.startsWith("ملاحظة:") || lower.startsWith("note:") ||
                lower.contains("تكييف") || lower.contains("سجاير") || lower.contains("دخان") ||
                lower.contains("شنط") || lower.contains("حقائب") -> {
                    passengerNote = line
                }
            }
        }

        if (pickupAddress == null || dropoffAddress == null) {
            val addressLines = lines.filter { line ->
                val lower = line.lowercase().trim()
                line.length > 3 &&
                !lower.matches(Regex("""^[0-9.,~≈\s]+$""")) &&
                nonAddressKeywords.none { kw -> lower.contains(kw) }
            }
            if (pickupAddress == null && dropoffAddress == null) {
                if (addressLines.size >= 2) {
                    pickupAddress = addressLines[0]
                    dropoffAddress = addressLines[1]
                } else if (addressLines.isNotEmpty()) {
                    dropoffAddress = addressLines[0]
                }
            } else if (pickupAddress == null && addressLines.isNotEmpty()) {
                pickupAddress = addressLines.firstOrNull { it != dropoffAddress }
            } else if (dropoffAddress == null && addressLines.isNotEmpty()) {
                dropoffAddress = addressLines.firstOrNull { it != pickupAddress }
            }
        }

        return OrderDetails(
            app = app,
            rawText = rawText,
            fare = fare,
            pickupDistanceKm = pickupKm,
            tripDistanceKm = tripKm,
            passengerRating = rating,
            passengerTripsCount = tripsCount,
            orderAgeSeconds = orderAgeSec,
            paymentMethod = paymentMethod,
            stopsCount = stopsCount,
            pickupAddress = pickupAddress,
            dropoffAddress = dropoffAddress,
            passengerNote = passengerNote,
            isListCard = isListCard
            // No coordinates: the ride apps don't expose them as text, so we never invent any.
        )
    }

    companion object {
        private val TILDE_CHARS = listOf("~", "≈", "\u223C", "\u2053", "\uFF5E")
        private val OFFER_MARKERS = listOf("اقترح أجرتك", "القبول مقابل", "Offer your fare", "Accept for")
    }
}
