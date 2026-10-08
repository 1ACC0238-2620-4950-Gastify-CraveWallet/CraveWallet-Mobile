package com.cravewallet.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cravewallet.app.R

val Poppins = FontFamily(
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private fun inter(weight: FontWeight) = Font(
    R.font.inter_variable,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Inter = FontFamily(
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold),
)

/** Escala tipográfica del Design System. */
object CwType {
    /** Monto principal · Poppins 700 · 32 sp */
    val Amount = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp)
    /** Headline Medium · Poppins 600 · 28 sp */
    val Headline = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp)
    /** Title Large · Poppins 600 · 22 sp */
    val Title = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
    /** Heading 2 · Poppins 600 · 18 sp */
    val Heading = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    /** Body Large · Inter 400 · 16 sp */
    val BodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    /** Body Medium · Inter 400 · 14 sp */
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    /** Body enfatizado · Inter 500 · 14 sp */
    val BodyStrong = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
    /** Caption · Inter 400 · 12 sp */
    val Caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    /** Label Small · Inter 400 · 11 sp */
    val Label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp)
    /** Botones · Inter 600 · 16 sp */
    val Button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp)
    val ButtonSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
}

val AppTypography = Typography(
    displaySmall = CwType.Amount,
    headlineMedium = CwType.Headline,
    titleLarge = CwType.Title,
    titleMedium = CwType.Heading,
    titleSmall = CwType.BodyStrong,
    bodyLarge = CwType.BodyLarge,
    bodyMedium = CwType.Body,
    bodySmall = CwType.Caption,
    labelLarge = CwType.ButtonSmall,
    labelMedium = CwType.Caption,
    labelSmall = CwType.Label,
)
