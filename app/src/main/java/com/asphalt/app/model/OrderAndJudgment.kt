package com.asphalt.app.model

/**
 * Parsed order details extracted from the ride card (via Accessibility text nodes or fallback OCR).
 * Strictly read-only; ASPHALT V5 never interacts or touches any buttons.
 */
data class OrderDetails(
    val id: String = System.currentTimeMillis().toString(),
    val app: SupportedApp,
    val rawText: String = "",
    val fare: Double? = null,                       // السعر
    val pickupDistanceKm: Double? = null,           // مسافة الاستلام
    val tripDistanceKm: Double? = null,             // مسافة الرحلة
    val tripMinutes: Int? = null,                   // زمن الرحلة بالدقايق (من فقاعة الخريطة)
    val passengerRating: Double? = null,           // تقييم الراكب
    val passengerTripsCount: Int? = null,          // عدد رحلات الراكب
    val orderAgeSeconds: Int? = null,              // عمر الطلب بالثواني
    val paymentMethod: PaymentMethod = PaymentMethod.UNKNOWN, // طريقة الدفع
    val stopsCount: Int = 1,                       // عدد المحطات
    val pickupAddress: String? = null,             // عنوان الاستلام
    val dropoffAddress: String? = null,            // عنوان الوصول
    val passengerNote: String? = null,             // ملاحظة الراكب
    val isListCard: Boolean = false,               // علامة على كارت في القائمة (مثل إن درايف)
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
    val dropoffLat: Double? = null,
    val dropoffLng: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Driver's personalized preferences and rules.
 * All numbers are driver-specific (no generic market averages).
 */
data class DriverRules(
    // Minimum mandatory rule
    val minFare: Double = 35.0,                    // أقل سعر إجمالي للرحلة (ج.م)
    val minRatePerKm: Double = 8.0,                // أقل سعر للكيلو
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETER,
    val maxPickupDistanceKm: Double = 3.5,         // أقصى مسافة استلام مقبولة
    val pickupThresholdKm: Double = 1.5,           // حد الاستلام: إذا زاد عنه يحسب كاملاً، وإذا أقل لا يحسب أو جزئي
    val calcRateType: CalcRateType = CalcRateType.NET, // قبل العمولة أو صافي بعدها
    val distanceCalculationMode: DistanceCalculationMode = DistanceCalculationMode.FULL_DISTANCE, // الرحلة فقط أو الاستلام + الرحلة
    
    // Vehicle & Running Costs
    val fuelPricePerLiter: Double = 15.0,          // سعر لتر البنزين (ج.م)
    val engineSize: EngineSize = EngineSize.CC_1400_TO_1600, // سعة الموتور لحساب الاستهلاك
    val maintenanceCostPerKm: Double = 1.25,       // صيانة الكيلو (زيت، كاوتش، فرامل)
    
    // Quality & Passenger Rules
    val minPassengerRating: Double = 4.60,         // أقل تقييم للراكب
    val minPassengerTrips: Int = 5,                // أقل عدد رحلات للراكب
    val maxOrderAgeSeconds: Int = 45,              // أقصى عمر للطلب
    val allowedPaymentMethods: Set<PaymentMethod> = setOf(
        PaymentMethod.CASH,
        PaymentMethod.ELECTRONIC,
        PaymentMethod.WALLET
    ),
    val maxStopsCount: Int = 1,                    // أقصى عدد محطات (أكثر من وجهة)
    val forbiddenKeywords: List<String> = listOf(
        "سجاير", "دخان", "سلفني", "استنى شوية", "تأخير", "فلوس بره", "بدون تكييف"
    ),

    // App-specific individual commissions
    val uberCommissionPct: Double = 25.0,
    val didiCommissionPct: Double = 22.0,
    val indriveCommissionPct: Double = 12.0,

    // Navigation & Direction Modes
    val isGoingHomeMode: Boolean = false,          // أنا مروّح دلوقتي
    val homeAddress: String = "المعادي - القاهرة",
    val homeLat: Double? = 29.9602,
    val homeLng: Double? = 31.2569,

    val isGoingDestMode: Boolean = false,          // رايح هناك دلوقتي
    val destAddress: String = "التجمع الخامس - القاهرة الجديدة",
    val destLat: Double? = 30.0074,
    val destLng: Double? = 31.4284,

    // Overlay visual preferences
    val overlayOpacity: Float = 0.95f,             // شفافية شريط الحكم
    val overlayScale: Float = 1.0f,                // حجم شريط الحكم
    val autoStopHours: Int = 3,                    // إغلاق القارئ تلقائياً عند عدم الاستخدام (1، 3، 6 أو 0 لإلغاء)
    val chimeSoundOnly: Boolean = false            // صوت فقط أو صوت + اهتزاز
)

/**
 * Result of the ASPHALT V5 Judgment Engine evaluation.
 */
data class JudgmentResult(
    val verdict: VerdictType,
    val order: OrderDetails,
    val grossFare: Double?,
    val netProfit: Double?,                        // الرحلة هتسيب كام بعد المصاريف
    val totalDistanceKm: Double?,
    val effectivePickupKm: Double,
    val ratePerKm: Double?,                        // السعر لكل كم (إجمالي أو صافي حسب الاختيار)
    val commissionDeduction: Double,               // عمولة التطبيق بالجنيه
    val fuelCostEstimate: Double,                  // تكلفة البنزين
    val maintenanceEstimate: Double,               // تكلفة الصيانة
    val counterOfferSuggestedFare: Double? = null, // اقتراح تفاوض في حالة "فكّر الأول"
    val directionAdvancementPct: Int? = null,      // نسبة التقريب لوجهة السائق (مروّح / رايح هناك)
    val reasons: List<String> = emptyList(),        // أسباب الرفض أو التنبيه
    val unparsedFields: List<String> = emptyList(), // الحقول التي لم تقرأ بوضوح (دون تخمين)
    val isOpportunity: Boolean = false,            // هل تنطبق عليه كل الشروط لفرصة مميزة وتنبيه صوتي
    val negotiateMaxFare: Double? = null,          // إن درايف: أعلى سعر ينفع تتفاوض بيه (السعر × 1.45)
    val roadSpeedKmh: Double? = null,              // متوسط سرعة الرحلة (كم/س) لمؤشر الطريق
    val fullRatePerKm: Double? = null              // السعر ÷ (استلام + رحلة كاملين)
)
