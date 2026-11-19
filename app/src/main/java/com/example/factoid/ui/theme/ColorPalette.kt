package com.example.factoid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

object ColorPalette {

    val names: List<String> = listOf(
        "Жёлтый",
        "Жёлто-оранжевый",
        "Оранжевый",
        "Красно-оранжевый",
        "Красный",
        "Красно-фиолетовый",
        "Фиолетовый",
        "Сине-фиолетовый",
        "Синий",
        "Сине-зелёный",
        "Зелёный",
        "Жёлто-зелёный"
    )

    val colors: List<Color> = listOf(
        Color(0xFFF7E27E),
        Color(0xFFF8CF7A),
        Color(0xFFF5B66E),
        Color(0xFFEF9A7A),
        Color(0xFFE88B8B),
        Color(0xFFD98BB0),
        Color(0xFFB997D1),
        Color(0xFF9A9BD6),
        Color(0xFF8DB4E2),
        Color(0xFF83CBC4),
        Color(0xFF9CCB92),
        Color(0xFFC5D87F)
    )

    val size: Int get() = colors.size

    private val darkContent = Color(0xFF1C1B1F)
    private val lightContent = Color(0xFFFFFFFF)

    fun normalizeIndex(index: Int): Int = index.mod(colors.size)

    fun colorAt(index: Int): Color = colors[normalizeIndex(index)]

    fun nameAt(index: Int): String = names[normalizeIndex(index)]

    fun contentColorFor(background: Color): Color {
        val backgroundLuminance = background.luminance()
        val darkContrast = contrastRatio(backgroundLuminance, darkContent.luminance())
        val lightContrast = contrastRatio(backgroundLuminance, lightContent.luminance())
        return if (darkContrast >= lightContrast) darkContent else lightContent
    }

    fun contentColorAt(index: Int): Color = contentColorFor(colorAt(index))

    private fun contrastRatio(first: Float, second: Float): Float {
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}

fun Color.contentColor(): Color = ColorPalette.contentColorFor(this)