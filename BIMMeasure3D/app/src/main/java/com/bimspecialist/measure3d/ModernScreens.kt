package com.bimspecialist.measure3d

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.bimspecialist.measure3d.core.DisplayUnit
import com.bimspecialist.measure3d.core.MeasureFormat
import java.util.Locale

private val navy = Color(0xFF081627)
private val midnight = Color(0xFF0D2442)
private val electric = Color(0xFF48B2FF)
private val sky = Color(0xFFB1DEFF)
private val muted = Color(0xFFB6CBDE)
private val panel = Color(0xFF112941)

@Composable
internal fun ModernHomeScreen(
    arabic: Boolean,
    projectName: String,
    savedCount: Int,
    storageReady: Boolean,
    newUpdate: Boolean,
    updateMessage: String?,
    onStart: (ToolMode) -> Unit,
    onProjects: () -> Unit,
    onHistory: () -> Unit,
    onUpdates: () -> Unit
) {
    fun tr(a: String, b: String) = if (arabic) a else b
    BoxWithConstraints(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(navy, midnight, navy))
    )) {
        val profile = ResponsiveUiPolicy.forBounds(
            maxWidth.value.toInt(), maxHeight.value.toInt(), LocalDensity.current.fontScale
        )
        Column(
            modifier = Modifier.fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = profile.horizontalPaddingDp.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (profile.compact) 10.dp else 14.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("BIM MEASURE", color = Color.White,
                    fontSize = if (profile.compact) 21.sp else 25.sp,
                    fontWeight = FontWeight.ExtraBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text("3D  ·  BIM SPECIALIST", color = sky,
                    fontSize = 11.sp, letterSpacing = 1.5.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onUpdates, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (newUpdate) tr("تحديث متاح", "Update") else tr("التحديثات", "Updates"),
                    fontSize = 12.sp, color = sky)
            }
        }
        if (!updateMessage.isNullOrBlank()) {
            Text(updateMessage, color = sky, fontSize = 12.sp, maxLines = 2)
        }

        Surface(
            color = Color(0xFF163956),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(if (profile.compact) 13.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    tr("القياس الذكي بكاميرا الجوال", "Measure with your phone"),
                    fontSize = if (profile.compact) 19.sp else 23.sp,
                    fontWeight = FontWeight.Bold, color = Color.White
                )
                Text(
                    tr("اختر أداة واحدة، وجّه الكاميرا إلى السطح، ثم احفظ النتيجة.",
                       "Choose a tool, point the camera at a surface, and save your result."),
                    fontSize = 13.sp, color = muted, lineHeight = 20.sp
                )
                Spacer(Modifier.height(4.dp))
                if (profile.showHomeTagline) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabelPill(tr("بدون حساب", "No account"))
                        LabelPill(tr("حفظ محلي", "Local saving"))
                    }
                }
            }
        }

        Text(tr("ماذا تريد أن تقيس؟", "What would you like to measure?"),
            color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)

        ModeCard(
            ToolMode.LENGTH,
            tr("قياس الطول", "Measure length"),
            tr("اختر نقطتين واحصل على الطول فورًا", "Tap two points to measure distance"),
            tr("ابدأ القياس", "Start measuring"),
            iconDp = profile.cardIconDp,
            minHeightDp = profile.cardMinHeightDp,
            onClick = { onStart(ToolMode.LENGTH) }
        )
        ModeCard(
            ToolMode.AREA,
            tr("قياس المساحة", "Measure area"),
            tr("حدد الزوايا لعرض المساحة والمحيط", "Mark corners to calculate area and perimeter"),
            tr("ابدأ القياس", "Start measuring"),
            iconDp = profile.cardIconDp,
            minHeightDp = profile.cardMinHeightDp,
            onClick = { onStart(ToolMode.AREA) }
        )
        ModeCard(
            ToolMode.SCAN,
            tr("المسح ثلاثي الأبعاد", "3D point scan"),
            tr("التقاط سحابة نقاط تجريبية حول الجسم", "Capture an experimental 3D point cloud"),
            tr("ابدأ المسح", "Start scanning"),
            iconDp = profile.cardIconDp,
            minHeightDp = profile.cardMinHeightDp,
            onClick = { onStart(ToolMode.SCAN) }
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = panel,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(tr("مشروعك الحالي", "Current project"), color = muted, fontSize = 12.sp)
                Text(projectName, color = Color.White, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tr("قياسات محفوظة: $savedCount", "$savedCount saved measurements"),
                    color = sky, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onProjects, enabled = storageReady,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text(tr("المشاريع", "Projects"), fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = onHistory, enabled = storageReady,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text(tr("سجل القياسات", "History"), fontSize = 13.sp) }
                }
            }
        }
        Text(
            tr("النتائج تقديرية وتعتمد على ARCore. تحقق من الأبعاد بشريط قياس قبل الاستخدام الهندسي.",
                "ARCore measurements are estimates. Verify dimensions with a tape before engineering use."),
            color = muted, fontSize = 11.sp, lineHeight = 17.sp
        )
        }
    }
}

@Composable
private fun LabelPill(label: String) {
    Surface(color = Color(0xFF255476), shape = RoundedCornerShape(50)) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 11.sp, color = Color(0xFFD1EBFF))
    }
}

@Composable
private fun ModeCard(
    mode: ToolMode,
    title: String,
    description: String,
    action: String,
    iconDp: Int,
    minHeightDp: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = minHeightDp.dp),
        color = Color(0xFF142C47),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, electric.copy(alpha = 0.28f))
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = if (minHeightDp < 100) 9.dp else 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(iconDp.dp).background(
                    Brush.linearGradient(listOf(Color(0xFF1E507B), Color(0xFF153654))),
                    RoundedCornerShape(16.dp)
                ),
                contentAlignment = Alignment.Center
            ) { ModeSymbol(mode, Modifier.size((iconDp * 0.72f).dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = if (minHeightDp < 100) 16.sp else 17.sp,
                    fontWeight = FontWeight.Bold, color = Color.White)
                Text(description, fontSize = 12.sp, color = muted, lineHeight = 17.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(action, fontSize = 12.sp, color = electric, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ModeSymbol(mode: ToolMode, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sx = size.width / 100f
        val sy = size.height / 100f
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
        when (mode) {
            ToolMode.LENGTH -> {
                drawLine(electric, p(13f, 55f), p(86f, 34f), strokeWidth = 4f * sx)
                drawCircle(Color.White, radius = 7f * sx, center = p(13f, 55f))
                drawCircle(Color.White, radius = 7f * sx, center = p(86f, 34f))
            }
            ToolMode.AREA -> {
                val polygon = Path().apply {
                    moveTo(15f * sx, 32f * sy)
                    lineTo(78f * sx, 19f * sy)
                    lineTo(88f * sx, 70f * sy)
                    lineTo(23f * sx, 82f * sy)
                    close()
                }
                drawPath(polygon, electric.copy(alpha = 0.2f))
                drawPath(polygon, electric, style = Stroke(width = 3f * sx))
                listOf(p(15f,32f),p(78f,19f),p(88f,70f),p(23f,82f)).forEach {
                    drawCircle(Color.White, 5f * sx, it)
                }
            }
            ToolMode.SCAN -> {
                val front = listOf(p(24f,40f),p(56f,55f),p(56f,88f),p(24f,72f))
                val back = listOf(p(56f,17f),p(88f,32f),p(88f,65f),p(56f,50f))
                for (i in 0..3) {
                    drawLine(electric, front[i], front[(i+1)%4], strokeWidth=2.8f*sx)
                    drawLine(electric, back[i], back[(i+1)%4], strokeWidth=2.8f*sx)
                }
                for (i in 0..3) drawLine(sky, front[i], back[i], strokeWidth = 2f*sx)
                drawCircle(Color.White, 3f*sx, p(56f,55f))
            }
        }
    }
}

@Composable
internal fun ModernCameraOverlay(
    arabic: Boolean,
    mode: ToolMode,
    unit: DisplayUnit,
    measured: Double?,
    perimeter: Double?,
    pointCount: Int,
    scanning: Boolean,
    scanCount: Int,
    isTracking: Boolean,
    anchorsStable: Boolean,
    storageReady: Boolean,
    statusMessage: String,
    onHome: () -> Unit,
    onMore: () -> Unit,
    onHistory: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onUnit: (DisplayUnit) -> Unit,
    onToggleScan: () -> Unit,
    onPreview: () -> Unit
) {
    fun tr(a: String, b: String) = if (arabic) a else b
    val title = when (mode) {
        ToolMode.LENGTH -> tr("قياس الطول", "Length")
        ToolMode.AREA -> tr("قياس المساحة", "Area")
        ToolMode.SCAN -> tr("مسح 3D", "3D point scan")
    }
    val ready = CaptureUiPolicy.canSaveMeasurement(
        mode, measured != null, isTracking, anchorsStable, storageReady
    )
    val dataReady = CaptureUiPolicy.canExport(
        mode, measured != null, isTracking, anchorsStable, storageReady,
        scanCount, scanning
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val profile = ResponsiveUiPolicy.forBounds(
            maxWidth.value.toInt(), maxHeight.value.toInt(), LocalDensity.current.fontScale
        )
        if (mode != ToolMode.SCAN && pointCount == 0) {
            Canvas(Modifier.align(Alignment.Center).size(54.dp)) {
                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                val length = size.width * 0.17f
                val gap = size.width * 0.075f
                val lineColor = Color.White.copy(alpha = if (isTracking) 0.8f else 0.35f)
                drawLine(lineColor, Offset(cx - length - gap, cy),
                    Offset(cx - gap, cy), strokeWidth = 2.dp.toPx())
                drawLine(lineColor, Offset(cx + gap, cy),
                    Offset(cx + length + gap, cy), strokeWidth = 2.dp.toPx())
                drawLine(lineColor, Offset(cx, cy - length - gap),
                    Offset(cx, cy - gap), strokeWidth = 2.dp.toPx())
                drawLine(lineColor, Offset(cx, cy + gap),
                    Offset(cx, cy + length + gap), strokeWidth = 2.dp.toPx())
            }
        }
        Row(
            Modifier.width(
                if (profile.landscape)
                    (maxWidth.value - profile.sidePanelWidthDp).coerceAtLeast(170f).dp
                else maxWidth
            ).align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                ))
                .background(Color(0xEC081627))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onHome) { Text(tr("الرئيسية", "Home"), color = sky, fontSize = 13.sp) }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                Text(
                    if (mode == ToolMode.SCAN) tr("نقاط ملتقطة: $scanCount", "$scanCount points")
                    else tr("النقاط: $pointCount", "$pointCount points"),
                    color = muted, fontSize = 11.sp
                )
            }
            TextButton(onClick = onMore) {
                Text(tr("المزيد", "More"), color = sky, fontSize = 13.sp)
            }
        }
        Column(
            Modifier
                .align(if (profile.landscape) Alignment.CenterEnd else Alignment.BottomCenter)
                .then(
                    if (profile.landscape)
                        Modifier.width(profile.sidePanelWidthDp.dp).fillMaxHeight()
                    else
                        Modifier.fillMaxWidth().heightIn(max = profile.panelMaxHeightDp.dp)
                )
                .background(
                    Color(0xF4081627),
                    if (profile.landscape) RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                    else RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp)
                )
                .then(
                    if (profile.landscape) Modifier.safeDrawingPadding()
                    else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    ))
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = profile.horizontalPaddingDp.dp,
                    vertical = if (profile.compact) 10.dp else 13.dp),
            verticalArrangement = Arrangement.spacedBy(if (profile.compact) 6.dp else 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                val statusColor = if (isTracking) Color(0xFF75E2C3) else Color(0xFFFFD48F)
                Surface(
                    modifier = Modifier.weight(1f, fill = false),
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(40)
                ) {
                    Text(
                        when {
                            !isTracking -> tr("جاري البحث عن سطح", "Searching for surface")
                            mode != ToolMode.SCAN && pointCount > 0 && !anchorsStable ->
                                tr("انتظر ثبات النقاط", "Stabilizing points")
                            else -> tr("الكاميرا جاهزة", "Camera ready")
                        },
                        color = statusColor, fontSize = if (profile.compact) 10.sp else 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
                if (mode != ToolMode.SCAN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DisplayUnit.entries.forEach { choice ->
                            Surface(
                                onClick = { onUnit(choice) },
                                modifier = Modifier.widthIn(min = 48.dp).height(44.dp),
                                color = if (choice == unit) electric else Color(0xFF28445D),
                                shape = RoundedCornerShape(9.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(choice.label, fontSize = 11.sp,
                                        color = if (choice == unit) navy else Color.White,
                                        maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            val mainValue = when (mode) {
                ToolMode.LENGTH -> measured?.let { MeasureFormat.distance(it, unit) }
                    ?: tr("حدد نقطتين على الصورة", "Tap two points on the camera")
                ToolMode.AREA -> measured?.let { MeasureFormat.area(it, unit) }
                    ?: tr("حدد زوايا المساحة", "Tap the area corners")
                ToolMode.SCAN -> tr("$scanCount نقطة", "$scanCount captured points")
            }
            Text(mainValue, color = Color.White, fontSize = if (measured != null) 28.sp else 18.sp,
                fontWeight = FontWeight.Bold, maxLines = 2)
            if (mode == ToolMode.AREA && perimeter != null) {
                Text(tr("المحيط: ", "Perimeter: ") + MeasureFormat.distance(perimeter, unit),
                    color = sky, fontSize = 13.sp)
            }
            if (!profile.compact || measured == null) {
            Text(
                when {
                    mode == ToolMode.SCAN -> tr(
                        "حرّك الجوال ببطء حول الجسم. النتيجة سحابة نقاط تجريبية.",
                        "Move slowly around the object. Sparse point cloud only.")
                    measured == null -> if (mode == ToolMode.LENGTH)
                        tr("اضغط على بداية ونهاية المسافة داخل الصورة.", "Tap the start and end points on the image.")
                    else tr("اضغط على زوايا الشكل بالترتيب.", "Tap the corners in order.")
                    else -> tr("تحقق من النتيجة قبل حفظها.", "Verify the result before saving.")
                },
                color = muted, fontSize = 11.sp, maxLines = 2,
                lineHeight = 16.sp
            )
            }
            if (mode == ToolMode.SCAN) {
                Button(
                    onClick = onToggleScan,
                    modifier = Modifier.fillMaxWidth().height(profile.primaryButtonHeightDp.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = electric),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text(if (scanning) tr("إيقاف المسح", "Pause scan")
                        else if (scanCount > 0) tr("استكمال المسح", "Resume scan")
                        else tr("بدء المسح", "Start scanning"),
                        color = navy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedButton(onClick = onPreview, enabled = dataReady, modifier = Modifier.weight(1f)) {
                        Text(tr("معاينة", "Preview"))
                    }
                    OutlinedButton(onClick = onExport, enabled = dataReady, modifier = Modifier.weight(1f)) {
                        Text(tr("تصدير", "Export"))
                    }
                    TextButton(onClick = onClear, enabled = scanCount > 0,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(tr("مسح", "Clear"), maxLines = 1)
                    }
                }
            } else {
                Button(
                    onClick = onSave, enabled = ready,
                    modifier = Modifier.fillMaxWidth().height(profile.primaryButtonHeightDp.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = electric),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text(tr("حفظ القياس", "Save measurement"),
                        fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = if (ready) navy else Color.LightGray)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = onUndo, enabled = pointCount > 0, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp)) { Text(tr("تراجع", "Undo"), fontSize = 13.sp) }
                    OutlinedButton(onClick = onClear, enabled = pointCount > 0, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp)) { Text(tr("إعادة", "Reset"), fontSize = 13.sp) }
                    OutlinedButton(onClick = onExport, enabled = dataReady, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp)) { Text(tr("تصدير", "Export"), fontSize = 13.sp) }
                }
            }
            if (statusMessage.isNotBlank() && (statusMessage.contains("failed", true) ||
                        statusMessage.contains("تعذر") || statusMessage.contains("فشل"))) {
                Text(statusMessage, color = Color(0xFFFFD48F), fontSize = 11.sp, maxLines = 2)
            }
        }
    }
}
