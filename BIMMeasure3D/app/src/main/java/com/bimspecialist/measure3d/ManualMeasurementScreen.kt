package com.bimspecialist.measure3d

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimspecialist.measure3d.core.*

/**
 * Tape-measure input mode; deliberately independent of ARCore and the camera.
 * The project saves the record with source = MANUAL so it is never mistaken for AR.
 */
@Composable
internal fun ManualMeasurementScreen(
    arabic: Boolean,
    projectName: String,
    projectId: String,
    storageReady: Boolean,
    isSaving: Boolean,
    lastSaved: ManualMeasurement?,
    lastSavedProjectId: String?,
    notice: String?,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onSave: (ManualMeasurement) -> Unit
) {
    fun tr(ar: String, en: String) = if (arabic) ar else en
    var mode by rememberSaveable { mutableStateOf(ManualMeasureMode.LENGTH) }
    var unit by rememberSaveable { mutableStateOf(DisplayUnit.METRE) }
    var lengthText by rememberSaveable { mutableStateOf("") }
    var widthText by rememberSaveable { mutableStateOf("") }
    val calculated = remember(mode, unit, lengthText, widthText) {
        runCatching { ManualMeasurementCalculator.calculate(mode, lengthText, widthText, unit) }.getOrNull()
    }
    val alreadySaved = RecordSavePolicy.manualAlreadySaved(
        calculated, projectId, lastSaved, lastSavedProjectId
    )

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF081627), Color(0xFF163B5C), Color(0xFF081627))))
    ) {
        val layout = ResponsiveUiPolicy.forBounds(
            maxWidth.value.toInt(), maxHeight.value.toInt(), LocalDensity.current.fontScale
        )
        Column(
            Modifier.fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = layout.horizontalPaddingDp.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(tr("رجوع", "Back"), color = Color(0xFF8DD2FF))
                }
                Spacer(Modifier.weight(1f))
                Text(tr("قياس يدوي", "Manual measure"), color = Color.White,
                    fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }

            Surface(
                color = Color(0xFF1B4260), shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFF3E7EA3))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("يعمل بدون ARCore", "Works without ARCore"), color = Color(0xFF9FE3C9),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("أدخل الأبعاد التي قستها بنفسك بشريط قياس. هذا الوضع لا يقيس بالكاميرا.",
                           "Enter dimensions measured with a physical tape. This mode does not measure with the camera."),
                        color = Color(0xFFE2F0FC), fontSize = 13.sp, lineHeight = 19.sp
                    )
                }
            }

            Text(tr("نوع القياس", "Measurement type"), color = Color.White, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == ManualMeasureMode.LENGTH,
                    onClick = { mode = ManualMeasureMode.LENGTH },
                    label = { Text(tr("الطول", "Length")) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                FilterChip(
                    selected = mode == ManualMeasureMode.RECTANGULAR_AREA,
                    onClick = { mode = ManualMeasureMode.RECTANGULAR_AREA },
                    label = { Text(tr("مساحة مستطيل", "Rectangle area")) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }

            Text(tr("وحدة إدخال الأبعاد", "Input dimension unit"), color = Color.White, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DisplayUnit.entries.forEach { option ->
                    FilterChip(
                        selected = unit == option,
                        onClick = { unit = option },
                        label = { Text(option.label) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            OutlinedTextField(
                value = lengthText,
                onValueChange = { if (it.length <= 20) lengthText = it },
                label = { Text(tr("الطول (${unit.label})", "Length (${unit.label})")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(tr("أدخل قيمة أكبر من صفر", "Enter a positive number")) }
            )
            if (mode == ManualMeasureMode.RECTANGULAR_AREA) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { if (it.length <= 20) widthText = it },
                    label = { Text(tr("العرض (${unit.label})", "Width (${unit.label})")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Surface(
                color = Color(0xFF102940),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("النتيجة", "Calculated result"), fontSize = 13.sp,
                        color = Color(0xFFB6CBDE))
                    Text(
                        when {
                            calculated == null -> tr("أدخل الأبعاد الصحيحة", "Enter valid dimensions")
                            mode == ManualMeasureMode.LENGTH ->
                                MeasureFormat.distance(calculated.valueMetresOrSquareMetres, unit)
                            else -> MeasureFormat.area(calculated.valueMetresOrSquareMetres, unit)
                        },
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp
                    )
                    if (calculated != null && mode == ManualMeasureMode.RECTANGULAR_AREA) {
                        Text(
                            tr("المحيط: ", "Perimeter: ") +
                                MeasureFormat.distance(calculated.perimeterMetres!!, unit),
                            fontSize = 14.sp, color = Color(0xFF9BD6FF)
                        )
                        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                            val insetX = size.width * 0.16f
                            val insetY = size.height * 0.13f
                            val w = size.width - insetX * 2
                            val h = size.height - insetY * 2
                            drawRect(
                                Color(0xFF3AA9F4).copy(alpha = 0.12f),
                                topLeft = Offset(insetX, insetY),
                                size = androidx.compose.ui.geometry.Size(w, h)
                            )
                            drawRect(
                                Color(0xFF63C0FF),
                                topLeft = Offset(insetX, insetY),
                                size = androidx.compose.ui.geometry.Size(w, h),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                    Text(
                        tr("الشكل توضيحي ولا يمثل موقعًا مساحيًا حقيقيًا.",
                            "Diagram is schematic, without survey coordinates."),
                        color = Color(0xFFBDD0E0), fontSize = 11.sp
                    )
                }
            }

            Button(
                onClick = { calculated?.let(onSave) },
                enabled = calculated != null && storageReady && !isSaving && !alreadySaved,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF48B2FF))
            ) {
                Text(
                    when {
                        isSaving -> tr("جارٍ الحفظ", "Saving")
                        alreadySaved -> tr("تم الحفظ في المشروع", "Saved to project")
                        else -> tr("حفظ في المشروع", "Save to project")
                    },
                    color = Color(0xFF071729), fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(tr("عرض سجل المشروع", "Open project history"))
            }
            Text(tr("المشروع: ", "Project: ") + projectName, fontSize = 12.sp,
                color = Color(0xFFB6CBDE))
            if (!notice.isNullOrBlank()) {
                Text(notice, fontSize = 12.sp, color = Color(0xFFB9E4FF))
            }
        }
    }
}
