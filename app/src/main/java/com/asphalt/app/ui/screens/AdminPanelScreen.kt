package com.asphalt.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asphalt.app.model.DriverFirebaseDoc
import com.asphalt.app.model.RideFirebaseDoc
import com.asphalt.app.ui.components.IosCard
import com.asphalt.app.ui.theme.AppPrimary
import java.text.SimpleDateFormat
import java.util.*

private val ColorSuccess = Color(0xFF22C55E)
private val ColorWarning = Color(0xFFF59E0B)
private val ColorDanger = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    drivers: List<DriverFirebaseDoc>,
    rides: List<RideFirebaseDoc>,
    onAddDaysToDriver: (driverId: String, days: Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Drivers, 1 = Real-time Rides, 2 = System Stats
    var driverSearchQuery by remember { mutableStateOf("") }
    var selectedDriverForRecharge by remember { mutableStateOf<DriverFirebaseDoc?>(null) }
    var daysToRechargeInput by remember { mutableStateOf("30") }

    // ترتيب السائقين بالجديد أولاً
    val sortedDrivers = remember(drivers) {
        drivers.sortedByDescending { it.createdAt?.toDate()?.time ?: it.lastActiveTimestamp?.toDate()?.time ?: 0L }
    }

    // تصفية السائقين في الوقت الفعلي برقم الهاتف أو الإيميل أو الاسم
    val filteredDrivers = remember(sortedDrivers, driverSearchQuery) {
        if (driverSearchQuery.isBlank()) {
            sortedDrivers
        } else {
            val q = driverSearchQuery.trim().lowercase()
            sortedDrivers.filter { driver ->
                driver.phone.lowercase().contains(q) ||
                driver.email.lowercase().contains(q) ||
                driver.driverName.lowercase().contains(q) ||
                driver.userId.lowercase().contains(q)
            }
        }
    }

    val activeSessionsCount = drivers.count { it.isSessionActive }
    val totalRevenueEstimate = rides.sumOf { it.fare }
    val totalProfitEstimate = rides.sumOf { it.netProfit }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(ColorSuccess, shape = CircleShape)
                        )
                        Text(
                            text = "لوحة التحكم الإدارية • إدارة السائقين والاشتراكات",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // كروت الإحصائيات الحية
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("السائقون المسجلون", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Text("${drivers.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("حصص نشطة الآن (24س)", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Text("$activeSessionsCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppPrimary)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("رحلات مراقبة", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Text("${rides.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ColorSuccess)
                    }
                }
            }

            // التبديل بين الأقسام
            TabRow(
                selectedTabIndex = selectedSection,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = AppPrimary
            ) {
                Tab(
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    text = { Text("السائقون والاشتراكات (${drivers.size})") }
                )
                Tab(
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    text = { Text("الطلبات الحية (${rides.size})") }
                )
                Tab(
                    selected = selectedSection == 2,
                    onClick = { selectedSection = 2 },
                    text = { Text("إحصائيات النظام") }
                )
            }

            when (selectedSection) {
                0 -> {
                    // حقل البحث اللحظي بالهاتف أو الإيميل
                    OutlinedTextField(
                        value = driverSearchQuery,
                        onValueChange = { driverSearchQuery = it },
                        placeholder = { Text("بحث برقم الهاتف أو البريد الإلكتروني أو الاسم...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppPrimary) },
                        trailingIcon = {
                            if (driverSearchQuery.isNotBlank()) {
                                IconButton(onClick = { driverSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_driver_search_input"),
                        singleLine = true
                    )

                    if (filteredDrivers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(48.dp))
                                Text(
                                    text = if (driverSearchQuery.isNotBlank()) "لا توجد نتائج تطابق: \"$driverSearchQuery\"" else "لا يوجد سائقون مسجلون بعد في Firebase",
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredDrivers, key = { it.id.ifBlank { it.userId } }) { driver ->
                                DriverAdminCard(
                                    driver = driver,
                                    onRechargeClick = {
                                        selectedDriverForRecharge = driver
                                    },
                                    onCopyText = { label, text ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
                                        Toast.makeText(context, "تم نسخ $label: $text", Toast.LENGTH_SHORT).show()
                                    },
                                    onWhatsAppClick = { phone ->
                                        val cleanPhone = phone.replace("+", "").replace(" ", "")
                                        try {
                                            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            })
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "تعذر فتح واتساب", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // الطلبات المفحوصة لحظياً
                    if (rides.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "لا توجد رحلات مسجلة في الوقت الفعلي حتى الآن",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(rides, key = { it.id }) { ride ->
                                RideAdminCard(ride = ride)
                            }
                        }
                    }
                }
                2 -> {
                    // إحصائيات النظام العامة
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IosCard {
                            Text(
                                "نظام أسفلت V5 المتكامل",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("إجمالي مبيعات الرحلات المفحوصة: ${"%.1f".format(totalRevenueEstimate)} ج.م", fontSize = 14.sp)
                            Text("إجمالي صافي أرباح السائقين: ${"%.1f".format(totalProfitEstimate)} ج.م", fontSize = 14.sp)
                            Text("نسبة قبول الطلبات المربحة: ${if (rides.isNotEmpty()) (rides.count { it.verdict == "ACCEPT" } * 100 / rides.size) else 0}%", fontSize = 14.sp)
                            Text("قاعدة بيانات السحابة: asphalt-37f49 (Firestore Cloud)", fontSize = 14.sp, color = AppPrimary)
                            Text("حساب إنستاباي المعتمد: ${PaymentConfig.INSTAPAY_ACCOUNT}", fontSize = 14.sp, color = Color(0xFF6366F1), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // نافذة التحقق من الدفع وتفعيل رصيد الأيام للسائق
    if (selectedDriverForRecharge != null) {
        val driver = selectedDriverForRecharge!!
        AlertDialog(
            onDismissRequest = { selectedDriverForRecharge = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(ColorSuccess.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = ColorSuccess, modifier = Modifier.size(30.dp))
                }
            },
            title = {
                Text(
                    text = "تأكيد الدفع وتفعيل الأيام",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // بيانات السائق
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("الكابتن: ${driver.driverName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (driver.phone.isNotBlank()) Text("الهاتف: ${driver.phone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            if (driver.email.isNotBlank()) Text("الإيميل: ${driver.email}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            Text("الرصيد الحالي: ${driver.remainingDays} يوم عمل (24س لكل يوم)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppPrimary)
                        }
                    }

                    Text(
                        "حدد عدد الأيام المراد إضافتها بعد التحقق من تحويل إنستاباي:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = daysToRechargeInput,
                        onValueChange = { daysToRechargeInput = it.filter { char -> char.isDigit() } },
                        label = { Text("عدد الأيام المراد إضافتها") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_recharge_days_input")
                    )

                    // أزرار سريعة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(3, 7, 14, 30, 60).forEach { preset ->
                            SuggestionChip(
                                onClick = { daysToRechargeInput = preset.toString() },
                                label = { Text("+$preset يوم", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val days = daysToRechargeInput.toIntOrNull() ?: 7
                        onAddDaysToDriver(driver.id.ifBlank { driver.userId }, days)
                        Toast.makeText(context, "تم تفعيل $days يوماً بنجاح للكابتن ${driver.driverName}", Toast.LENGTH_SHORT).show()
                        selectedDriverForRecharge = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccess),
                    modifier = Modifier.testTag("admin_confirm_recharge_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تأكيد وتفعيل الرصيد فوراً", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedDriverForRecharge = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun DriverAdminCard(
    driver: DriverFirebaseDoc,
    onRechargeClick: () -> Unit,
    onCopyText: (label: String, text: String) -> Unit,
    onWhatsAppClick: (phone: String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // الصف العلوي: الاسم وحالة الـ 24 ساعة
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AppPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = AppPrimary, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = driver.driverName.ifBlank { "كابتن أسفلت" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (driver.role == "admin") {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF6366F1).copy(alpha = 0.15f)
                                ) {
                                    Text("مسؤول", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Text(
                            text = "${driver.governorate} • ${driver.engineCc}cc • ${if (driver.vehicleType == "MOTORCYCLE") "موتوسيكل" else "سيارة"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // شارة الحصة النشطة (24 ساعة)
                Surface(
                    color = if (driver.isSessionActive) Color(0xFF166534) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (driver.isSessionActive) ColorSuccess else MaterialTheme.colorScheme.secondary,
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = if (driver.isSessionActive) "حصة 24س نشطة" else "الحصة غير نشطة",
                            fontSize = 11.sp,
                            color = if (driver.isSessionActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // بيانات الاتصال: الهاتف والإيميل مع إمكانية النسخ والمراسلة
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (driver.phone.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                            Text(driver.phone, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { onCopyText("رقم الهاتف", driver.phone) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الهاتف", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                            }
                            IconButton(
                                onClick = { onWhatsAppClick(driver.phone) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = "مراسلة واتساب", modifier = Modifier.size(14.dp), tint = ColorSuccess)
                            }
                        }
                    }
                }

                if (driver.email.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                            Text(driver.email, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        }

                        IconButton(
                            onClick = { onCopyText("البريد الإلكتروني", driver.email) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الإيميل", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // رصيد الأيام وزر الشحن
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "رصيد الأيام:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "${driver.remainingDays} يوم عمل",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (driver.remainingDays > 0) ColorSuccess else ColorDanger
                        )
                    }

                    Text(
                        text = "الباقة: ${driver.currentPlan} | فُحص: ${driver.totalRidesScanned} طلب",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Button(
                    onClick = onRechargeClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تفعيل / شحن أيام", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RideAdminCard(ride: RideFirebaseDoc) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(0.7.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = when (ride.app.lowercase()) {
                            "uber" -> Color.Black
                            "didi" -> Color(0xFFEA580C)
                            else -> Color(0xFF16A34A)
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = ride.app,
                            fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${"%.1f".format(ride.fare)} ج.م",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "صافي: ${"%.1f".format(ride.netProfit)} ج.م",
                        fontSize = 12.sp,
                        color = ColorSuccess,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "الكابتن: ${ride.driverName.ifBlank { "كابتن أسفلت" }} • المسافة: ${ride.tripKm} كم",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = when (ride.verdict) {
                    "ACCEPT" -> Color(0xFF166534)
                    "REJECT" -> Color(0xFF991B1B)
                    else -> Color(0xFF854D0E)
                },
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = when (ride.verdict) {
                        "ACCEPT" -> "اقبلها"
                        "REJECT" -> "ارفضها"
                        else -> "فكر فيها"
                    },
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
