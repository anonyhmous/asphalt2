package com.asphalt.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.asphalt.app.R

// Custom Font Families from locally downloaded TTF files
val IbmPlexSansArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic, FontWeight.Normal)
)

val InterFont = FontFamily(
    Font(R.font.inter, FontWeight.Normal)
)

// iOS-style Typography Scale tailored for Arabic RTL with English/Digit support
val Typography = Typography(
    // 34 Bold - Large Title
    displayLarge = TextStyle(
        fontFamily = IbmPlexSansArabic,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp
    ),
    // 28 Bold
    displayMedium = TextStyle(
        fontFamily = IbmPlexSansArabic,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    // 22 Semibold - Title
    titleLarge = TextStyle(
        fontFamily = IbmPlexSansArabic,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    // 20 Medium
    titleMedium = TextStyle(
        fontFamily = IbmPlexSansArabic,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 25.sp
    ),
    // 17 Semibold / Headline
    headlineSmall = TextStyle(
        fontFamily = IbmPlexSansArabic,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    // 17 Regular - Body
    bodyLarge = TextStyle(
        fontFamily = IbmPlexSansArabic,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    // 15 Regular - Subheadline
    bodyMedium = TextStyle(
        fontFamily = IbmPlexSansArabic,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    // 12 Regular - Caption
    labelSmall = TextStyle(
        fontFamily = IbmPlexSansArabic,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    // Numbers & Metrics (Inter font)
    labelLarge = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    )
)
