package com.asphalt.app.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * نظام الألوان (حبر): واجهة أبيض/أسود على طريقة تطبيقات آبل، واللون بيظهر بس في الأحكام.
 * فيه وضعين: نهاري وليلي، والتحويل بينهم من زرار في الشاشة الرئيسية.
 */
class AppPalette(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val border: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryPressed: Color,
    val primaryTint: Color,
    val onVerdict: Color,
    val accept: Color,
    val acceptBg: Color,
    val check: Color,
    val checkBg: Color,
    val consider: Color,
    val considerBg: Color,
    val reject: Color,
    val rejectBg: Color,
    val wait: Color,
    val waitBg: Color
)

val LightPalette = AppPalette(
    isDark = false,
    background = Color(0xFFF0F2F7),
    card = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8ECF4),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF596579),
    divider = Color(0xFFD9E0EA),
    border = Color(0xFF9DDCE5),
    primary = Color(0xFFE51D2A),
    onPrimary = Color(0xFFFFFFFF),
    primaryPressed = Color(0xFFB91520),
    primaryTint = Color(0xFFFFE8EA),
    onVerdict = Color(0xFFFFFFFF),
    accept = Color(0xFF23804F),
    acceptBg = Color(0xFFE6F2EB),
    check = Color(0xFF2F6FB0),
    checkBg = Color(0xFFE6EEF7),
    consider = Color(0xFFD08A12),
    considerBg = Color(0xFFF7EEDC),
    reject = Color(0xFFD0392F),
    rejectBg = Color(0xFFF8E6E3),
    wait = Color(0xFF6E6E6B),
    waitBg = Color(0xFFE8E8E4)
)

val DarkPalette = AppPalette(
    isDark = true,
    background = Color(0xFF070A10),
    card = Color(0xFF111722),
    surfaceVariant = Color(0xFF1A2431),
    textPrimary = Color(0xFFF4F7FC),
    textSecondary = Color(0xFFB1BECE),
    divider = Color(0xFF273746),
    border = Color(0xFF00BFD2),
    primary = Color(0xFFFF2636),
    onPrimary = Color(0xFFFFFFFF),
    primaryPressed = Color(0xFFD91526),
    primaryTint = Color(0xFF32151D),
    onVerdict = Color(0xFF0E0E0F),
    accept = Color(0xFF4CC38A),
    acceptBg = Color(0xFF14291F),
    check = Color(0xFF6AA9E8),
    checkBg = Color(0xFF142130),
    consider = Color(0xFFF0B23C),
    considerBg = Color(0xFF2E2410),
    reject = Color(0xFFF26B5E),
    rejectBg = Color(0xFF301816),
    wait = Color(0xFF9C9C98),
    waitBg = Color(0xFF232326)
)

/**
 * حالة الثيم العامة. بتتقرا من أي مكان (الشاشات والكارت العايم) وأي تغيير فيها
 * بيعمل إعادة رسم تلقائي لأنها State بتاع Compose.
 */
object ThemeState {
    /** 0 = زي إعداد الموبايل، 1 = نهاري، 2 = ليلي */
    var mode by mutableStateOf(0)
    var systemDark by mutableStateOf(false)

    val isDark: Boolean get() = mode == 2 || (mode == 0 && systemDark)
    val palette: AppPalette get() = if (isDark) DarkPalette else LightPalette

    fun updateSystemDark(context: Context) {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        systemDark = night == Configuration.UI_MODE_NIGHT_YES
    }
}

// ----- أسماء الألوان اللي بتستخدمها الشاشات (كلها بتتغير مع الوضع نهاري/ليلي) -----
val AppBackground: Color get() = ThemeState.palette.background
val AppCard: Color get() = ThemeState.palette.card
val AppSurfaceVariant: Color get() = ThemeState.palette.surfaceVariant
val AppTextPrimary: Color get() = ThemeState.palette.textPrimary
val AppTextSecondary: Color get() = ThemeState.palette.textSecondary
val AppDivider: Color get() = ThemeState.palette.divider
val AppBorder: Color get() = ThemeState.palette.border

val AppPrimary: Color get() = ThemeState.palette.primary
val AppOnPrimary: Color get() = ThemeState.palette.onPrimary
val AppPrimaryPressed: Color get() = ThemeState.palette.primaryPressed
val AppPrimaryTint: Color get() = ThemeState.palette.primaryTint
val AppOnVerdict: Color get() = ThemeState.palette.onVerdict

// ألوان الأحكام
val VerdictAccept: Color get() = ThemeState.palette.accept
val VerdictAcceptBg: Color get() = ThemeState.palette.acceptBg
val VerdictCheck: Color get() = ThemeState.palette.check
val VerdictCheckBg: Color get() = ThemeState.palette.checkBg
val VerdictConsider: Color get() = ThemeState.palette.consider
val VerdictConsiderBg: Color get() = ThemeState.palette.considerBg
val VerdictReject: Color get() = ThemeState.palette.reject
val VerdictRejectBg: Color get() = ThemeState.palette.rejectBg
val VerdictWait: Color get() = ThemeState.palette.wait
val VerdictWaitBg: Color get() = ThemeState.palette.waitBg

// ألوان تطبيقات التوصيل (ثابتة)
val UberBlack = Color(0xFF000000)
val DidiOrange = Color(0xFFFF7D00)
val IndriveGreen = Color(0xFF1E9E55)
