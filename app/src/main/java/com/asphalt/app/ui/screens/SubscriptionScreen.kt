package com.asphalt.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asphalt.app.model.*
import com.asphalt.app.ui.components.IosCard
import com.asphalt.app.ui.components.SectionHeader
import com.asphalt.app.ui.theme.*

/** Single source of truth for the payment / support contact details shown in the app. */
object PaymentConfig {
    const val INSTAPAY_ACCOUNT = "01122104999"
    const val WHATSAPP_LOCAL = "01128440045"
    const val WHATSAPP_INTERNATIONAL = "201128440045" // Egypt +20, no leading zero, no "+"
}

private const val DEFAULT_INSTAPAY_ACCOUNT = PaymentConfig.INSTAPAY_ACCOUNT
private const val WHATSAPP_INTERNATIONAL_DIAL = PaymentConfig.WHATSAPP_INTERNATIONAL
private const val WHATSAPP_LOCAL_DISPLAY = PaymentConfig.WHATSAPP_LOCAL

@Composable
fun SubscriptionScreen(
    currentSubscription: DriverSubscription,
    driverProfile: DriverProfile = DriverProfile(),
    driverEmail: String = "",
    driverUid: String = "",
    sessionCountdown: String = "00:00:00",
    isSessionActive: Boolean = false,
    sessionProgress: Float = 0f,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var selectedPlanType by remember { mutableStateOf(SubscriptionPlanType.MONTHLY) }
    var customDays by remember { mutableIntStateOf(14) }
    var copiedInstaPay by remember { mutableStateOf(false) }

    // حساب التسعير والتوفير لحظياً في الوقت الفعلي
    val activeQuote by remember(selectedPlanType, customDays) {
        derivedStateOf {
            SubscriptionCalculator.calculateQuote(selectedPlanType, customDays)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // زر الرجوع مع العنوان
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .testTag("subscription_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            SectionHeader(
                title = "الاشتراكات وشحن الرصيد",
                subtitle = "اختر الباقة المناسبة وسدّد عبر إنستاباي بسهولة"
            )
        }

        // بنر ميزة الـ 24 ساعة النشطة
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)),
            color = AppPrimary.copy(alpha = 0.1f),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppPrimary.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AppPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = AppPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ميزة أسفلت: نظام الـ 24 ساعة النشطة",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "الأيام لا تنقص بالتقويم! يتم خصم اليوم فقط عند تشغيل القارئ لبدء حصة 24 ساعة كاملة. لو لم تعمل اليوم، رصيدك محفوظ 100%.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // كارت رصيد الأيام وحالة الحصة الحالية
        IosCard(
            borderColor = if (currentSubscription.isCurrentlyActive) AppPrimary.copy(alpha = 0.6f) else VerdictReject.copy(alpha = 0.6f)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (currentSubscription.isCurrentlyActive) AppPrimary.copy(alpha = 0.15f) else VerdictRejectBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = if (currentSubscription.isCurrentlyActive) AppPrimary else VerdictReject,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "حالة الاشتراك الحالي",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "رصيدك: ${currentSubscription.remainingDays} يوم عمل متبقي",
                                fontSize = 13.sp,
                                color = if (currentSubscription.hasRemainingBalance) AppPrimary else VerdictReject,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSessionActive) VerdictAcceptBg else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (isSessionActive) "حصة 24س نشطة" else if (currentSubscription.remainingDays > 0) "الرصيد جاهز" else "الرصيد منتهي",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSessionActive) VerdictAccept else if (currentSubscription.remainingDays > 0) AppPrimary else VerdictReject,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (isSessionActive) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(VerdictAcceptBg.copy(alpha = 0.4f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "الوقت المتبقي في الحصة الحالية:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = sessionCountdown,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = VerdictAccept
                            )
                        }
                        Text(
                            text = "تنتهي: ${currentSubscription.sessionEndTimeFormatted}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // اختيار الباقة
        Text(
            text = "اختر الباقة المطلوبة:",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubscriptionPlanType.entries.forEach { plan ->
                val isSelected = selectedPlanType == plan
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedPlanType = plan }
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) AppPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .testTag("plan_tab_${plan.name}"),
                    color = if (isSelected) AppPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when (plan) {
                                SubscriptionPlanType.MONTHLY -> "شهرية"
                                SubscriptionPlanType.WEEKLY -> "أسبوعية"
                                SubscriptionPlanType.CUSTOM_DAYS -> "مخصصة"
                            },
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) AppPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (plan) {
                                SubscriptionPlanType.MONTHLY -> "30 يوم"
                                SubscriptionPlanType.WEEKLY -> "7 أيام"
                                SubscriptionPlanType.CUSTOM_DAYS -> "اختر الأيام"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // تفاصيل الباقة المخصصة
        AnimatedVisibility(
            visible = selectedPlanType == SubscriptionPlanType.CUSTOM_DAYS,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            IosCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "حدد عدد الأيام المطلوبة:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "خصم تصاعدي مع زيادة الأيام",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { if (customDays > 1) customDays-- },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                enabled = customDays > 1
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "إنقاص", modifier = Modifier.size(16.dp))
                            }

                            Text(
                                text = "$customDays ي",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AppPrimary,
                                modifier = Modifier.widthIn(min = 55.dp),
                                textAlign = TextAlign.Center
                            )

                            IconButton(
                                onClick = { if (customDays < 90) customDays++ },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                enabled = customDays < 90
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "زيادة", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // شريط التمرير
                    Slider(
                        value = customDays.toFloat(),
                        onValueChange = { customDays = it.toInt() },
                        valueRange = 1f..60f,
                        steps = 58,
                        colors = SliderDefaults.colors(
                            thumbColor = AppPrimary,
                            activeTrackColor = AppPrimary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("custom_days_slider")
                    )

                    // خيارات سريعة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(3, 5, 10, 15, 20, 30).forEach { days ->
                            val isQuickSelected = customDays == days
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { customDays = days },
                                color = if (isQuickSelected) AppPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "$days ي",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isQuickSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // كارت الحساب الفوري والتكلفة
        IosCard(
            borderColor = AppPrimary.copy(alpha = 0.8f)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = activeQuote.planType.titleArabic,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "المدة: ${activeQuote.daysCount} يوم عمل (24 ساعة لكل يوم)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppPrimary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = activeQuote.planType.badge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "سعر اليوم الفعلي:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "${String.format("%.1f", activeQuote.effectivePricePerDay)} ج.م / يوم",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppPrimary
                    )
                }

                if (activeQuote.discountPct > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "السعر بدون خصم:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "${activeQuote.grossTotal.toInt()} ج.م",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "نسبة التوفير الفوري:",
                            fontSize = 13.sp,
                            color = VerdictAccept
                        )
                        Text(
                            text = "وفر ${activeQuote.discountPct}% من قيمة الاشتراك",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = VerdictAccept
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // الإجمالي النهائي
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "المبلغ المطلوب تحويله:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "شامل القراءة الذكية وجميع التحديثات",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Text(
                        text = "${activeQuote.finalTotal.toInt()} ج.م",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = AppPrimary
                    )
                }
            }
        }

        // =========================================================================
        // قسم الدفع عبر إنستاباي والتأكيد المباشر عبر واتساب (بدون أي حقل لرقم العملية)
        // =========================================================================
        IosCard(
            borderColor = Color(0xFF6366F1).copy(alpha = 0.6f)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "طريقة السداد عبر إنستاباي (InstaPay)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "تحويل فوري بدون عمولات إضافية",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Text(
                    text = "حوّل مبلغ (${activeQuote.finalTotal.toInt()} ج.م) عبر تطبيق إنستاباي إلى العنوان التالي، ثم اضغط زر إرسال عبر واتساب لتأكيد الدفع وتفعيل أيامك فوراً:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 17.sp
                )

                // صندوق حساب إنستاباي
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "رقم حساب إنستاباي المعتمد (InstaPay):",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = DEFAULT_INSTAPAY_ACCOUNT,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (copiedInstaPay) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = VerdictAcceptBg
                            ) {
                                Text(
                                    text = "تم النسخ ✓",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VerdictAccept,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // الزر الأول: نسخ حساب إنستاباي
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("instapay_account", DEFAULT_INSTAPAY_ACCOUNT))
                        copiedInstaPay = true
                        // Android 13+ already shows its own "copied" confirmation
                        if (android.os.Build.VERSION.SDK_INT < 33) {
                            Toast.makeText(context, "تم نسخ رقم حساب إنستاباي: $DEFAULT_INSTAPAY_ACCOUNT", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF6366F1)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6366F1)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("copy_instapay_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (copiedInstaPay) "تم نسخ حساب إنستاباي ✓" else "نسخ حساب إنستاباي ($DEFAULT_INSTAPAY_ACCOUNT)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // الزر الثاني: إرسال عبر واتساب مباشرة (مع تعبئة الاسم والإيميل ورقم الهاتف تلقائياً)
                Button(
                    onClick = {
                        val captainName = driverProfile.name.ifBlank { "كابتن أسفلت" }
                        val captainPhone = driverProfile.phone.ifBlank { "غير مسجل" }
                        val captainEmail = driverEmail.ifBlank { "غير مسجل" }
                        val planTitle = activeQuote.planType.titleArabic
                        val daysCount = activeQuote.daysCount
                        val totalAmount = activeQuote.finalTotal.toInt()

                        val message = """
                            السلام عليكم إدارة أسفلت V5 🚗،
                            أرغب في تفعيل الاشتراك بعد التحويل عبر إنستاباي إلى الرقم: $DEFAULT_INSTAPAY_ACCOUNT

                            📋 بيانات الكابتن:
                            • الاسم: $captainName
                            • رقم الهاتف: $captainPhone
                            • البريد الإلكتروني: $captainEmail
                            • معرّف الحساب: ${driverUid.ifBlank { "غير متاح" }}

                            💳 تفاصيل الباقة المطلوبة:
                            • الباقة: $planTitle ($daysCount يوم عمل)
                            • المبلغ المحوّل: $totalAmount ج.م

                            سأرفق صورة إيصال التحويل في الرسالة التالية. برجاء التحقق وتفعيل رصيد الأيام في حسابي. شكراً جزيلاً!
                        """.trimIndent()

                        // Uri.encode -> %20 for spaces (URLEncoder produced "+" which WhatsApp can show literally)
                        val uri = Uri.parse("https://wa.me/$WHATSAPP_INTERNATIONAL_DIAL?text=${Uri.encode(message)}")
                        // Try WhatsApp, then WhatsApp Business, then any app/browser that handles the link.
                        val candidates = listOf("com.whatsapp", "com.whatsapp.w4b", null)
                        var opened = false
                        for (pkg in candidates) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    if (pkg != null) setPackage(pkg)
                                }
                                context.startActivity(intent)
                                opened = true
                                break
                            } catch (_: Exception) { /* try next */ }
                        }
                        if (!opened) {
                            Toast.makeText(context, "تعذر فتح واتساب. يمكنك مراسلة الإدارة على $WHATSAPP_LOCAL_DISPLAY", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25D366),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("send_whatsapp_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "إرسال إشعار الدفع عبر واتساب",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "ملاحظة: سيتم إرسال رسالة جاهزة تتضمن اسمك وإيميلك ورقم هاتفك والتكلفة المحددة لمسؤول الإدارة لتفعيل الرصيد مباشرة.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
