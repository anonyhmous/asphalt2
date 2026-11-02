package com.asphalt.app.model

/**
 * Car engine categories for realistic fuel consumption estimation:
 * - Up to 1300 cc
 * - 1400 - 1600 cc
 * - 1800 - 2000 cc
 * - Greater than 2000 cc
 */
enum class EngineSize(
    val labelArabic: String,
    val avgLitersPer100Km: Double,
    val isMotorcycle: Boolean = false,
    val recommendedMaintenanceCostPerKm: Double = 1.0,
    val ccDisplayName: String = ""
) {
    // دراجات نارية وسكوتر (Motorcycles / Scooters)
    MOTO_150("سكوتر / موتوسيكل 150cc", 2.4, isMotorcycle = true, recommendedMaintenanceCostPerKm = 0.35, ccDisplayName = "150cc"),
    MOTO_200("سكوتر / موتوسيكل 200cc", 2.8, isMotorcycle = true, recommendedMaintenanceCostPerKm = 0.45, ccDisplayName = "200cc"),
    MOTO_250("سكوتر / موتوسيكل 250cc", 3.4, isMotorcycle = true, recommendedMaintenanceCostPerKm = 0.55, ccDisplayName = "250cc"),

    // سيارات (Cars)
    UP_TO_1300("سيارة حتى 1300cc (اقتصادي)", 5.5, isMotorcycle = false, recommendedMaintenanceCostPerKm = 1.0, ccDisplayName = "حتى 1300cc"),
    CC_1400_TO_1600("سيارة 1400 - 1600cc (متوسط)", 7.2, isMotorcycle = false, recommendedMaintenanceCostPerKm = 1.25, ccDisplayName = "1400-1600cc"),
    CC_1800_TO_2000("سيارة 1800 - 2000cc (مرتفع)", 9.2, isMotorcycle = false, recommendedMaintenanceCostPerKm = 1.5, ccDisplayName = "1800-2000cc"),
    ABOVE_2000("سيارة أكبر من 2000cc (كبير)", 11.5, isMotorcycle = false, recommendedMaintenanceCostPerKm = 2.0, ccDisplayName = "+2000cc")
}

enum class PaymentMethod(val labelArabic: String) {
    CASH("نقدي (كاش)"),
    ELECTRONIC("بطاقة / فيزا"),
    WALLET("محفظة إلكترونية"),
    UNKNOWN("غير محدد")
}

enum class DistanceUnit(val labelArabic: String, val shortLabel: String) {
    KILOMETER("كيلومتر", "كم"),
    MILE("ميل", "ميل")
}

enum class CalcRateType(val labelArabic: String) {
    GROSS("قبل عمولة التطبيق"),
    NET("صافي بعد العمولة والمصاريف")
}

/** Selects which real-world distance is used for price-per-kilometre evaluation. */
enum class DistanceCalculationMode(val labelArabic: String) {
    TRIP_ONLY("مسافة الرحلة فقط"),
    FULL_DISTANCE("المسافة الكاملة: الاستلام + الرحلة")
}

/**
 * The 5 standard verdict colors and statuses:
 * - GREEN: Accept it (اقبله)
 * - LIGHT_GREEN: Passed visible info, check route (عدّى اللي باين افتحه وشوف المشوار)
 * - ORANGE: Think first (فكّر الأول - فاوض بكام)
 * - RED: Skip it (سيبه)
 * - GRAY: Wait for details / incomplete info (استنى التفاصيل)
 */
enum class VerdictType(
    val labelArabic: String,
    val shortBadge: String
) {
    ACCEPT(labelArabic = "اقبله", shortBadge = "قبول مميز"),
    CHECK_DETAILS(labelArabic = "عدّى اللي باين، افتحه وشوف المشوار", shortBadge = "راجع الخريطة"),
    CONSIDER(labelArabic = "فكّر الأول", shortBadge = "تفاوض"),
    REJECT(labelArabic = "سيبه", shortBadge = "تخطي"),
    WAIT_DETAILS(labelArabic = "استنى التفاصيل", shortBadge = "بيانات ناقصة")
}
