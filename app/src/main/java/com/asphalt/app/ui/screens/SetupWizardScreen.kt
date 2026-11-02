package com.asphalt.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asphalt.app.ui.components.IosCard
import com.asphalt.app.ui.components.IosPrimaryButton
import com.asphalt.app.ui.theme.*

@Composable
fun SetupWizardScreen(
    onFinishSetup: () -> Unit
) {
    val context = LocalContext.current

    var disclosureAccepted by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibilityService(context)) }
    var isOverlayEnabled by remember { mutableStateOf(checkOverlayPermission(context)) }
    var isBatteryUnrestricted by remember { mutableStateOf(checkBatteryOptimization(context)) }

    // Recheck permissions on resume or manual click
    fun refreshPermissions() {
        isAccessibilityEnabled = checkAccessibilityService(context)
        isOverlayEnabled = checkOverlayPermission(context)
        isBatteryUnrestricted = checkBatteryOptimization(context)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "إعداد قارئ أسفلت V5",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "خطوات بسيطة لتفعيل القارئ وشريط الحكم العايم فوق شاشة أوبر وديدي وإن درايف",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            // Step 1: Mandatory Google Play Disclosure
            IosCard {
                StepHeader(
                    stepNumber = "1",
                    title = "الإفصاح الواضح والموافقة",
                    isDone = disclosureAccepted
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = AppPrimaryTint,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "إفصاح إمكانية الوصول (Accessibility):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AppPrimaryPressed
                        )
                        Text(
                            text = "يستخدم تطبيق أسفلت V5 واجهة AccessibilityService لغرض واحد فقط: قراءة بيانات كروت الطلبات المعروضة على الشاشة (السعر، المسافات، المحطات، التقييم) من تطبيقات النقل المحددة (إن درايف، ديدي، أوبر) وتقديم تقييم فوري للربحية. القراءة بتتم من نص الشاشة ومن لقطة شاشة بتتقرا بالـ OCR على الموبايل نفسه.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 17.sp
                        )
                        Text(
                            text = "• اللقطة بتتقرا في الذاكرة وبتتمسح فوراً ومبتتحفظش، ومبيخزنش بيانات شخصية للركاب، ومبيضغطش ولا بيقبل ولا بيرفض الطلبات نيابة عنك مطلقاً.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppPrimaryPressed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = disclosureAccepted,
                        onCheckedChange = { disclosureAccepted = it },
                        modifier = Modifier.testTag("disclosure_checkbox")
                    )
                    Text(
                        text = "قرأت الإفصاح وأوافق على منح الإذن لقراءة كروت التطبيقات الثلاثة فقط",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Step 2: Accessibility Service activation
            IosCard {
                StepHeader(
                    stepNumber = "2",
                    title = "تفعيل خدمة إمكانية الوصول",
                    isDone = isAccessibilityEnabled
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "فعّل \"قارئ طلبات أسفلت V5\" من قائمة إمكانية الوصول:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Brand manufacturer guidance
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "مسار الخدمة حسب نوع هاتفك:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(text = "• سامسونج: الضبط > إمكانية الوصول > التطبيقات المثبتة", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Text(text = "• شاومي وريدمي: الإعدادات > إعدادات إضافية > إمكانية الوصول > التطبيقات التي تم تنزيلها", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Text(text = "• أوبو وريلمي: الإعدادات > إعدادات النظام > تسهيل الاستخدام", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Text(text = "• فيفو وهواوي: الإعدادات > المساعدة الذكية > إمكانية الوصول", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    enabled = disclosureAccepted,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAccessibilityEnabled) VerdictAccept else AppPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_accessibility_button")
                ) {
                    Text(if (isAccessibilityEnabled) "الخدمة مفعلة ✓" else "فتح إعدادات إمكانية الوصول")
                }
            }

            // Step 3: Draw over other apps (SYSTEM_ALERT_WINDOW)
            IosCard {
                StepHeader(
                    stepNumber = "3",
                    title = "الظهور فوق التطبيقات الأخرى",
                    isDone = isOverlayEnabled
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "مطلوب لإظهار شريط الحكم الصغير العايم فوق شاشة الطلب والخريطة مباشرة.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOverlayEnabled) VerdictAccept else AppPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_overlay_permission_button")
                ) {
                    Text(if (isOverlayEnabled) "الإذن مفعّل ✓" else "السماح بالظهور فوق التطبيقات")
                }
            }

            // Step 4: Battery & Auto-start Optimization
            IosCard {
                StepHeader(
                    stepNumber = "4",
                    title = "تحسين البطارية والتشغيل في الخلفية",
                    isDone = isBatteryUnrestricted
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "اختر \"بدون قيود\" (Unrestricted) علشان نظام أندرويد ما يقفلش القارئ في الخلفية وتفوتك طلبات مهمة.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val appIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(appIntent)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBatteryUnrestricted) VerdictAccept else AppPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_battery_settings_button")
                ) {
                    Text(if (isBatteryUnrestricted) "البطارية بدون قيود ✓" else "ضبط البطارية: بدون قيود")
                }
            }

            // Crucial Banking & Security Apps Warning
            Surface(
                color = VerdictConsiderBg,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VerdictConsider.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = VerdictConsider,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "تنبيه أمني هام: بعض تطبيقات البنوك والحكومة (مثل إنستاباي أو المحافظ البنكية) ترفض العمل عند وجود أي قارئ شاشة مفعل على الجهاز لحمايتك. أسفلت V5 يلتزم بالمعايير الأمنية ولا يتحايل على ذلك، ويمكنك إيقاف القارئ بنقرة واحدة من الإشعار وقت استخدام البنك ثم تشغيله فوراً.",
                        fontSize = 12.sp,
                        color = VerdictConsider,
                        lineHeight = 17.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { refreshPermissions() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("تحديث الحالة")
                }

                IosPrimaryButton(
                    text = "بدء الاستخدام والدخول للرئيسية",
                    onClick = onFinishSetup,
                    modifier = Modifier.weight(2f),
                    testTag = "finish_setup_wizard_button"
                )
            }
        }
    }
}

@Composable
private fun StepHeader(
    stepNumber: String,
    title: String,
    isDone: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isDone) VerdictAccept else AppPrimary),
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = AppOnVerdict,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = stepNumber,
                        color = AppOnPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (isDone) {
            Text(
                text = "مكتمل",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerdictAccept
            )
        }
    }
}

private fun checkAccessibilityService(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: ""
    val componentName = android.content.ComponentName(
        context.packageName,
        "com.asphalt.app.service.AsphaltAccessibilityService"
    )
    val component = componentName.flattenToString()
    val short = componentName.flattenToShortString()
    return enabledServices.split(':').any {
        it.equals(component, ignoreCase = true) || it.equals(short, ignoreCase = true)
    }
}

private fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

private fun checkBatteryOptimization(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } else {
        true
    }
}
