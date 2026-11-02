package com.asphalt.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.asphalt.app.model.PaymentMethod
import com.asphalt.app.model.SupportedApp
import com.asphalt.app.ui.theme.*

@Composable
fun SimulatorDialog(
    onDismiss: () -> Unit,
    onSimulate: (
        app: SupportedApp,
        fare: Double,
        pickupKm: Double,
        tripKm: Double,
        rating: Double,
        trips: Int,
        ageSec: Int,
        payment: PaymentMethod,
        stops: Int,
        pickupAddr: String,
        dropoffAddr: String,
        note: String?
    ) -> Unit
) {
    var selectedPreset by remember { mutableStateOf(0) }

    var app by remember { mutableStateOf(SupportedApp.UBER) }
    var fare by remember { mutableStateOf("110") }
    var pickupKm by remember { mutableStateOf("1.2") }
    var tripKm by remember { mutableStateOf("11.5") }
    var rating by remember { mutableStateOf("4.92") }
    var tripsCount by remember { mutableStateOf("48") }
    var ageSec by remember { mutableStateOf("15") }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var stopsCount by remember { mutableStateOf(1) }
    var pickupAddr by remember { mutableStateOf("المهندسين - شارع لبنان") }
    var dropoffAddr by remember { mutableStateOf("التجمع الخامس - شارع التسعين") }
    var passengerNote by remember { mutableStateOf("") }

    fun applyPreset(index: Int) {
        selectedPreset = index
        when (index) {
            0 -> { // Great trip (Green)
                app = SupportedApp.UBER
                fare = "110"
                pickupKm = "1.2"
                tripKm = "11.5"
                rating = "4.92"
                tripsCount = "52"
                ageSec = "10"
                paymentMethod = PaymentMethod.CASH
                stopsCount = 1
                pickupAddr = "المهندسين"
                dropoffAddr = "التجمع الخامس"
                passengerNote = ""
            }
            1 -> { // Check route (Light green)
                app = SupportedApp.DIDI
                fare = "75"
                pickupKm = "2.1"
                tripKm = "9.0"
                rating = "4.75"
                tripsCount = "20"
                ageSec = "20"
                paymentMethod = PaymentMethod.ELECTRONIC
                stopsCount = 1
                pickupAddr = "المعادي"
                dropoffAddr = "مدينة نصر"
                passengerNote = ""
            }
            2 -> { // Counter-offer / Negotiate (Orange)
                app = SupportedApp.INDRIVER
                fare = "45"
                pickupKm = "1.8"
                tripKm = "13.0"
                rating = "4.80"
                tripsCount = "33"
                ageSec = "14"
                paymentMethod = PaymentMethod.CASH
                stopsCount = 1
                pickupAddr = "الهرم"
                dropoffAddr = "مصر الجديدة"
                passengerNote = "محتاج تكييف عالي"
            }
            3 -> { // Reject / Skip it (Red)
                app = SupportedApp.UBER
                fare = "25"
                pickupKm = "4.2"
                tripKm = "2.5"
                rating = "4.15"
                tripsCount = "2"
                ageSec = "55"
                paymentMethod = PaymentMethod.CASH
                stopsCount = 2
                pickupAddr = "العجوزة"
                dropoffAddr = "وسط البلد"
                passengerNote = "سجاير في العربية"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = AppPrimary)
                Text("مُحاكي كروت الطلبات الحي", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "اختر سيناريو جاهز للاختبار الفوري:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Presets list
                val presetLabels = listOf(
                    "أوبر ممتاز (أخضر)",
                    "ديدي راجع الخريطة (أخضر فاتح)",
                    "إن درايف فاوض (برتقالي)",
                    "طلب خاسر سيبه (أحمر)"
                )

                presetLabels.forEachIndexed { idx, label ->
                    val isSelected = selectedPreset == idx
                    Surface(
                        onClick = { applyPreset(idx) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) AppPrimaryTint else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, AppPrimary) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AppPrimaryPressed else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                // Editable Fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = fare,
                        onValueChange = { fare = it },
                        label = { Text("الأجرة (ج.م)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pickupKm,
                        onValueChange = { pickupKm = it },
                        label = { Text("الاستلام (كم)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = tripKm,
                        onValueChange = { tripKm = it },
                        label = { Text("الرحلة (كم)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rating,
                        onValueChange = { rating = it },
                        label = { Text("التقييم ★") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = tripsCount,
                        onValueChange = { tripsCount = it },
                        label = { Text("الرحلات") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ageSec,
                        onValueChange = { ageSec = it },
                        label = { Text("العمر (ث)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = passengerNote,
                    onValueChange = { passengerNote = it },
                    label = { Text("ملاحظة الراكب (سجاير، تكييف، إلخ)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSimulate(
                        app,
                        fare.toDoubleOrNull() ?: 50.0,
                        pickupKm.toDoubleOrNull() ?: 1.5,
                        tripKm.toDoubleOrNull() ?: 10.0,
                        rating.toDoubleOrNull() ?: 4.8,
                        tripsCount.toIntOrNull() ?: 30,
                        ageSec.toIntOrNull() ?: 15,
                        paymentMethod,
                        stopsCount,
                        pickupAddr,
                        dropoffAddr,
                        passengerNote.ifBlank { null }
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                modifier = Modifier.testTag("execute_simulation_button")
            ) {
                Text("فحص كارت الطلب الآن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
