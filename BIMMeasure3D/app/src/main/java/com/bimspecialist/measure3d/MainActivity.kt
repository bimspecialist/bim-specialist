package com.bimspecialist.measure3d

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimspecialist.measure3d.core.*
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

private enum class ToolMode { LENGTH, AREA, SCAN }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MeasureApp() }
    }
}

private val deepNavy = Color(0xFF0A1425)
private val brightBlue = Color(0xFF45ABFF)
private val paleBlue = Color(0xFFB7E0FF)
private val warningAmber = Color(0xFFFFD08B)

@Composable
private fun MeasureApp() {
    val context = LocalContext.current
    val arabic = remember { Locale.getDefault().language == "ar" }
    fun tr(ar: String, en: String) = if (arabic) ar else en

    val store = remember(context) { MeasurementStore(context) }
    var savedRecords by remember { mutableStateOf(store.load()) }
    var showHistory by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<ToolMode?>(null) }
    var showScanReset by remember { mutableStateOf(false) }

    var mode by remember { mutableStateOf(ToolMode.LENGTH) }
    var unit by remember { mutableStateOf(DisplayUnit.METRE) }
    val anchors = remember { mutableStateListOf<Anchor>() }
    val sampler = remember { PointSampler() }
    var scanning by remember { mutableStateOf(false) }
    var scanCount by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<List<Vec3>?>(null) }
    val frameRef = remember { arrayOfNulls<Frame>(1) }
    val updateNanos = remember { longArrayOf(0L) }
    val frameCounter = remember { intArrayOf(0) }
    var viewWidth by remember { mutableIntStateOf(0) }
    var viewHeight by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(emptyList<ScreenPoint>()) }
    var isTracking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(tr(
        "حرّك الهاتف لاكتشاف السطح، ثم انقر لتثبيت نقاط القياس.",
        "Move the phone to detect a plane, then tap to place measurement points."
    )) }

    var pendingData by remember { mutableStateOf<ByteArray?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingData
        if (uri != null && bytes != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("Cannot open selected document")
                message = tr("تم حفظ الملف في الموقع الذي اخترته.", "File saved in selected location.")
            } catch (e: Exception) {
                message = tr("تعذر حفظ الملف: ", "Save failed: ") + (e.localizedMessage ?: "")
            }
        }
        pendingData = null
    }
    fun export(filename: String, bytes: ByteArray) {
        pendingData = bytes
        exportLauncher.launch(filename)
    }
    fun clearAnchors() {
        anchors.forEach { it.detach() }
        anchors.clear()
        overlay = emptyList()
    }
    fun resetScan() {
        scanning = false
        preview = null
        sampler.clear()
        scanCount = 0
        frameCounter[0] = 0
    }
    fun switchNow(next: ToolMode) {
        clearAnchors()
        resetScan()
        mode = next
        message = when (next) {
            ToolMode.LENGTH -> tr("انقر على نقطتين على السطح لقياس المسافة.", "Tap two points on a detected plane.")
            ToolMode.AREA -> tr("حدد زوايا المساحة بالترتيب حول المحيط.", "Tap corner points around the boundary.")
            ToolMode.SCAN -> tr("اجمع سحابة نقاط أولية. ليست شبكة مجسم للطباعة.", "Capture a sparse point cloud, not a printable mesh.")
        }
    }
    fun requestMode(next: ToolMode) {
        if (next == mode) return
        if (anchors.isNotEmpty() || sampler.count() > 0) pendingMode = next else switchNow(next)
    }
    DisposableEffect(Unit) {
        onDispose { anchors.forEach { it.detach() }; frameRef[0] = null }
    }
    val vertices = anchors.map { anchor ->
        val t = anchor.pose.translation
        Vec3(t[0].toDouble(), t[1].toDouble(), t[2].toDouble())
    }
    val measured: Double? = try {
        when (mode) {
            ToolMode.LENGTH -> if (vertices.size == 2) Geometry.length(vertices[0], vertices[1]) else null
            ToolMode.AREA -> if (vertices.size >= 3) Geometry.planarArea(vertices) else null
            ToolMode.SCAN -> null
        }
    } catch (_: IllegalArgumentException) { null }
    val error: String? = if (mode == ToolMode.AREA && vertices.size >= 3 && measured == null)
        tr("المضلع غير صالح. تحقق من تقاطع الأضلاع واستواء السطح.", "Invalid polygon. Check for crossing edges or nonplanar points.")
    else null
    val perimeter = if (mode == ToolMode.AREA && measured != null)
        Geometry.perimeter(vertices, closed = true) else null

    fun saveCurrent() {
        val value = measured ?: return
        val kind = if (mode == ToolMode.LENGTH) "LENGTH" else "AREA"
        try {
            store.add(kind, value, perimeter, vertices)
            savedRecords = store.load()
            message = tr("تم حفظ القياس محليًا في السجل.", "Measurement saved locally.")
        } catch (e: Exception) {
            message = tr("تعذر حفظ القياس: ", "Cannot save measurement: ") + (e.localizedMessage ?: "")
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = brightBlue, background = deepNavy)) {
        Box(Modifier.fillMaxSize().background(deepNavy).onSizeChanged {
            viewWidth = it.width
            viewHeight = it.height
        }) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                depthMode = Config.DepthMode.DISABLED,
                planeRenderer = mode != ToolMode.SCAN,
                onSessionFailed = { e ->
                    message = tr("تعذر تشغيل ARCore: ", "ARCore unavailable: ") + (e.localizedMessage ?: "")
                },
                onSessionUpdated = { _, frame ->
                    frameRef[0] = frame
                    val now = System.nanoTime()
                    if (now - updateNanos[0] > 170_000_000L) {
                        updateNanos[0] = now
                        isTracking = frame.camera.trackingState == TrackingState.TRACKING
                        if (anchors.isNotEmpty() && isTracking && viewWidth > 0 && viewHeight > 0) {
                            try {
                                val view = FloatArray(16)
                                val proj = FloatArray(16)
                                frame.camera.getViewMatrix(view, 0)
                                frame.camera.getProjectionMatrix(proj, 0, 0.1f, 100f)
                                val projected = vertices.map { p ->
                                    ArProjection.worldToScreen(p, view, proj, viewWidth, viewHeight)
                                }
                                overlay = if (projected.all { it != null }) projected.filterNotNull() else emptyList()
                            } catch (_: Exception) { overlay = emptyList() }
                        } else overlay = emptyList()
                    }

                    if (mode == ToolMode.SCAN && scanning && frame.camera.trackingState == TrackingState.TRACKING) {
                        frameCounter[0]++
                        if (frameCounter[0] % 10 == 0) {
                            try {
                                val t = frame.camera.pose.translation
                                val eye = Vec3(t[0].toDouble(), t[1].toDouble(), t[2].toDouble())
                                frame.acquirePointCloud().use { cloud ->
                                    val data = cloud.points.duplicate()
                                    while (data.remaining() >= 4) {
                                        val point = Vec3(data.get().toDouble(), data.get().toDouble(), data.get().toDouble())
                                        val confidence = data.get()
                                        if (point.distanceTo(eye) <= 5.0) sampler.add(point, confidence)
                                    }
                                }
                                scanCount = sampler.count()
                                if (sampler.isFull()) {
                                    scanning = false
                                    message = tr("وصلت إلى الحد الأعلى للنقاط. صدّر السحابة الآن.", "Point limit reached. Export the cloud.")
                                }
                            } catch (_: Exception) { /* Occasional unavailable AR frames are expected. */ }
                        }
                    }
                },
                onTouchEvent = { event, _ ->
                    if (event.actionMasked == MotionEvent.ACTION_UP && mode != ToolMode.SCAN && !showHistory && pendingMode == null) {
                        val hit = frameRef[0]?.hitTest(event)?.firstOrNull {
                            it.trackable is Plane && (it.trackable as Plane).isPoseInPolygon(it.hitPose)
                        }
                        if (hit == null) {
                            message = tr("لم يتم العثور على سطح. حرّك الكاميرا ببطء.", "No detected plane. Move the camera slowly.")
                        } else if ((mode == ToolMode.LENGTH && anchors.size < 2) ||
                            (mode == ToolMode.AREA && anchors.size < 24)) {
                            try {
                                anchors.add(hit.createAnchor())
                                message = tr("أضيفت النقطة ${anchors.size}.", "Point ${anchors.size} added.")
                            } catch (e: Exception) {
                                message = e.localizedMessage ?: "AR error"
                            }
                        }
                    }
                    mode != ToolMode.SCAN && event.actionMasked == MotionEvent.ACTION_UP
                }
            )

            if (mode != ToolMode.SCAN && overlay.size == vertices.size) {
                Canvas(Modifier.fillMaxSize()) {
                    if (mode == ToolMode.AREA && overlay.size >= 3) {
                        val polygon = Path().apply {
                            moveTo(overlay[0].x, overlay[0].y)
                            overlay.drop(1).forEach { lineTo(it.x, it.y) }
                            close()
                        }
                        drawPath(polygon, brightBlue.copy(alpha = 0.16f))
                        drawPath(polygon, brightBlue, style = Stroke(width = 3.dp.toPx()))
                    } else {
                        for (i in 0 until overlay.size - 1) {
                            drawLine(brightBlue, Offset(overlay[i].x, overlay[i].y),
                                Offset(overlay[i+1].x, overlay[i+1].y), strokeWidth = 3.dp.toPx())
                        }
                    }
                    overlay.forEach { p ->
                        val xy = Offset(p.x, p.y)
                        drawCircle(Color.White, radius = 10.dp.toPx(), center = xy)
                        drawCircle(brightBlue, radius = 6.dp.toPx(), center = xy)
                    }
                }
            }
            if (mode == ToolMode.SCAN && preview != null) {
                CloudPreview(preview.orEmpty(), Modifier.fillMaxSize())
            }

            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xF9081324), Color(0xDF152840))))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("BIM Measure 3D", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("BIM SPECIALIST  •  v0.2 Beta", fontSize = 10.sp, color = paleBlue)
                        }
                        OutlinedButton(onClick = { showHistory = true }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                            Text(tr("السجل (${savedRecords.size})", "History (${savedRecords.size})"), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ToolTab(tr("الطول", "Length"), mode == ToolMode.LENGTH, Modifier.weight(1f)) { requestMode(ToolMode.LENGTH) }
                        ToolTab(tr("المساحة", "Area"), mode == ToolMode.AREA, Modifier.weight(1f)) { requestMode(ToolMode.AREA) }
                        ToolTab(tr("مسح 3D", "3D Scan"), mode == ToolMode.SCAN, Modifier.weight(1f)) { requestMode(ToolMode.SCAN) }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (mode != ToolMode.SCAN) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DisplayUnit.entries.forEach { option ->
                                FilterChip(
                                    selected = unit == option,
                                    onClick = { unit = option },
                                    label = { Text(option.label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    val primary = when (mode) {
                        ToolMode.LENGTH -> measured?.let { MeasureFormat.distance(it, unit) }
                            ?: tr("نقطتان لقياس الطول", "Select 2 points")
                        ToolMode.AREA -> measured?.let { MeasureFormat.area(it, unit) }
                            ?: tr("3 نقاط أو أكثر للمساحة", "Select 3+ points")
                        ToolMode.SCAN -> tr("النقاط: ", "Points: ") + scanCount
                    }
                    Text(primary, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = paleBlue)
                    if (perimeter != null) {
                        Text(tr("المحيط: ", "Perimeter: ") + MeasureFormat.distance(perimeter, unit),
                            color = Color.White, fontSize = 13.sp)
                    }
                    if (error != null) Text(error, fontSize = 12.sp, color = warningAmber)
                }

                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xE7182F4B), Color(0xFC081323))))
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    Text(
                        if (isTracking) tr("AR TRACKING  •  اتصال جيد", "AR TRACKING  •  active")
                        else tr("AR SEARCHING  •  حرّك الهاتف ببطء", "AR SEARCHING  •  move device"),
                        fontSize = 11.sp, color = if (isTracking) Color(0xFF83E4C4) else warningAmber
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(message, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp, color = Color.White)
                    Spacer(Modifier.height(10.dp))

                    when (mode) {
                        ToolMode.LENGTH, ToolMode.AREA -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = {
                                    if (anchors.isNotEmpty()) {
                                        anchors.removeAt(anchors.lastIndex).detach()
                                        overlay = emptyList()
                                    }
                                }, enabled = anchors.isNotEmpty(), modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 3.dp)) {
                                    Text(tr("تراجع", "Undo"), fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = { clearAnchors() }, enabled = anchors.isNotEmpty(),
                                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)) {
                                    Text(tr("مسح", "Clear"), fontSize = 12.sp)
                                }
                                Button(onClick = { saveCurrent() }, enabled = measured != null,
                                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)) {
                                    Text(tr("حفظ", "Save"), fontSize = 12.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = {
                                    if (measured != null) export(
                                        if (mode == ToolMode.LENGTH) "length_mm.dxf" else "area_mm.dxf",
                                        Exports.dxf(vertices, mode == ToolMode.AREA)
                                    )
                                }, enabled = measured != null, modifier = Modifier.weight(1f)) {
                                    Text(tr("تصدير DXF", "DXF"), fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = {
                                    if (measured != null) export(
                                        if (mode == ToolMode.LENGTH) "length_points.csv" else "area_points.csv",
                                        Exports.measurementCsv(if (mode == ToolMode.LENGTH) "LENGTH" else "AREA",
                                            vertices, measured, perimeter)
                                    )
                                }, enabled = measured != null, modifier = Modifier.weight(1f)) {
                                    Text(tr("تصدير CSV", "CSV"), fontSize = 12.sp)
                                }
                            }
                        }
                        ToolMode.SCAN -> {
                            Text(tr(
                                "سحابة نقطية تجريبية فقط، غير مناسبة للطباعة المباشرة أو أعمال المساحة الدقيقة.",
                                "Sparse point cloud only. Not a printable model or precision survey."
                            ), fontSize = 11.sp, color = warningAmber)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = {
                                    if (scanning) {
                                        scanning = false
                                        message = tr("توقف المسح مؤقتًا. يمكنك استكماله.", "Paused. Resume to add more points.")
                                    } else {
                                        preview = null
                                        scanning = true
                                        message = tr("حرّك الكاميرا ببطء حول الجسم.", "Move slowly around the object.")
                                    }
                                }, enabled = !sampler.isFull(),
                                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) {
                                    Text(if (scanning) tr("إيقاف", "Pause") else if (scanCount > 0) tr("استكمال", "Resume") else tr("ابدأ", "Start"),
                                        fontSize = 11.sp)
                                }
                                OutlinedButton(onClick = {
                                    preview = if (preview == null) sampler.snapshot() else null
                                }, enabled = !scanning && scanCount > 0,
                                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) {
                                    Text(if (preview == null) tr("معاينة", "Preview") else tr("كاميرا", "Camera"), fontSize = 11.sp)
                                }
                                OutlinedButton(onClick = { showScanReset = true },
                                    enabled = scanCount > 0,
                                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) {
                                    Text(tr("مسح", "Reset"), fontSize = 11.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { export("scan_points_m.ply", Exports.ply(sampler.snapshot())) },
                                    enabled = !scanning && scanCount > 0, modifier = Modifier.weight(1f)) { Text("PLY", fontSize = 12.sp) }
                                OutlinedButton(onClick = { export("scan_points_m.xyz", Exports.xyz(sampler.snapshot())) },
                                    enabled = !scanning && scanCount > 0, modifier = Modifier.weight(1f)) { Text("XYZ", fontSize = 12.sp) }
                            }
                        }
                    }
                    Text(
                        tr("النتائج تقديرية. تحقق بقياس مرجعي قبل أي استخدام هندسي.",
                            "Measurements are estimates. Verify with a physical reference."),
                        fontSize = 10.sp, color = paleBlue.copy(alpha = 0.75f)
                    )
                }
            }
        }

        if (showHistory) {
            AlertDialog(
                onDismissRequest = { showHistory = false },
                title = { Text(tr("سجل القياسات المحلي", "Saved measurements")) },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        if (savedRecords.isEmpty()) {
                            Text(tr("لا توجد قياسات محفوظة بعد.", "No saved measurements yet."))
                        }
                        savedRecords.forEach { record ->
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                (if (record.kind == "LENGTH") tr("الطول", "Length") else tr("المساحة", "Area")) +
                                    "  •  " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.timestampMillis)),
                                fontSize = 12.sp, color = paleBlue
                            )
                            Text(if (record.kind == "LENGTH") MeasureFormat.distance(record.valueMeters, unit)
                                 else MeasureFormat.area(record.valueMeters, unit), fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    export("measurement_${record.id.take(8)}.dxf",
                                        Exports.dxf(record.points, record.kind == "AREA"))
                                }) { Text("DXF") }
                                TextButton(onClick = {
                                    export("measurement_${record.id.take(8)}.csv",
                                        Exports.measurementCsv(record.kind, record.points,
                                            record.valueMeters, record.perimeterMeters))
                                }) { Text("CSV") }
                                TextButton(onClick = {
                                    store.delete(record.id)
                                    savedRecords = store.load()
                                }) { Text(tr("حذف", "Delete"), color = warningAmber) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showHistory = false }) { Text(tr("إغلاق", "Close")) } }
            )
        }
        if (pendingMode != null) {
            AlertDialog(
                onDismissRequest = { pendingMode = null },
                title = { Text(tr("تغيير الأداة؟", "Switch tools?")) },
                text = { Text(tr("سيتم مسح النقاط غير المحفوظة في هذه الجلسة. القياسات الموجودة في السجل ستبقى محفوظة.",
                    "Unsaved live points will be discarded. Saved measurements remain in history.")) },
                confirmButton = {
                    TextButton(onClick = { val next = pendingMode; pendingMode = null; if (next != null) switchNow(next) }) {
                        Text(tr("متابعة", "Continue"))
                    }
                },
                dismissButton = { TextButton(onClick = { pendingMode = null }) { Text(tr("إلغاء", "Cancel")) } }
            )
        }
        if (showScanReset) {
            AlertDialog(
                onDismissRequest = { showScanReset = false },
                title = { Text(tr("حذف نقاط المسح؟", "Clear scan points?")) },
                text = { Text(tr("سيتم حذف سحابة النقاط الحالية من الذاكرة.", "Current point cloud will be discarded.")) },
                confirmButton = {
                    TextButton(onClick = { resetScan(); showScanReset = false }) { Text(tr("حذف", "Clear")) }
                },
                dismissButton = { TextButton(onClick = { showScanReset = false }) { Text(tr("إلغاء", "Cancel")) } }
            )
        }
    }
}

@Composable
private fun ToolTab(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = 4.dp, vertical = 3.dp)
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(38.dp), contentPadding = padding) {
            Text(title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(38.dp), contentPadding = padding) {
            Text(title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CloudPreview(points: List<Vec3>, modifier: Modifier = Modifier) {
    var yaw by remember { mutableFloatStateOf(0.5f) }
    var pitch by remember { mutableFloatStateOf(0.3f) }
    val sample = remember(points) {
        if (points.size <= 5000) points else points.filterIndexed { i, _ -> i % max(1, points.size / 5000) == 0 }.take(5000)
    }
    val center = remember(sample) {
        if (sample.isEmpty()) Vec3(0.0, 0.0, 0.0)
        else Vec3(sample.map { it.x }.average(), sample.map { it.y }.average(), sample.map { it.z }.average())
    }
    val radius = remember(sample, center) { max(0.1, sample.maxOfOrNull { it.distanceTo(center) } ?: 1.0) }
    Canvas(modifier.background(Color(0xFF111D2E)).pointerInput(Unit) {
        detectDragGestures { change, drag ->
            change.consume()
            yaw += drag.x * 0.008f
            pitch = (pitch + drag.y * 0.008f).coerceIn(-1.4f, 1.4f)
        }
    }) {
        val scale = minOf(size.width, size.height) * 0.38 / radius.toFloat()
        val cy = cos(yaw.toDouble()); val sy = sin(yaw.toDouble())
        val cp = cos(pitch.toDouble()); val sp = sin(pitch.toDouble())
        for (p in sample) {
            val v = p - center
            val x = v.x * cy - v.z * sy
            val z = v.x * sy + v.z * cy
            val y = v.y * cp - z * sp
            drawCircle(brightBlue, radius = 1.4.dp.toPx(),
                center = Offset(size.width / 2 + (x * scale).toFloat(),
                    size.height / 2 - (y * scale).toFloat()))
        }
    }
}
