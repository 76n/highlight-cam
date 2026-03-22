package com.highlightcam.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.highlightcam.app.R

private val provider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

private val fontName = GoogleFont("Inter")

private val InterFontFamily =
    FontFamily(
        Font(googleFont = fontName, fontProvider = provider, weight = FontWeight.Normal),
        Font(googleFont = fontName, fontProvider = provider, weight = FontWeight.Medium),
        Font(googleFont = fontName, fontProvider = provider, weight = FontWeight.SemiBold),
        Font(googleFont = fontName, fontProvider = provider, weight = FontWeight.Bold),
    )

object HCType {
    val nums =
        TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 48.sp,
            letterSpacing = (-2).sp,
        )
    val heading =
        TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            letterSpacing = (-0.5).sp,
        )
    val title =
        TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            letterSpacing = (-0.2).sp,
        )
    val body =
        TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            letterSpacing = 0.sp,
        )
    val label =
        TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 0.3.sp,
        )
    val micro =
        TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
        )
}

val Typography =
    Typography(
        displayLarge = HCType.nums,
        headlineLarge = HCType.heading,
        titleLarge = HCType.heading,
        titleMedium = HCType.title,
        titleSmall = HCType.label,
        bodyLarge = HCType.body,
        bodyMedium = HCType.body,
        bodySmall = HCType.micro,
        labelLarge = HCType.label,
        labelMedium = HCType.label,
        labelSmall = HCType.micro,
    )
