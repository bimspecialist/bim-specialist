package com.bimspecialist.measure3d

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.bimspecialist.measure3d.core.DisplayUnit

/**
 * Visual design references in Android Studio; not ARCore or on-device validation.
 * Use the supplied aspect ratio and compare with a real GT 20 Pro screenshot.
 */
@Preview(
    name = "Infinix GT 20 Pro · Arabic home",
    device = "spec:width=393dp,height=880dp,dpi=393",
    locale = "ar",
    showSystemUi = true
)
@Composable
private fun Gt20ArabicHomePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        ModernHomeScreen(
            arabic = true,
            projectName = "مشروع تجريبي",
            savedCount = 3,
            storageReady = true,
            newUpdate = false,
            updateMessage = null,
            arCapability = com.bimspecialist.measure3d.core.ArCapability.READY,
            onRetryAr = {}, onGetArServices = {}, onManual = {},
            onStart = {}, onProjects = {}, onHistory = {}, onUpdates = {}
        )
    }
}

@Preview(
    name = "Infinix GT 20 Pro · Arabic camera",
    device = "spec:width=393dp,height=880dp,dpi=393",
    locale = "ar",
    showSystemUi = true
)
@Composable
private fun Gt20ArabicCameraPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(Color(0xFF284D60))) {
            ModernCameraOverlay(
                arabic = true,
                mode = ToolMode.LENGTH,
                unit = DisplayUnit.METRE,
                measured = 1.25,
                perimeter = null,
                pointCount = 2,
                scanning = false,
                scanCount = 0,
                isTracking = true,
                anchorsStable = true,
                storageReady = true,
                statusMessage = "",
                onHome = {}, onMore = {}, onHistory = {},
                onUndo = {}, onClear = {}, onSave = {}, onExport = {},
                onUnit = {}, onToggleScan = {}, onPreview = {}
            )
        }
    }
}

@Preview(
    name = "GT 20 Pro · Larger text",
    device = "spec:width=393dp,height=880dp,dpi=393",
    locale = "ar",
    fontScale = 1.35f,
    showSystemUi = true
)
@Composable
private fun Gt20LargerFontsPreview() = Gt20ArabicHomePreview()
