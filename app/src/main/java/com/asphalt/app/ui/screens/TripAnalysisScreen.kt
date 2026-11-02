package com.asphalt.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asphalt.app.engine.AsphaltJudgmentEngine
import com.asphalt.app.model.*
import com.asphalt.app.ui.components.*
import com.asphalt.app.ui.theme.*

@Composable
fun TripAnalysisScreen(
    driverRules: DriverRules,
    latestJudgment: JudgmentResult?
) {
    var fareInput by remember { mutableStateOf(latestJudgment?.grossFare?.toInt()?.toString() ?: "") }
    var pickupInput by remember { mutableStateOf(latestJudgment?.order?.pickupDistanceKm?.toString() ?: "") }
    var tripInput by remember { mutableStateOf(latestJudgment?.order?.tripDistanceKm?.toString() ?: "") }
    var selectedApp by remember { mutableStateOf(latestJudgment?.order?.app ?: SupportedApp.INDRIVER) }
    var ratingInput by remember { mutableStateOf(latestJudgment?.order?.passengerRating?.toString() ?: "") }

    val judgmentEngine = remember { AsphaltJudgmentEngine() }
    var currentAnalysis by remember { mutableStateOf<JudgmentResult?>(latestJudgment) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun calculate() {
        val fare = fareInput.replace(',', '.').toDoubleOrNull() ?: 0.0
        val pickup = pickupInput.replace(',', '.').toDoubleOrNull() ?: 0.0
        val trip = tripInput.replace(',', '.').toDoubleOrNull() ?: 0.0
        val rating = ratingInput.replace(',', '.').toDoubleOrNull()

        if (fare <= 0.0 || (pickup <= 0.0 && trip <= 0.0)) {
            errorMessage = "اكتب السعر ومسافة واحدة على الأقل (الاستلام أو الرحلة) عشان أحسب لك."
            return
        }

        val order = OrderDetails(
            app = selectedApp,
            fare = fare,
            pickupDistanceKm = pickup,
            tripDistanceKm = trip,
            passengerRating = rating,
            stopsCount = 1
        )
        currentAnalysis = judgmentEngine.evaluate(order, driverRules)
        errorMessage = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "التحليل التفصيلي للرحلة",
            subtitle = "تشريح كامل للمسافات وعمولة التطبيق وتكلفة البنزين وصافي الأرباح."
        )

        // Input Card
        IosCard {
            Text(
                text = "بيانات المشوار المراد تحليله:",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            // App Selector
            IosSegmentedControl(
                options = listOf("إن درايف", "ديدي", "أوبر"),
                selectedIndex = when (selectedApp) {
                    SupportedApp.INDRIVER -> 0
                    SupportedApp.DIDI -> 1
                    SupportedApp.UBER -> 2
                },
                onSelectIndex = { index ->
                    selectedApp = when (index) {
                        0 -> SupportedApp.INDRIVER
                        1 -> SupportedApp.DIDI
                        else -> SupportedApp.UBER
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = fareInput,
                    onValueChange = { fareInput = it },
                    label = { Text("السعر (ج.م)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = pickupInput,
                    onValueChange = { pickupInput = it },
                    label = { Text("الاستلام (كم)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = tripInput,
                    onValueChange = { tripInput = it },
                    label = { Text("الرحلة (كم)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ratingInput,
                onValueChange = { ratingInput = it },
                label = { Text("تقييم الراكب ★ (اختياري)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            IosPrimaryButton(
                text = "احسب التحليل التفصيلي الآن",
                icon = Icons.Default.Analytics,
                onClick = { calculate() },
                testTag = "run_analysis_button"
            )
        }

        errorMessage?.let { msg ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = VerdictRejectBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = VerdictReject)
                    Text(text = msg, fontSize = 12.sp, color = VerdictReject, lineHeight = 16.sp)
                }
            }
        }

        // Analysis Results Card
        currentAnalysis?.let { analysis ->
            val verdictColor = analysis.verdict.primaryColor()
            val verdictBg = analysis.verdict.bgColor()

            IosCard(
                borderColor = verdictColor.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "الحكم النهائي للطلب:",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(verdictBg)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = analysis.verdict.labelArabic,
                            color = verdictColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Net Profit Hero
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if ((analysis.netProfit ?: 0.0) > 0) VerdictAcceptBg else VerdictRejectBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "صافي المكسب الفعلي الذي يدخل جيبك:", fontSize = 13.sp, color = AppTextSecondary)
                        Text(
                            text = "${String.format("%.1f", analysis.netProfit ?: 0.0)} ج.م",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = if ((analysis.netProfit ?: 0.0) > 0) VerdictAccept else VerdictReject,
                            fontFamily = InterFont
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Breakdown list
                AnalysisBreakdownRow(
                    title = "الأجرة المعروضة للطلب",
                    amount = "+${analysis.grossFare?.toInt()} ج.م",
                    color = AppTextPrimary
                )
                AnalysisBreakdownRow(
                    title = "عمولة تطبيق ${analysis.order.app.titleArabic}",
                    amount = "-${String.format("%.1f", analysis.commissionDeduction)} ج.م",
                    color = VerdictReject
                )
                AnalysisBreakdownRow(
                    title = "تقدير استهلاك البنزين (${driverRules.engineSize.labelArabic.take(12)})",
                    amount = "-${String.format("%.1f", analysis.fuelCostEstimate)} ج.م",
                    color = VerdictConsider
                )
                AnalysisBreakdownRow(
                    title = "صيانة المسافة (زيت، فرامل، كاوتش)",
                    amount = "-${String.format("%.1f", analysis.maintenanceEstimate)} ج.م",
                    color = VerdictWait
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                AnalysisBreakdownRow(
                    title = "سعر الكيلو الصافي الفعلي",
                    amount = "${String.format("%.2f", analysis.ratePerKm ?: 0.0)} ج.م/كم",
                    color = AppPrimary
                )

                if (analysis.reasons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "تفاصيل الملاحظات والأسباب:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    analysis.reasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(verdictColor, CircleShape))
                            Text(text = reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                analysis.counterOfferSuggestedFare?.let { counterOffer ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = VerdictConsiderBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, VerdictConsider),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "اقتراح التفاوض العادل لك:", fontSize = 13.sp, color = VerdictConsider, fontWeight = FontWeight.SemiBold)
                            Text(text = "اطلب ${counterOffer.toInt()} ج.م", fontSize = 15.sp, color = VerdictConsider, fontWeight = FontWeight.Bold, fontFamily = InterFont)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun AnalysisBreakdownRow(title: String, amount: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(text = amount, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = InterFont)
    }
}
