package com.asphalt.app.engine

import com.asphalt.app.model.*
import kotlin.math.*

/**
 * Core mathematical and rule-based evaluation engine for ASPHALT V5.
 * All computations are driver-specific and executed strictly on-device.
 */
class AsphaltJudgmentEngine {

    fun evaluate(
        order: OrderDetails,
        rules: DriverRules,
        avoidedZoneNames: List<String> = emptyList(),
        preferredZoneNames: List<String> = emptyList()
    ): JudgmentResult {
        val unparsedFields = mutableListOf<String>()

        if (order.fare == null) unparsedFields.add("السعر غير واضح")
        if (order.pickupDistanceKm == null) unparsedFields.add("مسافة الاستلام غير واضحة")
        if (order.tripDistanceKm == null) unparsedFields.add("مسافة الرحلة غير واضحة")

        // Never evaluate a full-distance offer unless BOTH pickup and ride legs are actually read.
        // Trip-only mode requires the ride leg; missing values must not be silently treated as zero.
        val requiredDistanceMissing = when (rules.distanceCalculationMode) {
            DistanceCalculationMode.TRIP_ONLY -> order.tripDistanceKm == null
            DistanceCalculationMode.FULL_DISTANCE -> order.pickupDistanceKm == null || order.tripDistanceKm == null
        }
        if (order.fare == null || requiredDistanceMissing) {
            return JudgmentResult(
                verdict = VerdictType.WAIT_DETAILS,
                order = order,
                grossFare = order.fare,
                netProfit = null,
                totalDistanceKm = null,
                effectivePickupKm = 0.0,
                ratePerKm = null,
                commissionDeduction = 0.0,
                fuelCostEstimate = 0.0,
                maintenanceEstimate = 0.0,
                unparsedFields = unparsedFields,
                reasons = listOf("في انتظار تحميل الخريطة والأرقام من تطبيق ${order.app.titleArabic}")
            )
        }

        val fare = order.fare
        val rawPickup = order.pickupDistanceKm ?: 0.0
        val tripKm = order.tripDistanceKm ?: 0.0

        // Pickup threshold calculation:
        // "الاستلام محسوب كله لو وصل لحد يختاره السائق، ولو أقل منه مش بيتحسب"
        val effectivePickupKm = if (rawPickup >= rules.pickupThresholdKm) {
            rawPickup
        } else {
            0.0 // absorbed / does not penalize distance
        }

        val totalEffectiveDistance = when (rules.distanceCalculationMode) {
            DistanceCalculationMode.TRIP_ONLY -> tripKm
            // Full-distance mode deliberately includes every pickup kilometre; it must not
            // silently discard a short pickup leg based on the legacy threshold.
            DistanceCalculationMode.FULL_DISTANCE -> rawPickup + tripKm
        }
        val actualTotalDistance = rawPickup + tripKm

        // Commission calculation based on app
        val commissionRatePct = when (order.app) {
            SupportedApp.UBER -> rules.uberCommissionPct
            SupportedApp.DIDI -> rules.didiCommissionPct
            SupportedApp.INDRIVER -> rules.indriveCommissionPct
        }
        val commissionAmount = fare * (commissionRatePct / 100.0)

        // Fuel and maintenance calculations based on EngineSize (Car vs Motorcycle 150cc/200cc/250cc)
        val litersPerKm = rules.engineSize.avgLitersPer100Km / 100.0
        val fuelCost = totalEffectiveDistance * litersPerKm * rules.fuelPricePerLiter
        val maintenanceCost = totalEffectiveDistance * rules.maintenanceCostPerKm

        // Net profit left in driver's pocket after operational expenses and app commission:
        val netProfit = fare - (commissionAmount + fuelCost + maintenanceCost)

        // Rate per km (Gross or Net according to driver preference)
        val grossRatePerKm = if (totalEffectiveDistance > 0.1) fare / totalEffectiveDistance else fare
        val netRatePerKm = if (totalEffectiveDistance > 0.1) netProfit / totalEffectiveDistance else netProfit

        val evaluatedRate = if (rules.calcRateType == CalcRateType.NET) netRatePerKm else grossRatePerKm

        val reasons = mutableListOf<String>()
        var isHardRejection = false
        var isSoftWarning = false

        // Check 1: Minimum total fare rule
        if (fare < rules.minFare) {
            reasons.add("السعر ${String.format("%.0f", fare)} ج.م أقل من حدك الأدنى (${String.format("%.0f", rules.minFare)} ج.م)")
            isHardRejection = true
        }

        // Check 2: Minimum rate per km
        if (evaluatedRate < rules.minRatePerKm) {
            val typeStr = if (rules.calcRateType == CalcRateType.NET) "صافي" else "إجمالي"
            reasons.add("سعر الكيلو $typeStr ${String.format("%.1f", evaluatedRate)} ج.م أقل من المطلوب (${String.format("%.1f", rules.minRatePerKm)} ج.م/كم)")
            isHardRejection = true
        }

        // Check 3: Pickup distance threshold
        if (rawPickup > rules.maxPickupDistanceKm) {
            reasons.add("الاستلام بعيد (${String.format("%.1f", rawPickup)} كم) عن أقصى حد مسموح (${rules.maxPickupDistanceKm} كم)")
            isHardRejection = true
        }

        // Check 4: Passenger rating
        if (order.passengerRating != null && order.passengerRating < rules.minPassengerRating) {
            reasons.add("تقييم الراكب ضعيف (${String.format("%.2f", order.passengerRating)} ★) - أقل من ${rules.minPassengerRating}")
            isSoftWarning = true
        }

        // Check 5: Passenger trip count
        if (order.passengerTripsCount != null && order.passengerTripsCount < rules.minPassengerTrips) {
            reasons.add("عدد رحلات الراكب قليل (${order.passengerTripsCount} رحلة) - حساب جديد")
            isSoftWarning = true
        }

        // Check 6: Order age
        if (order.orderAgeSeconds != null && order.orderAgeSeconds > rules.maxOrderAgeSeconds) {
            reasons.add("طلب قديم متأخر (${order.orderAgeSeconds} ثانية) - غالباً لف على كباتن كتير")
            isSoftWarning = true
        }

        // Check 7: Payment method
        if (order.paymentMethod != PaymentMethod.UNKNOWN && !rules.allowedPaymentMethods.contains(order.paymentMethod)) {
            reasons.add("طريقة الدفع (${order.paymentMethod.labelArabic}) غير مفضلة لديك")
            isHardRejection = true
        }

        // Check 8: Multiple stops
        if (order.stopsCount > rules.maxStopsCount) {
            reasons.add("أكتر من وجهة (${order.stopsCount} محطات)")
            isSoftWarning = true
        }

        // Check 9: Forbidden keywords in passenger note
        if (!order.passengerNote.isNullOrBlank()) {
            val noteLower = order.passengerNote.lowercase()
            for (kw in rules.forbiddenKeywords) {
                if (noteLower.contains(kw.lowercase())) {
                    reasons.add("ملاحظة الراكب تحتوي كلمة محظورة: \"$kw\"")
                    isHardRejection = true
                    break
                }
            }
        }

        // Check 10: Avoided / Preferred zones check
        val combinedText = "${order.pickupAddress.orEmpty()} ${order.dropoffAddress.orEmpty()}"
        for (avoided in avoidedZoneNames) {
            if (combinedText.contains(avoided, ignoreCase = true)) {
                reasons.add("منطقة متجنّبة: $avoided (زحمة / طريق صعب)")
                isHardRejection = true
            }
        }

        // Direction / Going Home / Destination calculation
        var directionAdvancementPct: Int? = null
        // Only computed when real coordinates are known for both pickup and drop-off (never invented).
        if ((rules.isGoingHomeMode || rules.isGoingDestMode) && order.dropoffLat != null && order.dropoffLng != null &&
            order.pickupLat != null && order.pickupLng != null) {
            val targetLat = if (rules.isGoingHomeMode) rules.homeLat else rules.destLat
            val targetLng = if (rules.isGoingHomeMode) rules.homeLng else rules.destLng
            if (targetLat != null && targetLng != null) {
                directionAdvancementPct = computeStraightLineAdvancement(
                    pickupLat = order.pickupLat,
                    pickupLng = order.pickupLng,
                    dropoffLat = order.dropoffLat,
                    dropoffLng = order.dropoffLng,
                    targetLat = targetLat,
                    targetLng = targetLng
                )
            }
        }

        // Counter-offer suggestion for negotiation (e.g. for inDrive or orange verdict)
        val targetMinGrossFare = max(rules.minFare, rules.minRatePerKm * totalEffectiveDistance * (1 + (commissionRatePct / 100.0)))
        val counterOfferFare = if (fare < targetMinGrossFare) {
            // Round to neat 5 or 10 increments
            ceil(targetMinGrossFare / 5.0) * 5.0
        } else null

        // Determine Final Verdict
        val verdict: VerdictType
        val isOpportunity: Boolean

        when {
            // Hard rejection -> RED
            isHardRejection -> {
                verdict = VerdictType.REJECT
                isOpportunity = false
            }
            // Close to threshold or soft warnings -> ORANGE (Think first / Negotiate)
            isSoftWarning || (counterOfferFare != null && fare < targetMinGrossFare * 0.95) -> {
                verdict = VerdictType.CONSIDER
                isOpportunity = false
            }
            // Some non-critical detail needs check (e.g. trip distance not on map yet) -> LIGHT GREEN
            order.tripDistanceKm == null || unparsedFields.isNotEmpty() -> {
                verdict = VerdictType.CHECK_DETAILS
                isOpportunity = false
            }
            // Passed all driver rules with great margin -> GREEN (Accept it!)
            else -> {
                verdict = VerdictType.ACCEPT
                isOpportunity = true
            }
        }

        // إن درايف: أعلى سعر مسموح بالتفاوض = السعر × 1.45 (متأكد منه على 4 رحلات) ويتقرّب لتحت
        val negotiateMax = if (order.app == SupportedApp.INDRIVER) floor(fare * 1.45) else null

        // سرعة الرحلة من الدقايق والكيلومترات اللي على الفقاعة الخضرا
        val speedKmh = if (order.tripDistanceKm != null && order.tripMinutes != null && order.tripMinutes > 0) {
            order.tripDistanceKm / (order.tripMinutes / 60.0)
        } else null

        // السعر لكل كيلو بالاستلام والرحلة كاملين (زي الكارت اللي السواق اتعود عليه)
        val fullRate = if (order.pickupDistanceKm != null && order.tripDistanceKm != null && actualTotalDistance > 0.1) {
            fare / actualTotalDistance
        } else null

        return JudgmentResult(
            verdict = verdict,
            order = order,
            grossFare = fare,
            netProfit = netProfit,
            totalDistanceKm = totalEffectiveDistance,
            effectivePickupKm = effectivePickupKm,
            ratePerKm = evaluatedRate,
            commissionDeduction = commissionAmount,
            fuelCostEstimate = fuelCost,
            maintenanceEstimate = maintenanceCost,
            counterOfferSuggestedFare = counterOfferFare,
            directionAdvancementPct = directionAdvancementPct,
            reasons = reasons,
            unparsedFields = unparsedFields,
            isOpportunity = isOpportunity,
            negotiateMaxFare = negotiateMax,
            roadSpeedKmh = speedKmh,
            fullRatePerKm = fullRate
        )
    }

    /**
     * Straight-line direction angle computation indicating how much
     * the dropoff moves the driver closer toward home or destination.
     */
    private fun computeStraightLineAdvancement(
        pickupLat: Double, pickupLng: Double,
        dropoffLat: Double, dropoffLng: Double,
        targetLat: Double, targetLng: Double
    ): Int {
        val distBefore = distanceBetween(pickupLat, pickupLng, targetLat, targetLng)
        val distAfter = distanceBetween(dropoffLat, dropoffLng, targetLat, targetLng)
        if (distBefore <= 0.001) return 100
        val diff = distBefore - distAfter
        val pct = (diff / distBefore) * 100.0
        return pct.coerceIn(-100.0, 100.0).toInt()
    }

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return 6371.0 * c // Earth radius in km
    }
}
