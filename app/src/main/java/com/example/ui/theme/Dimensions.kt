package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object FeatherDimensions {
    val SpacingMicro = 4.dp
    val SpacingSmall = 8.dp
    val SpacingMedium = 12.dp
    val SpacingStandard = 16.dp
    val SpacingLarge = 24.dp
    val SpacingExtraLarge = 32.dp

    val ControlHeightSmall = 36.dp
    val ControlHeightStandard = 44.dp
    val BottomBarHeight = 58.dp
    val FloatingDockPadding = 12.dp

    val RadiusSmall = 12.dp
    val RadiusCard = 18.dp
    val RadiusLargeCard = 22.dp
    val RadiusAddressBar = 28.dp
    val RadiusFloatingDock = 30.dp
    val RadiusPill = 32.dp
}

object FeatherShapes {
    val SmallControl = RoundedCornerShape(FeatherDimensions.RadiusSmall)
    val Card = RoundedCornerShape(FeatherDimensions.RadiusCard)
    val LargeCard = RoundedCornerShape(FeatherDimensions.RadiusLargeCard)
    val AddressBar = RoundedCornerShape(FeatherDimensions.RadiusAddressBar)
    val FloatingDock = RoundedCornerShape(FeatherDimensions.RadiusFloatingDock)
    val Pill = RoundedCornerShape(FeatherDimensions.RadiusPill)
}
