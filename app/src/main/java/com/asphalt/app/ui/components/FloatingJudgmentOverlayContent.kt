package com.asphalt.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asphalt.app.model.JudgmentResult
import com.asphalt.app.model.VerdictType
import com.asphalt.app.ui.theme.primaryColor

private val CardLight = Color(0xFFE9EDF4)
private val TileLight = Color(0xFFF7F8FC)
private val InkDark = Color(0xFF1A2233)
private val LabelGray = Color(0xFF55607A)
private val GoodGreen = Color(0xFF168A4A)
private val WarnOrange = Color(0xFFB85C0A)
private val BadRed = Color(0xFFC63A3A)

/** الكلام اللي على الشريط الكبير: إن درايف بيعرض أعلى سعر تفاوض، وغيره بيعرض الحكم. */
private fun bannerTextFor(j: JudgmentResult): String {
    val max = j.negotiateMaxFare
    return when {
        j.verdict == VerdictType.REJECT -> j.verdict.labelArabic
        j.verdict == VerdictType.WAIT_DETAILS -> j.verdict.labelArabic
        max != null -> "فاوض بـ${max.toInt()} ج"
        else -> j.verdict.labelArabic
    }
}

private fun rateColor(v: VerdictType): Color = when (v) {
    VerdictType.ACCEPT -> GoodGreen
    VerdictType.REJECT -> BadRed
    else -> WarnOrange
}

/** سريع / متوسط / زحمة من سرعة الرحلة الفعلية. */
private fun roadLabel(speedKmh: Double?): Pair<String, Color> {
    if (speedKmh == null) return Pair("—", LabelGray)
    return when {
        speedKmh >= 35.0 -> Pair("سريع", GoodGreen)
        speedKmh >= 20.0 -> Pair("≈متوسط", WarnOrange)
        else -> Pair("زحمة", BadRed)
    }
}

@Composable
private fun StatTile(
    value: String,
    unit: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TileLight)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = value, color = valueColor, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
            if (unit.isNotEmpty()) {
                Text(text = unit, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Text(text = label, color = LabelGray, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

/**
 * كارت الحكم العايم: شريط كبير بالقرار، تلات مربعات بأرقام كبيرة، وسطر الصافي.
 * الخط كبير عشان يتقرا من بعيد وإنت سايق. علامة التحذير بتفتح التفاصيل، وعلامة X بتقفله.
 */
@Composable
fun FloatingJudgmentOverlayContent(
    judgment: JudgmentResult,
    isExpanded: Boolean,
    opacity: Float = 0.95f,
    scale: Float = 1.0f,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val verdictColor = judgment.verdict.primaryColor()
    val order = judgment.order
    val bothKnown = order.pickupDistanceKm != null && order.tripDistanceKm != null
    val rateText = judgment.fullRatePerKm?.let { String.format("%.2f", it) } ?: "—"
    val totalText = if (bothKnown && judgment.totalDistanceKm != null) {
        String.format("%.1f", judgment.totalDistanceKm)
    } else "—"
    val road = roadLabel(judgment.roadSpeedKmh)

    Column(
        modifier = modifier
            .scale(scale)
            .width(350.dp)
            .shadow(12.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(CardLight.copy(alpha = opacity))
            .border(2.dp, verdictColor.copy(alpha = 0.9f), RoundedCornerShape(22.dp))
            .animateContentSize()
            .padding(12.dp)
            .testTag("floating_overlay_root"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // الصف الأول: الشريط الكبير + تحذير + إغلاق
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(verdictColor)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bannerTextFor(judgment),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "تفاصيل",
                tint = if (isExpanded) WarnOrange else LabelGray,
                modifier = Modifier
                    .size(34.dp)
                    .clickable { onToggleExpand() }
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "إغلاق",
                tint = LabelGray,
                modifier = Modifier
                    .size(34.dp)
                    .clickable { onDismiss() }
            )
        }

        // التلات مربعات: سعر الكيلو، الإجمالي، الطريق
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatTile(
                value = rateText, unit = "", label = "ج للكيلو",
                valueColor = rateColor(judgment.verdict), modifier = Modifier.weight(1f)
            )
            StatTile(
                value = totalText, unit = if (totalText == "—") "" else "كم", label = "الإجمالي",
                valueColor = InkDark, modifier = Modifier.weight(1f)
            )
            StatTile(
                value = road.first, unit = "", label = "الطريق",
                valueColor = road.second, modifier = Modifier.weight(1f)
            )
        }

        // سطر الصافي بعد عمولة التطبيق بس
        val net = judgment.netProfit
        Text(
            text = if (net != null) "تفضلك ${net.toInt()} ج بعد عمولة التطبيق" else "في انتظار الأرقام",
            color = InkDark,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth()
        )

        if (isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val pickup = order.pickupDistanceKm?.let { String.format("%.1f", it) + " كم" } ?: "غير واضح"
                val trip = order.tripDistanceKm?.let { String.format("%.1f", it) + " كم" } ?: "غير واضح"
                Text(
                    text = "الاستلام: $pickup  •  الرحلة: $trip",
                    color = InkDark, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                )
                judgment.counterOfferSuggestedFare?.let {
                    Text(
                        text = "اقتراح التفاوض: ${it.toInt()} ج",
                        color = WarnOrange, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                    )
                }
                judgment.reasons.forEach { reason ->
                    Text(text = "• $reason", color = BadRed, fontSize = 15.sp)
                }
                if (judgment.unparsedFields.isNotEmpty()) {
                    Text(
                        text = "تنبيه: " + judgment.unparsedFields.joinToString("، "),
                        color = LabelGray, fontSize = 14.sp
                    )
                }
            }
        }
    }
}
