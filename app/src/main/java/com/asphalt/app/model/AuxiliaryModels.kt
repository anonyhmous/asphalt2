package com.asphalt.app.model

enum class ZoneType(val labelArabic: String) {
    PREFERRED("مفضلة (حبيبي فيها الشغل)"),
    AVOIDED("متجنّبة (زحمة / طرق وعرة / بطيئة)")
}

data class ZoneItem(
    val id: Long = 0,
    val name: String,
    val governorate: String,
    val type: ZoneType,
    val notes: String = "",
    val radiusKm: Double = 3.0
)

data class ShiftSummary(
    val totalOrdersScanned: Int = 0,
    val acceptedOrdersCount: Int = 0,
    val totalEstimatedNetProfit: Double = 0.0,
    val avoidedCostSaved: Double = 0.0
)

data class DriverProfile(
    val firstName: String = "كابتن",
    val lastName: String = "",
    val phoneWithCountryCode: String = "",
    val governorate: String = "القاهرة",
    val country: String = "مصر",
    val currency: String = "ج.م"
) {
    val name: String get() = if (lastName.isNotBlank()) "$firstName $lastName" else firstName
    val phone: String get() = phoneWithCountryCode
}

data class ProblemReport(
    val id: Long = 0,
    val category: String,
    val description: String,
    val attachedMetricsJson: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSentToServer: Boolean = false
)
