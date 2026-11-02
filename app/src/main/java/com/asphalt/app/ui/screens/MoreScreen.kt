package com.asphalt.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asphalt.app.ui.components.IosCard
import com.asphalt.app.ui.components.IosPrimaryButton
import com.asphalt.app.ui.components.SectionHeader
import com.asphalt.app.ui.theme.AppPrimary

import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Logout
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * شاشة "المزيد": المظهر (نهاري/ليلي)، إعداد الصلاحيات، لوحة التحكم الإدارية، الحساب السحابي، وتشخيص القراءة.
 */
@Composable
fun MoreScreen(
    isDark: Boolean,
    subscription: com.asphalt.app.model.DriverSubscription,
    onNavigateToSubscription: () -> Unit,
    isAdmin: Boolean = false,
    onNavigateToAdminPanel: () -> Unit,
    onSignOut: () -> Unit,
    onToggleTheme: () -> Unit,
    captureProvider: () -> String,
    onOpenSetupWizard: () -> Unit
) {
    val context = LocalContext.current
    var showCapture by remember { mutableStateOf(false) }
    var captureText by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "المزيد",
            subtitle = "المظهر والصلاحيات وتشخيص القراءة"
        )

        // المظهر
        IosCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = AppPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "الوضع الليلي",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDark) "شغّال دلوقتي" else "مقفول (الوضع النهاري)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Switch(checked = isDark, onCheckedChange = { onToggleTheme() })
            }
        }

        // باقات واشتراكات أسفلت
        IosCard(
            borderColor = AppPrimary.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "نظام الاشتراكات المرن",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "الباقة الحالية: ${subscription.planType.titleArabic} • ${if (subscription.isCurrentlyActive) "رصيد الأيام: ${subscription.remainingDays} يوم" else "منتهي"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "نظام الـ 24 ساعة النشطة: الأيام لا تنقص بالتقويم! تُخصم الأيام فقط عند تشغيل القارئ لبدء حصة 24 ساعة. باقات شهرية وأسبوعية ومرنة بالأيام مع خصم يصل إلى 50%.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            IosPrimaryButton(
                text = "إدارة الباقة والاشتراكات",
                onClick = onNavigateToSubscription,
                icon = Icons.Filled.CreditCard,
                testTag = "open_subscriptions_button"
            )
        }

        // لوحة التحكم الإدارية (Admin Panel)
        if (isAdmin) IosCard(
            borderColor = Color(0xFF3B82F6).copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "لوحة التحكم الإدارية (Admin Panel)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "مراقبة وإدارة السائقين والحصص النشطة وسجل الرحلات المباشر",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "تتيح لك كمسؤول مراقبة جميع السائقين المشتركين، وحصص الـ 24 ساعة النشطة لحظياً، وشحن أرصدة الأيام لأي سائق، واستعراض سجل الطلبات المباشر من إن درايف وديدي وأوبر المتزامن عبر خوادم أسفلت Firebase.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            IosPrimaryButton(
                text = "فتح لوحة التحكم الإدارية",
                onClick = onNavigateToAdminPanel,
                icon = Icons.Default.AdminPanelSettings,
                testTag = "open_admin_panel_button"
            )
        }

        // حساب Firebase السحابي
        IosCard {
            val user = Firebase.auth.currentUser
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = AppPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "الحساب السحابي المتزامن",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = user?.email ?: (user?.displayName ?: "متصل بـ Firebase (asphalt-37f49)"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("خروج", fontSize = 12.sp)
                }
            }
        }

        // الصلاحيات
        IosCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "إعداد الصلاحيات",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "خدمة الوصول (قراءة الشاشة واللقطة للـ OCR)، والظهور فوق التطبيقات، والإشعارات، واستثناء البطارية.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            IosPrimaryButton(
                text = "افتح معالج الإعداد",
                onClick = onOpenSetupWizard,
                icon = Icons.Default.Settings,
                testTag = "open_setup_wizard_button"
            )
        }

        // تشخيص القراءة
        IosCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "تشخيص القراءة",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "بيعرض آخر نص اتقرا من الشاشة (نص + OCR). افتح إن درايف أو ديدي لحد ما يظهر طلب، ارجع هنا واضغط عرض. النص ده في ذاكرة التطبيق بس ومبيتحفظش، وممكن يحتوي أسماء، فراجعه قبل ما تبعته لأي حد.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    captureText = captureProvider()
                    showCapture = true
                    copied = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("اعرض آخر قراءة")
            }

            if (showCapture) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = captureText.ifBlank { "لسه ما اتقرا حاجة. افتح تطبيق التوصيل وجرّب تاني." },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (captureText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("asphalt-capture", captureText))
                            copied = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(if (copied) "اتنسخ" else "انسخ النص")
                    }
                }
            }
        }

        // التزامات التطبيق
        IosCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "التزامات أسفلت V5",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            val points = listOf(
                "أداة قراءة وحساب فقط.",
                "مبيضغطش على أي زرار، ومبيقبلش ولا بيرفض ولا بيفاوض بدالك.",
                "بيقرا نص الشاشة ولقطة شاشة (OCR) بس لما يكون إن درايف أو ديدي أو أوبر هو اللي قدامك.",
                "اللقطة بتتقرا في الذاكرة وبتتمسح فوراً، ومبتتحفظش.",
                "مبيحفظش اسم الراكب ولا رقمه.",
                "كل الحسابات على موبايلك."
            )
            points.forEachIndexed { index, text ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                }
                Text(
                    text = "${index + 1}. $text",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Text(
            text = "أسفلت V5 • نسخة خاصة",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )
    }
}
