package com.asphalt.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asphalt.app.R
import com.asphalt.app.model.DriverProfile
import com.asphalt.app.model.DriverRules
import com.asphalt.app.model.ShiftSummary
import com.asphalt.app.ui.components.IosCard
import com.asphalt.app.ui.components.IosPrimaryButton
import com.asphalt.app.ui.theme.*

@Composable
fun HomeScreen(
    profile: DriverProfile,
    rules: DriverRules,
    summary: ShiftSummary,
    isReaderActive: Boolean,
    subscription: com.asphalt.app.model.DriverSubscription,
    sessionCountdown: String = "00:00:00",
    sessionProgress: Float = 0f,
    isSessionActive: Boolean = false,
    bannerMessage: String? = null,
    onClearBanner: () -> Unit = {},
    showInsufficientDaysDialog: Boolean = false,
    onDismissInsufficientDaysDialog: () -> Unit = {},
    onNavigateToSubscription: () -> Unit,
    onToggleReader: (Boolean) -> Unit,
    onToggleGoingHome: () -> Unit,
    onToggleGoingDest: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToZones: () -> Unit,
    onOpenSimulator: () -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Exact user-supplied ASPHALT artwork; displayed without redrawing or altering its lettering.
        Image(
            painter = painterResource(id = R.drawable.asphalt_brand_art),
            contentDescription = "شعار أسفلت V5 الأصلي",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF05070B))
        )

        // Driver Greeting Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "يا هلا، كابتن ${profile.firstName}",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "محافظة ${profile.governorate} • ${profile.currency}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // زرار التحويل بين الوضع النهاري والليلي
                Surface(
                    onClick = onToggleTheme,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("theme_toggle_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDark) "التحويل للوضع النهاري" else "التحويل للوضع الليلي",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (isReaderActive) VerdictAcceptBg else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isReaderActive) VerdictAccept else VerdictWait),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isReaderActive) Icons.Default.CheckCircle else Icons.Default.PauseCircle,
                            contentDescription = null,
                            tint = if (isReaderActive) VerdictAccept else VerdictWait,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // Activation Banner Message
        if (bannerMessage != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onClearBanner() }
                    .testTag("activation_banner"),
                color = AppPrimary.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppPrimary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AppPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = bannerMessage,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Insufficient Days Alert Dialog
        if (showInsufficientDaysDialog) {
            AlertDialog(
                onDismissRequest = onDismissInsufficientDaysDialog,
                title = {
                    Text(
                        text = "رصيد الأيام غير كافٍ",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "لا يمكنك تشغيل قارئ الطلبات لأن رصيدك الحالي 0 يوم. نظام أسفلت يخصم يوماً واحداً عند بدء كل حصة عمل 24 ساعة. يرجى شحن باقتك للاستمرار.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDismissInsufficientDaysDialog()
                            onNavigateToSubscription()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                        modifier = Modifier.testTag("dialog_recharge_button")
                    ) {
                        Text("شحن الباقة الآن")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissInsufficientDaysDialog) {
                        Text("لاحقاً")
                    }
                }
            )
        }

        // Reader Status Hero Card with Big iOS Switch
        IosCard(
            backgroundColor = MaterialTheme.colorScheme.surface,
            borderColor = if (isReaderActive) VerdictAccept else MaterialTheme.colorScheme.outline
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isReaderActive) VerdictAccept else VerdictWait)
                    )

                    Column {
                        Text(
                            text = if (isReaderActive) "قارئ الطلبات شغّال" else "القارئ متوقف مؤقتاً",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                isReaderActive && isSessionActive -> "يراقب إن درايف وديدي وأوبر • الحصة سارية"
                                isReaderActive -> "يراقب إن درايف وديدي وأوبر فقط"
                                isSessionActive -> "موقوف مؤقتاً • الحصة سارية ($sessionCountdown)"
                                subscription.remainingDays > 0 -> "شغّله لبدء حصة 24 ساعة (خصم 1 يوم من ${subscription.remainingDays})"
                                else -> "الرصيد منتهي • يرجى الشحن لتشغيل القارئ"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Switch(
                    checked = isReaderActive,
                    onCheckedChange = onToggleReader,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = VerdictAccept,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = VerdictWait
                    ),
                    modifier = Modifier.testTag("reader_active_switch")
                )
            }

            if (isReaderActive) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "التطبيقات المعتمدة: إن درايف • ديدي • أوبر",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "قراءة النص + ML Kit OCR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppPrimary
                    )
                }
            }
        }

        // 24-Hour Active Session Card & Live Countdown Engine
        IosCard(
            backgroundColor = MaterialTheme.colorScheme.surface,
            borderColor = when {
                isSessionActive -> VerdictAccept.copy(alpha = 0.8f)
                subscription.remainingDays > 0 -> AppPrimary.copy(alpha = 0.5f)
                else -> VerdictReject.copy(alpha = 0.6f)
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header row
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSessionActive -> VerdictAcceptBg
                                        subscription.remainingDays > 0 -> AppPrimary.copy(alpha = 0.12f)
                                        else -> VerdictRejectBg
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSessionActive) Icons.Default.HourglassTop else Icons.Default.Timer,
                                contentDescription = null,
                                tint = when {
                                    isSessionActive -> VerdictAccept
                                    subscription.remainingDays > 0 -> AppPrimary
                                    else -> VerdictReject
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "حصة العمل (نظام الـ 24 ساعة)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "اليوم = 24 ساعة تبدأ عند تشغيل القارئ فقط",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // Status pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isSessionActive -> VerdictAcceptBg
                            subscription.remainingDays > 0 -> MaterialTheme.colorScheme.surfaceVariant
                            else -> VerdictRejectBg
                        }
                    ) {
                        Text(
                            text = when {
                                isSessionActive -> "حصة نشطة الآن"
                                subscription.remainingDays > 0 -> "الرصيد محفوظ"
                                else -> "الرصيد منتهي"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isSessionActive -> VerdictAccept
                                subscription.remainingDays > 0 -> AppPrimary
                                else -> VerdictReject
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Main body: Countdown Timer or Idle State
                if (isSessionActive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VerdictAcceptBg.copy(alpha = 0.35f))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "الوقت المتبقي في الـ 24 ساعة الحالية",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = sessionCountdown,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = VerdictAccept,
                            modifier = Modifier.testTag("session_countdown_timer")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (1f - sessionProgress).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = VerdictAccept,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "تنتهي الحصة: ${subscription.sessionEndTimeFormatted} • أوقف وشغّل القارئ بدون أي خصم",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else if (subscription.remainingDays > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = AppPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "أيامك محفوظة 100% ولا تنقص بالتقويم",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "عند تشغيل القارئ، يُخصم يوم واحد وتبدأ حصة 24 ساعة فورية.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VerdictRejectBg.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = VerdictReject,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "لا يوجد رصيد أيام متبقي لبدء حصة عمل",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = VerdictReject
                            )
                            Text(
                                text = "اشحن باقتك الآن لتشغيل قارئ الطلبات وفحص الرحلات.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Footer with remaining balance and manage button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "رصيد الأيام المتبقية",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "${subscription.remainingDays} يوم عمل متاح",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (subscription.remainingDays > 0) AppPrimary else VerdictReject
                        )
                    }

                    TextButton(
                        onClick = onNavigateToSubscription,
                        colors = ButtonDefaults.textButtonColors(contentColor = AppPrimary),
                        modifier = Modifier.testTag("manage_subscription_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (subscription.remainingDays > 0) "إدارة / شحن أيام" else "شحن الرصيد الآن",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Today's Shift Summary ("حصة النهارده")
        IosCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "حصة النهارده",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "تحديث لحظي",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ShiftStatTile(
                    label = "طلبات اتفحصت",
                    value = "${summary.totalOrdersScanned}",
                    subText = "طلب",
                    color = AppPrimary
                )
                ShiftStatTile(
                    label = "طلبات قبلتها",
                    value = "${summary.acceptedOrdersCount}",
                    subText = "مطابقة",
                    color = VerdictAccept
                )
                ShiftStatTile(
                    label = "صافي ربح تقديري",
                    value = "${summary.totalEstimatedNetProfit.toInt()}",
                    subText = profile.currency,
                    color = VerdictAccept
                )
                ShiftStatTile(
                    label = "وفّرت مصاريف",
                    value = "${summary.avoidedCostSaved.toInt()}",
                    subText = profile.currency,
                    color = VerdictConsider
                )
            }
        }

        // Quick Going Home & Going Destination Chips
        IosCard {
            Text(
                text = "أوضاع التوجيه الذكي:",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DirectionModeButton(
                    title = "أنا مروّح دلوقتي",
                    subtitle = rules.homeAddress,
                    isActive = rules.isGoingHomeMode,
                    onClick = onToggleGoingHome,
                    modifier = Modifier.weight(1f)
                )

                DirectionModeButton(
                    title = "رايح هناك دلوقتي",
                    subtitle = rules.destAddress,
                    isActive = rules.isGoingDestMode,
                    onClick = onToggleGoingDest,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Live Simulator Banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppPrimaryTint,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppPrimary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = AppPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "مُحاكي كروت الطلبات الحي",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppPrimaryPressed
                    )
                }

                Text(
                    text = "اختبر فوراً كيف يقرأ التطبيق كروت إن درايف وديدي وأوبر، واحسب صافي الربح وشاهد النافذة العايمة وصوت تنبيه الفرصة دون الحاجة لفتح تطبيق السائق الآن.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )

                IosPrimaryButton(
                    text = "تشغيل تجربة طلب فوري",
                    onClick = onOpenSimulator,
                    icon = Icons.Default.Bolt,
                    testTag = "open_simulator_button"
                )
            }
        }

        // Quick Navigation Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionTile(
                icon = Icons.Default.Tune,
                title = "قواعدي",
                caption = "${rules.minRatePerKm} ج/كم • ${if (rules.engineSize.isMotorcycle) "🏍️ " + rules.engineSize.ccDisplayName else rules.engineSize.labelArabic.take(12)}",
                onClick = onNavigateToRules,
                modifier = Modifier.weight(1f)
            )

            QuickActionTile(
                icon = Icons.Default.Place,
                title = "مناطقي",
                caption = "المفضلة والمتجنّبة",
                onClick = onNavigateToZones,
                modifier = Modifier.weight(1f)
            )
        }

        // Latest Driver News & Alerts

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun ShiftStatTile(
    label: String,
    value: String,
    subText: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = InterFont)
        Text(text = subText, fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun DirectionModeButton(
    title: String,
    subtitle: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) AppPrimary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.Navigation else Icons.Default.NearMe,
                    contentDescription = null,
                    tint = if (isActive) AppOnPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) AppOnPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = subtitle,
                fontSize = 11.sp,
                maxLines = 1,
                color = if (isActive) AppOnPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = caption,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1
            )
        }
    }
}
