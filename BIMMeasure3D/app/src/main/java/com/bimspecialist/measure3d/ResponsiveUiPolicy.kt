package com.bimspecialist.measure3d

/**
 * Responsive visual rules expressed as density-independent dimensions.
 * No device-model checks: the Infinix GT 20 Pro's tall screen and Android
 * display-size/font settings are handled through the actual available bounds.
 */
internal data class ScreenLayoutProfile(
    val landscape: Boolean,
    val compact: Boolean,
    val horizontalPaddingDp: Int,
    val cardIconDp: Int,
    val cardMinHeightDp: Int,
    val panelMaxHeightDp: Int,
    val sidePanelWidthDp: Int,
    val primaryButtonHeightDp: Int,
    val showHomeTagline: Boolean
)

internal object ResponsiveUiPolicy {
    fun forBounds(widthDp: Int, heightDp: Int, fontScale: Float): ScreenLayoutProfile {
        require(widthDp > 0 && heightDp > 0 && fontScale.isFinite() && fontScale > 0f)
        val landscape = widthDp > heightDp
        val compact = widthDp < 385 || heightDp < 740 || fontScale > 1.2f
        val horizontal = if (widthDp < 360) 12 else 16
        val icon = if (compact) 54 else 66
        val homeCard = if (compact) 94 else 106
        val panelHeight = if (landscape) {
            heightDp
        } else {
            minOf(
                if (fontScale >= 1.3f) 410 else 360,
                maxOf(235, (heightDp * if (compact) 0.48f else 0.42f).toInt())
            )
        }
        val sideWidth = minOf(345, maxOf(260, (widthDp * 0.43f).toInt()))
        return ScreenLayoutProfile(
            landscape = landscape,
            compact = compact,
            horizontalPaddingDp = horizontal,
            cardIconDp = icon,
            cardMinHeightDp = homeCard,
            panelMaxHeightDp = panelHeight,
            sidePanelWidthDp = sideWidth,
            primaryButtonHeightDp = 52,
            showHomeTagline = heightDp >= 700 && !landscape
        )
    }
}
