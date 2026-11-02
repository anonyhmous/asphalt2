package com.asphalt.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asphalt.app.model.*
import com.asphalt.app.ui.components.*
import com.asphalt.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    currentRules: DriverRules,
    onSaveRules: (DriverRules) -> Unit
) {
    var minFare by remember(currentRules) { mutableStateOf(currentRules.minFare.toString()) }
    var minRatePerKm by remember(currentRules) { mutableStateOf(currentRules.minRatePerKm.toString()) }
    var distanceUnit by remember(currentRules) { mutableStateOf(currentRules.distanceUnit) }
    var calcRateType by remember(currentRules) { mutableStateOf(currentRules.calcRateType) }
    var distanceCalculationMode by remember(currentRules) { mutableStateOf(currentRules.distanceCalculationMode) }
    var maxPickupKm by remember(currentRules) { mutableStateOf(currentRules.maxPickupDistanceKm.toString()) }
    var pickupThresholdKm by remember(currentRules) { mutableStateOf(currentRules.pickupThresholdKm.toString()) }

    var fuelPrice by remember(currentRules) { mutableStateOf(currentRules.fuelPricePerLiter.toString()) }
    var engineSize by remember(currentRules) { mutableStateOf(currentRules.engineSize) }
    var maintenancePerKm by remember(currentRules) { mutableStateOf(currentRules.maintenanceCostPerKm.toString()) }

    var uberCommission by remember(currentRules) { mutableStateOf(currentRules.uberCommissionPct.toString()) }
    var didiCommission by remember(currentRules) { mutableStateOf(currentRules.didiCommissionPct.toString()) }
    var indriveCommission by remember(currentRules) { mutableStateOf(currentRules.indriveCommissionPct.toString()) }

    var minRating by remember(currentRules) { mutableStateOf(currentRules.minPassengerRating.toString()) }
    var minTrips by remember(currentRules) { mutableStateOf(currentRules.minPassengerTrips.toString()) }
    var maxAgeSec by remember(currentRules) { mutableStateOf(currentRules.maxOrderAgeSeconds.toString()) }

    var overlayOpacity by remember(currentRules) { mutableStateOf(currentRules.overlayOpacity) }
    var overlayScale by remember(currentRules) { mutableStateOf(currentRules.overlayScale) }
    var autoStopHours by remember(currentRules) { mutableStateOf(currentRules.autoStopHours) }
    var chimeSoundOnly by remember(currentRules) { mutableStateOf(currentRules.chimeSoundOnly) }

    var forbiddenWordsText by remember(currentRules) {
        mutableStateOf(currentRules.forbiddenKeywords.joinToString("، "))
    }

    var showSaveSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "قواعدي ومعايير الحكم",
            subtitle = "أرقامك أنت فقط. كل حاجة اختيارية ما عدا أقل سعر."
        )

        // Rule 1: Mandatory Min Fare & Rate
        IosCard {
            Text(
                text = "الأجرة وسعر الكيلو (إجباري)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = minFare,
                onValueChange = { minFare = it },
                label = { Text("أقل سعر مقبول للرحلة (ج.م) *") },
                supportingText = { Text("أي رحلة أقل من السعر ده هيديك حكم \"سيبه\" فوراً") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("min_fare_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = minRatePerKm,
                onValueChange = { minRatePerKm = it },
                label = { Text("أقل سعر لكل كيلو (ج.م/كم)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("min_rate_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "طريقة احتساب الحد:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            IosSegmentedControl(
                options = listOf("صافي بعد العمولة والمصاريف", "قبل عمولة التطبيق"),
                selectedIndex = if (calcRateType == CalcRateType.NET) 0 else 1,
                onSelectIndex = { calcRateType = if (it == 0) CalcRateType.NET else CalcRateType.GROSS }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "وحدة المسافة:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            IosSegmentedControl(
                options = listOf("كيلومتر (كم)", "ميل (Mile)"),
                selectedIndex = if (distanceUnit == DistanceUnit.KILOMETER) 0 else 1,
                onSelectIndex = { distanceUnit = if (it == 0) DistanceUnit.KILOMETER else DistanceUnit.MILE }
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "المسافة المستخدمة في حساب سعر الكيلو:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            IosSegmentedControl(
                options = listOf("الرحلة فقط", "الاستلام + الرحلة"),
                selectedIndex = if (distanceCalculationMode == DistanceCalculationMode.TRIP_ONLY) 0 else 1,
                onSelectIndex = { distanceCalculationMode = if (it == 0) DistanceCalculationMode.TRIP_ONLY else DistanceCalculationMode.FULL_DISTANCE }
            )
            Text(
                text = if (distanceCalculationMode == DistanceCalculationMode.FULL_DISTANCE)
                    "سيتم احتساب المسافة من موقعك إلى الراكب ثم إلى الوجهة."
                else "سيتم احتساب مسافة الرحلة من الراكب إلى الوجهة فقط.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // Rule 2: Pickup rules
        IosCard {
            Text(
                text = "مسافة الاستلام وحد الاحتساب",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = pickupThresholdKm,
                onValueChange = { pickupThresholdKm = it },
                label = { Text("حد الاستلام المجاني/المتسامح فيه (كم)") },
                supportingText = { Text("لو الاستلام وصل للرقم ده أو عداه بيتحسب كله، ولو أقل منه مش بيتحسب") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pickup_threshold_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = maxPickupKm,
                onValueChange = { maxPickupKm = it },
                label = { Text("أقصى مسافة استلام مقبولة (كم)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("max_pickup_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        // Rule 3: Vehicle, Fuel, and Maintenance
        IosCard {
            Text(
                text = "سيارتك وتكلفة التشغيل (لحساب صافي الجيب)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "نوع المركبة وسعة الموتور (CC) لضبط حسابات الرحلة:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            // تبديل سريع بين سكوتر/موتوسيكل وسيارة
            var isMotorcycleMode by remember(engineSize) { mutableStateOf(engineSize.isMotorcycle) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            isMotorcycleMode = true
                            if (!engineSize.isMotorcycle) {
                                engineSize = EngineSize.MOTO_150
                                maintenancePerKm = "0.35"
                            }
                        }
                        .border(
                            width = if (isMotorcycleMode) 2.dp else 1.dp,
                            color = if (isMotorcycleMode) AppPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    color = if (isMotorcycleMode) AppPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TwoWheeler,
                            contentDescription = null,
                            tint = if (isMotorcycleMode) AppPrimary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "سكوتر / موتوسيكل",
                            fontSize = 13.sp,
                            fontWeight = if (isMotorcycleMode) FontWeight.Bold else FontWeight.Medium,
                            color = if (isMotorcycleMode) AppPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            isMotorcycleMode = false
                            if (engineSize.isMotorcycle) {
                                engineSize = EngineSize.CC_1400_TO_1600
                                maintenancePerKm = "1.25"
                            }
                        }
                        .border(
                            width = if (!isMotorcycleMode) 2.dp else 1.dp,
                            color = if (!isMotorcycleMode) AppPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    color = if (!isMotorcycleMode) AppPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = if (!isMotorcycleMode) AppPrimary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "سيارة ملاكي",
                            fontSize = 13.sp,
                            fontWeight = if (!isMotorcycleMode) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isMotorcycleMode) AppPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // خيارات السعة للموتوسيكل (150cc, 200cc, 250cc) بشكل مباشر وسريع
            if (isMotorcycleMode) {
                Text(
                    text = "اختر سعة موتور الموتوسيكل (CC):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        EngineSize.MOTO_150,
                        EngineSize.MOTO_200,
                        EngineSize.MOTO_250
                    ).forEach { size ->
                        val isSelected = engineSize == size
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    engineSize = size
                                    maintenancePerKm = size.recommendedMaintenanceCostPerKm.toString()
                                }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) AppPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            color = if (isSelected) AppPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = size.ccDisplayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AppPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "~${size.avgLitersPer100Km} لتر/100كم",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            } else {
                // خيارات سعة محرك السيارة
                var expandedEngine by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedEngine,
                    onExpandedChange = { expandedEngine = !expandedEngine }
                ) {
                    OutlinedTextField(
                        value = "${engineSize.labelArabic} (~${engineSize.avgLitersPer100Km} لتر/100كم)",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEngine) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedEngine,
                        onDismissRequest = { expandedEngine = false }
                    ) {
                        EngineSize.entries.filter { !it.isMotorcycle }.forEach { size ->
                            DropdownMenuItem(
                                text = { Text("${size.labelArabic} (~${size.avgLitersPer100Km} لتر/100كم)") },
                                onClick = {
                                    engineSize = size
                                    expandedEngine = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = fuelPrice,
                    onValueChange = { fuelPrice = it },
                    label = { Text("سعر لتر البنزين (ج.م)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = maintenancePerKm,
                    onValueChange = { maintenancePerKm = it },
                    label = { Text("صيانة/كم (زيت، كاوتش)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        }

        // Rule 4: App-Specific Individual Commissions
        IosCard {
            Text(
                text = "عمولة التطبيقات الخاصة",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "تُخصم من الأجرة أولاً قبل أي حساب",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uberCommission,
                    onValueChange = { uberCommission = it },
                    label = { Text("أوبر %") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = didiCommission,
                    onValueChange = { didiCommission = it },
                    label = { Text("ديدي %") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = indriveCommission,
                    onValueChange = { indriveCommission = it },
                    label = { Text("إن درايف %") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        }

        // Rule 5: Quality, Passenger rating, and forbidden note keywords
        IosCard {
            Text(
                text = "معايير الراكب وجودة الطلب",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = minRating,
                    onValueChange = { minRating = it },
                    label = { Text("أقل تقييم ★") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = minTrips,
                    onValueChange = { minTrips = it },
                    label = { Text("أقل رحلات") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = maxAgeSec,
                    onValueChange = { maxAgeSec = it },
                    label = { Text("أقصى ثواني") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = forbiddenWordsText,
                onValueChange = { forbiddenWordsText = it },
                label = { Text("كلمات ممنوعة في ملاحظة الراكب (مفصولة بفاصلة)") },
                supportingText = { Text("مثل: سجاير، دخان، سلفني، استنى شوية، فلوس بره") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Rule 6: Overlay customization and live preview
        IosCard {
            Text(
                text = "تخصيص شريط الحكم العايم",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "شفافية الخلفية: ${(overlayOpacity * 100).toInt()}%",
                fontSize = 13.sp
            )
            Slider(
                value = overlayOpacity,
                onValueChange = { overlayOpacity = it },
                valueRange = 0.5f..1.0f
            )

            Text(
                text = "حجم شريط الحكم: ${(overlayScale * 100).toInt()}%",
                fontSize = 13.sp
            )
            Slider(
                value = overlayScale,
                onValueChange = { overlayScale = it },
                valueRange = 0.8f..1.2f
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "معاينة حية لشكل شريط الحكم:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Embedded live preview of overlay
            val previewJudgment = remember {
                JudgmentResult(
                    verdict = VerdictType.ACCEPT,
                    order = OrderDetails(
                        app = SupportedApp.UBER,
                        fare = 95.0,
                        pickupDistanceKm = 1.2,
                        tripDistanceKm = 11.5,
                        passengerRating = 4.92,
                        pickupAddress = "الدقي",
                        dropoffAddress = "التجمع"
                    ),
                    grossFare = 95.0,
                    netProfit = 62.5,
                    totalDistanceKm = 12.7,
                    effectivePickupKm = 0.0,
                    ratePerKm = 8.2,
                    commissionDeduction = 23.75,
                    fuelCostEstimate = 6.8,
                    maintenanceEstimate = 1.95,
                    isOpportunity = true
                )
            }

            FloatingJudgmentOverlayContent(
                judgment = previewJudgment,
                isExpanded = true,
                opacity = overlayOpacity,
                scale = overlayScale,
                onToggleExpand = {},
                onDismiss = {}
            )
        }

        // Rule 7: Auto Stop & Chime settings
        IosCard {
            Text(
                text = "تنبيه الفرصة والإغلاق التلقائي",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "نغمة الفرصة: صوت فقط بدون اهتزاز",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "بيرن بنغمة مميزة تختلف عن رنة التطبيق لما الطلب يغطي سعره ومفيهوش مشاكل",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Switch(
                    checked = chimeSoundOnly,
                    onCheckedChange = { chimeSoundOnly = it }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "إغلاق القارئ تلقائياً عند عدم فتح تطبيقات السائق:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            IosSegmentedControl(
                options = listOf("ساعة", "3 ساعات", "6 ساعات", "بدون"),
                selectedIndex = when (autoStopHours) {
                    1 -> 0
                    3 -> 1
                    6 -> 2
                    else -> 3
                },
                onSelectIndex = { index ->
                    autoStopHours = when (index) {
                        0 -> 1
                        1 -> 3
                        2 -> 6
                        else -> 0
                    }
                }
            )
        }

        // Save Button
        IosPrimaryButton(
            text = "حفظ جميع القواعد",
            icon = Icons.Default.Save,
            onClick = {
                val keywords = forbiddenWordsText.split("،", ",", "\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val updated = currentRules.copy(
                    minFare = minFare.toDoubleOrNull() ?: currentRules.minFare,
                    minRatePerKm = minRatePerKm.toDoubleOrNull() ?: currentRules.minRatePerKm,
                    distanceUnit = distanceUnit,
                    calcRateType = calcRateType,
                    distanceCalculationMode = distanceCalculationMode,
                    maxPickupDistanceKm = maxPickupKm.toDoubleOrNull() ?: currentRules.maxPickupDistanceKm,
                    pickupThresholdKm = pickupThresholdKm.toDoubleOrNull() ?: currentRules.pickupThresholdKm,
                    fuelPricePerLiter = fuelPrice.toDoubleOrNull() ?: currentRules.fuelPricePerLiter,
                    engineSize = engineSize,
                    maintenanceCostPerKm = maintenancePerKm.toDoubleOrNull() ?: currentRules.maintenanceCostPerKm,
                    uberCommissionPct = uberCommission.toDoubleOrNull() ?: currentRules.uberCommissionPct,
                    didiCommissionPct = didiCommission.toDoubleOrNull() ?: currentRules.didiCommissionPct,
                    indriveCommissionPct = indriveCommission.toDoubleOrNull() ?: currentRules.indriveCommissionPct,
                    minPassengerRating = minRating.toDoubleOrNull() ?: currentRules.minPassengerRating,
                    minPassengerTrips = minTrips.toIntOrNull() ?: currentRules.minPassengerTrips,
                    maxOrderAgeSeconds = maxAgeSec.toIntOrNull() ?: currentRules.maxOrderAgeSeconds,
                    forbiddenKeywords = keywords,
                    overlayOpacity = overlayOpacity,
                    overlayScale = overlayScale,
                    autoStopHours = autoStopHours,
                    chimeSoundOnly = chimeSoundOnly
                )
                onSaveRules(updated)
                showSaveSuccess = true
            },
            testTag = "save_rules_button"
        )

        if (showSaveSuccess) {
            Text(
                text = "تم حفظ قواعدك بنجاح وسيبدأ تطبيقها على كل الطلبات القادمة ✓",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerdictAccept
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
