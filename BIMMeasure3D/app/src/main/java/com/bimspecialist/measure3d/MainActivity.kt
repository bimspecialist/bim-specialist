package com.bimspecialist.measure3d

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.app.Activity
import java.io.File
import com.bimspecialist.measure3d.updates.AppUpdater
import com.bimspecialist.measure3d.updates.AppUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.ui.platform.LocalConfiguration
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

internal enum class ToolMode { LENGTH, AREA, SCAN }

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
    val currentLocale = LocalConfiguration.current.locales[0] ?: Locale.US
    val arabic = currentLocale.language == "ar"
    fun tr(ar: String, en: String) = if (arabic) ar else en

    val uiScope = rememberCoroutineScope()
    val store = remember(context) { MeasurementStore(context) }
    val projectStore = remember(context) { ProjectStore(context) }
    var projects by remember { mutableStateOf(listOf(FieldProject("general", "General", 0L))) }
    var projectId by remember { mutableStateOf("general") }
    var pendingProjectId by remember { mutableStateOf<String?>(null) }
    var projectPicker by remember { mutableStateOf(false) }
    var projectInput by remember { mutableStateOf("") }
    var projectError by remember { mutableStateOf("") }
    var referenceDialog by remember { mutableStateOf(false) }
    var referenceInput by remember { mutableStateOf("") }
    var referenceError by remember { mutableStateOf("") }
    var referenceResult by remember { mutableStateOf<ReferenceResult?>(null) }
    var extrusionHeightInput by remember { mutableStateOf("0.20") }
    var solidPreview by remember { mutableStateOf<SolidMesh?>(null) }
    var extrudeReverse by remember { mutableStateOf(false) }
    var scanStats by remember { mutableStateOf<CloudStats?>(null) }
    var savedRecords by remember { mutableStateOf(emptyList<SavedMeasurement>()) }
    var storageReady by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var backupRestoreData by remember { mutableStateOf<ByteArray?>(null) }
    var backupRestoreInfo by remember { mutableStateOf<BackupContents?>(null) }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var updateDismissed by remember { mutableStateOf(false) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateDownloading by remember { mutableStateOf(false) }
    var downloadedUpdate by remember { mutableStateOf<Pair<Int, File>?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var pendingMode by remember { mutableStateOf<ToolMode?>(null) }
    var showScanReset by remember { mutableStateOf(false) }

    var mode by rememberSaveable { mutableStateOf(ToolMode.LENGTH) }
    var showHome by rememberSaveable { mutableStateOf(true) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var manualSaving by remember { mutableStateOf(false) }
    var manualLastSaved by remember { mutableStateOf<ManualMeasurement?>(null) }
    var manualNotice by remember { mutableStateOf<String?>(null) }
    var arCapability by remember { mutableStateOf(ArCapability.CHECKING) }
    var arCheckRequest by remember { mutableIntStateOf(0) }
    var showArHelp by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var cameraPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED)
    }
    var pendingArMode by remember { mutableStateOf<ToolMode?>(null) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
        permissionDenied = !granted
        if (!granted) pendingArMode = null
    }
    DisposableEffect(context, arCheckRequest) {
        var active = true
        arCapability = ArCapability.CHECKING
        try {
            ArCoreApk.getInstance().checkAvailabilityAsync(context.applicationContext) { availability ->
                if (active) arCapability = ArCapabilityPolicy.fromGoogleAvailability(availability.name)
            }
        } catch (_: Exception) {
            if (active) arCapability = ArCapability.UNKNOWN
        }
        onDispose { active = false }
    }
    var showAdvanced by remember { mutableStateOf(false) }
    var showExportOptions by remember { mutableStateOf(false) }
    var pendingHome by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf(DisplayUnit.METRE) }
    var dxfCoordinates by remember { mutableStateOf(DxfCoordinateSystem.ARCORE_RAW) }
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
    var anchorsStable by remember { mutableStateOf(false) }
    val stability = remember { ArStabilityMonitor() }
    var message by remember { mutableStateOf(tr(
        "حرّك الهاتف لاكتشاف السطح، ثم انقر لتثبيت نقاط القياس.",
        "Move the phone to detect a plane, then tap to place measurement points."
    )) }
    LaunchedEffect(context) {
        try {
            val snapshot = withContext(Dispatchers.IO) { FieldDatabase.get(context).snapshot() }
            projects = snapshot.projects
            projectId = snapshot.selectedProject
            savedRecords = snapshot.measurements
            unit = snapshot.unit
            storageReady = true
        } catch (e: Exception) {
            storageReady = false
            message = tr("تعذر تحميل البيانات دون فقدها: ", "Stored records cannot be opened safely: ") +
                (e.localizedMessage ?: "")
        }
    }
    LaunchedEffect(Unit) {
        // The application remains fully usable offline, even if the release endpoint is unavailable.
        availableUpdate = try { AppUpdater.checkAvailable() } catch (_: Exception) { null }
    }

    var pendingData by remember { mutableStateOf<ByteArray?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingData
        pendingData = null
        if (uri != null && bytes != null) uiScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IllegalStateException("Cannot open selected document")
                }
                message = tr("تم حفظ الملف في الموقع الذي اخترته.", "File saved in selected location.")
            } catch (e: Exception) {
                message = tr("تعذر حفظ الملف: ", "Save failed: ") + (e.localizedMessage ?: "")
            }
        }
    }
    val backupImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) uiScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 20_000_000) { "Backup exceeds 20 MB" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: error("Unable to read backup")
                }
                val info = withContext(Dispatchers.IO) { ProjectBackup.inspect(bytes) }
                backupRestoreData = bytes
                backupRestoreInfo = info
                projectPicker = false
            } catch (e: Exception) {
                message = tr("تعذر قراءة النسخة الاحتياطية: ", "Cannot open backup: ") +
                    (e.localizedMessage ?: "")
            }
        }
    }
    fun export(filename: String, bytes: ByteArray) {
        pendingData = bytes
        exportLauncher.launch(filename)
    }
    fun clearAnchors() {
        anchors.forEach { it.detach() }
        anchors.clear()
        stability.reset()
        anchorsStable = false
        overlay = emptyList()
    }
    fun resetScan() {
        scanStats = null
        scanning = false
        preview = null
        sampler.clear()
        scanCount = 0
        frameCounter[0] = 0
    }
    fun switchNow(next: ToolMode) {
        clearAnchors()
        resetScan()
        referenceResult = null
        solidPreview = null
        mode = next
        message = when (next) {
            ToolMode.LENGTH -> tr("انقر على نقطتين على السطح لقياس المسافة.", "Tap two points on a detected plane.")
            ToolMode.AREA -> tr("حدد زوايا المساحة بالترتيب حول المحيط.", "Tap corner points around the boundary.")
            ToolMode.SCAN -> tr("اجمع سحابة نقاط أولية. ليست شبكة مجسم للطباعة.", "Capture a sparse point cloud, not a printable mesh.")
        }
    }
    fun requestHome() {
        if (CaptureUiPolicy.requiresDiscardConfirmation(anchors.size, sampler.count())) {
            pendingHome = true
        } else {
            clearAnchors()
            resetScan()
            showAdvanced = false
            showHome = true
        }
    }
    fun requestMode(next: ToolMode) {
        if (next == mode) return
        if (anchors.isNotEmpty() || sampler.count() > 0) pendingMode = next else switchNow(next)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraPermissionGranted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                arCheckRequest++
            }
            if (event == Lifecycle.Event.ON_STOP) {
                scanning = false
                isTracking = false
                anchorsStable = false
                stability.reset()
                frameRef[0] = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            anchors.forEach { it.detach() }
            frameRef[0] = null
        }
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

    fun saveManualMeasurement(result: ManualMeasurement) {
        if (!storageReady || manualSaving || manualLastSaved == result) return
        manualSaving = true
        manualNotice = null
        uiScope.launch {
            try {
                val updatedRecords = withContext(Dispatchers.IO) {
                    store.add(
                        result.kind, result.valueMetresOrSquareMetres,
                        result.perimeterMetres, result.points, projectId, source = "MANUAL"
                    )
                    store.load()
                }
                savedRecords = updatedRecords
                manualLastSaved = result
                manualNotice = tr("تم حفظ القياس اليدوي في المشروع.",
                    "Manual measurement saved to your project.")
            } catch (e: Exception) {
                manualNotice = tr("تعذر حفظ القياس: ", "Save failed: ") +
                    (e.localizedMessage ?: "")
            } finally {
                manualSaving = false
            }
        }
    }

    fun saveCurrent() {
        val value = measured ?: return
        if (!storageReady || !isTracking || !anchorsStable) return
        val kind = if (mode == ToolMode.LENGTH) "LENGTH" else "AREA"
        val pointsSnapshot = vertices.toList()
        val destination = projectId
        val measuredPerimeter = perimeter
        uiScope.launch {
            try {
                savedRecords = withContext(Dispatchers.IO) {
                    store.add(kind, value, measuredPerimeter, pointsSnapshot, destination)
                    store.load()
                }
                message = tr("تم حفظ القياس محليًا في السجل.", "Measurement saved locally.")
            } catch (e: Exception) {
                message = tr("فشل الحفظ، ولم تُحذف القياسات القديمة: ",
                    "Save failed without overwriting previous records: ") + (e.localizedMessage ?: "")
            }
        }
    }

    LaunchedEffect(cameraPermissionGranted, pendingArMode, arCapability) {
        val selected = pendingArMode
        if (cameraPermissionGranted && selected != null && arCapability == ArCapability.READY) {
            pendingArMode = null
            switchNow(selected)
            showHome = false
            showManual = false
        }
    }
    BackHandler(enabled = !showHome || showManual) {
        when {
            showManual -> showManual = false
            showAdvanced -> showAdvanced = false
            else -> requestHome()
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = brightBlue, background = deepNavy)) {
        if (showManual) {
            ManualMeasurementScreen(
                arabic = arabic,
                projectName = if (projectId == "general") tr("عام", "General")
                    else projects.firstOrNull { it.id == projectId }?.name ?: "General",
                storageReady = storageReady,
                isSaving = manualSaving,
                lastSaved = manualLastSaved,
                notice = manualNotice,
                onBack = { showManual = false },
                onHistory = { showHistory = true },
                onSave = { entry -> saveManualMeasurement(entry) }
            )
        } else if (showHome) {
            ModernHomeScreen(
                arabic = arabic,
                projectName = if (projectId == "general") tr("عام", "General")
                    else projects.firstOrNull { it.id == projectId }?.name ?: "General",
                savedCount = savedRecords.count { it.projectId == projectId },
                storageReady = storageReady,
                newUpdate = availableUpdate != null,
                updateMessage = updateStatus,
                arCapability = arCapability,
                onRetryAr = { arCheckRequest++ },
                onGetArServices = {
                    val market = Intent(Intent.ACTION_VIEW, Uri.parse(
                        "market://details?id=com.google.ar.core"
                    ))
                    try {
                        context.startActivity(market)
                    } catch (_: Exception) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://play.google.com/store/apps/details?id=com.google.ar.core"
                        )))
                    }
                },
                onManual = {
                    manualNotice = null
                    showManual = true
                },
                onStart = { selected ->
                    if (arCapability != ArCapability.READY) {
                        showArHelp = true
                    } else if (cameraPermissionGranted) {
                        switchNow(selected)
                        showAdvanced = false
                        showHome = false
                    } else {
                        pendingArMode = selected
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onProjects = { if (storageReady) projectPicker = true },
                onHistory = { showHistory = true },
                onUpdates = {
                    updateDismissed = false
                    updateChecking = true
                    uiScope.launch {
                        try {
                            availableUpdate = AppUpdater.checkAvailable()
                            if (availableUpdate == null) {
                                updateStatus = tr("لا يوجد تحديث جديد متاح.", "No new update available.")
                            }
                        } catch (_: Exception) {
                            updateStatus = tr("تعذر التحقق من التحديثات حاليًا.", "Unable to check updates.")
                        } finally {
                            updateChecking = false
                        }
                    }
                }
            )
        } else {
        Box(Modifier.fillMaxSize().background(deepNavy).onSizeChanged {
            viewWidth = it.width
            viewHeight = it.height
        }) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                depthMode = Config.DepthMode.DISABLED,
                planeRenderer = mode != ToolMode.SCAN,
                onSessionFailed = { e ->
                    isTracking = false
                    anchorsStable = false
                    stability.reset()
                    frameRef[0] = null
                    overlay = emptyList()
                    scanning = false
                    arCapability = ArCapability.SESSION_FAILED
                    showArHelp = true
                    message = tr("تعذر تشغيل ARCore: ", "ARCore unavailable: ") +
                        (e.localizedMessage ?: "")
                },
                onSessionUpdated = { _, frame ->
                    frameRef[0] = frame
                    val now = System.nanoTime()
                    if (now - updateNanos[0] > 170_000_000L) {
                        updateNanos[0] = now
                        isTracking = frame.camera.trackingState == TrackingState.TRACKING &&
                            anchors.all { it.trackingState == TrackingState.TRACKING }
                        val currentAnchors = anchors.map { anchor ->
                            val position = anchor.pose.translation
                            Vec3(position[0].toDouble(), position[1].toDouble(), position[2].toDouble())
                        }
                        anchorsStable = stability.update(currentAnchors, isTracking)
                        if (anchors.isNotEmpty() && isTracking && viewWidth > 0 && viewHeight > 0) {
                            try {
                                val view = FloatArray(16)
                                val proj = FloatArray(16)
                                frame.camera.getViewMatrix(view, 0)
                                frame.camera.getProjectionMatrix(proj, 0, 0.1f, 100f)
                                val projected = currentAnchors.map { p ->
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
                    if (event.actionMasked == MotionEvent.ACTION_UP && mode != ToolMode.SCAN && !showHistory && !projectPicker && pendingProjectId == null && !referenceDialog && pendingMode == null && backupRestoreInfo == null && solidPreview == null && !showScanReset && !(availableUpdate != null && !updateDismissed) && !showExportOptions && !pendingHome) {
                        val hit = frameRef[0]?.hitTest(event)?.firstOrNull {
                            it.trackable is Plane && it.trackable.trackingState == TrackingState.TRACKING &&
                                (it.trackable as Plane).isPoseInPolygon(it.hitPose)
                        }
                        if (frameRef[0]?.camera?.trackingState != TrackingState.TRACKING) {
                            message = tr("تعذر تثبيت النقطة. انتظر استقرار التتبع.", "Tracking unstable. Try again when tracking is active.")
                        } else if (hit == null) {
                            message = tr("لم يتم العثور على سطح. حرّك الكاميرا ببطء.", "No detected plane. Move the camera slowly.")
                        } else if ((mode == ToolMode.LENGTH && anchors.size < 2) ||
                            (mode == ToolMode.AREA && anchors.size < 24)) {
                            try {
                                val distance = hit.hitPose.translation.let { h ->
                                    val t = frameRef[0]!!.camera.pose.translation
                                    Vec3(h[0].toDouble(), h[1].toDouble(), h[2].toDouble())
                                        .distanceTo(Vec3(t[0].toDouble(), t[1].toDouble(), t[2].toDouble()))
                                }
                                if (distance > 8.0 || distance < 0.1) {
                                    message = tr("اختر سطحًا يبعد بين 10 سم و8 أمتار.", "Select a surface between 0.1 and 8 metres away.")
                                } else {
                                    anchors.add(hit.createAnchor())
                                    stability.reset()
                                    anchorsStable = false
                                    referenceResult = null
                                    message = tr("أضيفت النقطة ${anchors.size}.", "Point ${anchors.size} added.")
                                }
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

            if (showAdvanced) {
            val advancedShort = LocalConfiguration.current.screenHeightDp < 700
            Column(Modifier.fillMaxSize().safeDrawingPadding(),
                verticalArrangement = Arrangement.SpaceBetween) {
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = if (advancedShort) 155.dp else 300.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xF9081324), Color(0xDF152840))))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            TextButton(onClick = { showAdvanced = false }) {
                                Text(tr("العودة للواجهة البسيطة", "Back to simple view"), color = paleBlue)
                            }
                            Text("BIM Measure 3D", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("BIM SPECIALIST  •  ${BuildConfig.VERSION_NAME}", fontSize = 10.sp, color = paleBlue)
                        }
                        Column {
                            TextButton(
                                enabled = !updateChecking,
                                onClick = {
                                    updateDismissed = false
                                    updateChecking = true
                                    uiScope.launch {
                                        try {
                                            availableUpdate = AppUpdater.checkAvailable()
                                            if (availableUpdate == null) {
                                                updateStatus = tr("لا يوجد تحديث جديد متاح.", "No newer signed release available.")
                                            }
                                        } catch (_: Exception) {
                                            updateStatus = tr("تعذر فحص التحديثات. تأكد من الاتصال.", "Cannot check updates right now.")
                                        } finally { updateChecking = false }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                                modifier = Modifier.height(29.dp)
                            ) { Text(if (availableUpdate == null) tr("التحديثات", "Updates") else tr("تحديث جديد", "New update"), fontSize = 11.sp) }
                            OutlinedButton(onClick = { showHistory = true },
                                contentPadding = PaddingValues(horizontal = 7.dp), modifier = Modifier.height(34.dp)) {
                                Text(tr("السجل (${savedRecords.count { it.projectId == projectId }})", "History (${savedRecords.count { it.projectId == projectId }})"), fontSize = 11.sp)
                            }
                        }
                    }
                    if (updateStatus != null) {
                        Text(updateStatus!!, color = paleBlue, fontSize = 11.sp, maxLines = 2)
                    }
                    Spacer(Modifier.height(5.dp))
                    OutlinedButton(
                        onClick = { projectPicker = true },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text(
                            tr("المشروع: ", "Project: ") +
                                (if (projectId == "general") tr("عام", "General") else projects.firstOrNull { it.id == projectId }?.name ?: "General"),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(7.dp))
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
                                    onClick = {
                                        if (storageReady) {
                                            val previous = unit
                                            unit = option
                                            uiScope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) { projectStore.saveUnit(option) }
                                                } catch (e: Exception) {
                                                    unit = previous
                                                    message = tr("فشل حفظ الوحدة: ", "Unable to save units: ") +
                                                        (e.localizedMessage ?: "")
                                                }
                                            }
                                        }
                                    },
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
                    if (mode == ToolMode.LENGTH && referenceResult != null) {
                        Text(
                            tr("فرق المرجع: ", "Reference difference: ") +
                                String.format(Locale.US, "%.2f%%", referenceResult!!.errorPercent),
                            fontSize = 12.sp, color = warningAmber
                        )
                    }
                    if (perimeter != null) {
                        Text(tr("المحيط: ", "Perimeter: ") + MeasureFormat.distance(perimeter, unit),
                            color = Color.White, fontSize = 13.sp)
                    }
                    if (error != null) Text(error, fontSize = 12.sp, color = warningAmber)
                }

                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xE7182F4B), Color(0xFC081323))))
                        .heightIn(max = if (advancedShort) 180.dp else 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    Text(
                        if (!isTracking) tr("AR SEARCHING  •  حرّك الهاتف ببطء", "AR SEARCHING  •  move device")
                        else if (anchors.isNotEmpty() && !anchorsStable) tr("AR STABILIZING  •  انتظر استقرار النقاط", "AR STABILIZING  •  wait for anchors")
                        else tr("AR TRACKING  •  التتبع مستقر", "AR TRACKING  •  stable"),
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
                                        referenceResult = null
                                        anchors.removeAt(anchors.lastIndex).detach()
                                        stability.reset()
                                        anchorsStable = false
                                        overlay = emptyList()
                                    }
                                }, enabled = anchors.isNotEmpty(), modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 3.dp)) {
                                    Text(tr("تراجع", "Undo"), fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = { referenceResult = null; clearAnchors() }, enabled = anchors.isNotEmpty(),
                                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)) {
                                    Text(tr("مسح", "Clear"), fontSize = 12.sp)
                                }
                                Button(onClick = { saveCurrent() }, enabled = measured != null && isTracking && anchorsStable && storageReady,
                                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)) {
                                    Text(tr("حفظ", "Save"), fontSize = 12.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DxfCoordinateSystem.entries.forEach { coordinateMode ->
                                    FilterChip(
                                        selected = dxfCoordinates == coordinateMode,
                                        onClick = { dxfCoordinates = coordinateMode },
                                        label = { Text(if (coordinateMode == DxfCoordinateSystem.CAD_Z_UP) "CAD Z-up"
                                            else "AR raw", fontSize = 10.sp) }
                                    )
                                }
                            }
                            Text(tr("محاور DXF: خام AR أو CAD Z-up؛ الأصل محلي وليس مساحيًا.",
                                "DXF axes: AR raw or CAD Z-up. Origin is local, not survey-controlled."),
                                fontSize = 10.sp, color = paleBlue)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = {
                                    if (measured != null) export(
                                        if (mode == ToolMode.LENGTH) "length_mm.dxf" else "area_mm.dxf",
                                        Exports.dxf(vertices, mode == ToolMode.AREA, dxfCoordinates)
                                    )
                                }, enabled = measured != null && isTracking && anchorsStable, modifier = Modifier.weight(1f)) {
                                    Text(tr("تصدير DXF", "DXF"), fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = {
                                    if (measured != null) export(
                                        if (mode == ToolMode.LENGTH) "length_points.csv" else "area_points.csv",
                                        Exports.measurementCsv(if (mode == ToolMode.LENGTH) "LENGTH" else "AREA",
                                            vertices, measured, perimeter)
                                    )
                                }, enabled = measured != null && isTracking && anchorsStable, modifier = Modifier.weight(1f)) {
                                    Text(tr("تصدير CSV", "CSV"), fontSize = 12.sp)
                                }
                            }
                            if (mode == ToolMode.LENGTH && measured != null) {
                                TextButton(
                                    onClick = { referenceInput = ""; referenceError = ""; referenceDialog = true }
                                ) { Text(tr("التحقق بمقياس مرجعي", "Check against tape-measure reference"), fontSize = 12.sp) }
                            }
                            if (mode == ToolMode.AREA && measured != null && isTracking && anchorsStable) {
                                Spacer(Modifier.height(4.dp))
                                HorizontalDivider()
                                Text(
                                    tr("إنشاء مجسم هندسي من المساحة، وليس من المسح بالكاميرا.",
                                       "Generate a solid from the area outline, not from a 3D scan."),
                                    fontSize = 11.sp, color = paleBlue
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    OutlinedTextField(
                                        value = extrusionHeightInput,
                                        onValueChange = { if (it.length <= 12) extrusionHeightInput = it },
                                        modifier = Modifier.weight(1f),
                                        label = { Text(tr("ارتفاع المجسم (م)", "Extrusion height (m)"), fontSize = 11.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                    )
                                    FilterChip(
                                        selected = extrudeReverse,
                                        onClick = { extrudeReverse = !extrudeReverse },
                                        label = { Text(tr("عكس الاتجاه", "Reverse"), fontSize = 10.sp) }
                                    )
                                }
                                Text(
                                    tr("يُصدّر المجسم بوحدة mm. اتجاه البثق يتبع ترتيب النقاط.",
                                       "Exports use mm. Extrusion direction depends on vertex order."),
                                    fontSize = 10.sp, color = warningAmber
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            try {
                                                val h = extrusionHeightInput.trim().replace(',', '.').toDoubleOrNull()
                                                    ?: throw IllegalArgumentException("Enter a valid height in metres")
                                                solidPreview = Extrusion.fromPolygon(vertices, h, extrudeReverse)
                                            } catch (e: Exception) {
                                                message = tr("تعذر إنشاء المعاينة: ", "Solid preview failed: ") + (e.localizedMessage ?: "")
                                            }
                                        }
                                    ) { Text("3D") }
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            try {
                                                val h = extrusionHeightInput.trim().replace(',', '.').toDoubleOrNull()
                                                    ?: throw IllegalArgumentException("Enter a valid height in metres")
                                                val solid = Extrusion.fromPolygon(vertices, h, extrudeReverse)
                                                export("measured_outline_solid_mm.stl", MeshExports.stlMillimetres(solid))
                                            } catch (e: Exception) {
                                                message = tr("تعذر إنشاء STL: ", "STL export failed: ") + (e.localizedMessage ?: "")
                                            }
                                        }
                                    ) { Text("STL") }
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            try {
                                                val h = extrusionHeightInput.trim().replace(',', '.').toDoubleOrNull()
                                                    ?: throw IllegalArgumentException("Enter a valid height in metres")
                                                val solid = Extrusion.fromPolygon(vertices, h, extrudeReverse)
                                                export("measured_outline_solid_mm.obj", MeshExports.objMillimetres(solid))
                                            } catch (e: Exception) {
                                                message = tr("تعذر إنشاء OBJ: ", "OBJ export failed: ") + (e.localizedMessage ?: "")
                                            }
                                        }
                                    ) { Text("OBJ") }
                                }
                            }
                        }
                        ToolMode.SCAN -> {
                            Text(tr(
                                "سحابة نقطية تجريبية فقط، غير مناسبة للطباعة المباشرة أو أعمال المساحة الدقيقة.",
                                "Sparse point cloud only. Not a printable model or precision survey."
                            ), fontSize = 11.sp, color = warningAmber)
                            Spacer(Modifier.height(4.dp))
                            if (scanStats != null) {
                                Text(
                                    tr("امتداد النقاط (م): ", "Point bounds (m): ") +
                                    String.format(Locale.US, "%.2f × %.2f × %.2f",
                                        scanStats!!.spanX, scanStats!!.spanY, scanStats!!.spanZ),
                                    fontSize = 11.sp, color = paleBlue
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = {
                                    if (scanning) {
                                        scanning = false
                                        scanStats = CloudAnalysis.compute(sampler.snapshot())
                                        message = tr("توقف المسح مؤقتًا. يمكنك استكماله.", "Paused. Resume to add more points.")
                                    } else {
                                        scanStats = null
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
            } else {
                ModernCameraOverlay(
                    arabic = arabic,
                    mode = mode,
                    unit = unit,
                    measured = measured,
                    perimeter = perimeter,
                    pointCount = vertices.size,
                    scanning = scanning,
                    scanCount = scanCount,
                    isTracking = isTracking,
                    anchorsStable = anchorsStable,
                    storageReady = storageReady,
                    statusMessage = message,
                    onHome = { requestHome() },
                    onMore = { showAdvanced = true },
                    onHistory = { showHistory = true },
                    onUndo = {
                        if (anchors.isNotEmpty()) {
                            referenceResult = null
                            anchors.removeAt(anchors.lastIndex).detach()
                            stability.reset()
                            anchorsStable = false
                            overlay = emptyList()
                        }
                    },
                    onClear = {
                        if (mode == ToolMode.SCAN) showScanReset = true
                        else {
                            referenceResult = null
                            clearAnchors()
                        }
                    },
                    onSave = { saveCurrent() },
                    onExport = { showExportOptions = true },
                    onUnit = { selected ->
                        if (storageReady) {
                            val previous = unit
                            unit = selected
                            uiScope.launch {
                                try {
                                    withContext(Dispatchers.IO) { projectStore.saveUnit(selected) }
                                } catch (e: Exception) {
                                    unit = previous
                                    message = tr("تعذر حفظ الوحدة: ", "Cannot save unit: ") +
                                        (e.localizedMessage ?: "")
                                }
                            }
                        }
                    },
                    onToggleScan = {
                        if (scanning) {
                            scanning = false
                            scanStats = CloudAnalysis.compute(sampler.snapshot())
                            message = tr("تم إيقاف المسح مؤقتًا.", "Scanning paused.")
                        } else if (!sampler.isFull()) {
                            preview = null
                            scanning = true
                            message = tr("حرّك الكاميرا ببطء حول الجسم.", "Move slowly around the object.")
                        }
                    },
                    onPreview = {
                        preview = if (preview == null) sampler.snapshot() else null
                    }
                )
            }
        }
        }

        if (showArHelp) {
            AlertDialog(
                onDismissRequest = { showArHelp = false },
                title = { Text(tr("حالة الواقع المعزز", "AR camera availability")) },
                text = {
                    Text(when (arCapability) {
                        ArCapability.UNSUPPORTED -> tr(
                            "هذا الهاتف لا يدعم ARCore حاليًا بحسب فحص Google. يمكنك استعمال القياس اليدوي وسجل المشاريع.",
                            "ARCore is not supported on this device according to Google's check. Manual measurements and project records still work.")
                        ArCapability.SERVICE_REQUIRED -> tr(
                            "ثبّت أو حدّث Google Play Services for AR من المتجر ثم أعد الفحص.",
                            "Install or update Google Play Services for AR, then run the check again.")
                        ArCapability.SESSION_FAILED -> tr(
                            "تعذر بدء جلسة الكاميرا. جرّب تحديث خدمة ARCore أو استخدم القياس اليدوي.",
                            "The AR session could not start. Try updating ARCore or use manual measurements.")
                        else -> tr(
                            "لم نتمكن من تأكيد دعم الكاميرا الآن. أعد الفحص أو استخدم القياس اليدوي.",
                            "AR availability has not been confirmed. Retry or use manual measurements.")
                    })
                },
                confirmButton = {
                    TextButton(onClick = {
                        showArHelp = false
                        arCheckRequest++
                    }) { Text(tr("إعادة الفحص", "Retry")) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showArHelp = false
                        if (!showHome) requestHome()
                        else showManual = true
                    }) {
                        Text(if (showHome) tr("قياس يدوي", "Manual mode")
                            else tr("الرئيسية", "Home"))
                    }
                }
            )
        }
        if (permissionDenied) {
            AlertDialog(
                onDismissRequest = { permissionDenied = false },
                title = { Text(tr("صلاحية الكاميرا", "Camera permission")) },
                text = { Text(tr(
                    "يحتاج قياس AR إلى إذن الكاميرا. القياس اليدوي لا يحتاج إلى هذا الإذن.",
                    "AR measurement requires camera access; manual measurement does not."
                )) },
                confirmButton = {
                    TextButton(onClick = {
                        permissionDenied = false
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        })
                    }) { Text(tr("إعدادات التطبيق", "App settings")) }
                },
                dismissButton = {
                    TextButton(onClick = { permissionDenied = false }) {
                        Text(tr("إغلاق", "Close"))
                    }
                }
            )
        }
        if (showExportOptions) {
            AlertDialog(
                onDismissRequest = { showExportOptions = false },
                title = { Text(tr("تصدير النتيجة", "Export result")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (mode == ToolMode.SCAN) {
                            Text(tr("اختر صيغة السحابة النقطية. لا تنتج هذه الأداة شبكة STL.",
                                "Choose a point-cloud format. This mode does not create a printable STL."),
                                fontSize = 12.sp)
                            Button(onClick = {
                                showExportOptions = false
                                export("scan_points_m.ply", Exports.ply(sampler.snapshot()))
                            }, modifier = Modifier.fillMaxWidth()) { Text("PLY") }
                            OutlinedButton(onClick = {
                                showExportOptions = false
                                export("scan_points_m.xyz", Exports.xyz(sampler.snapshot()))
                            }, modifier = Modifier.fillMaxWidth()) { Text("XYZ") }
                        } else {
                            Text(tr("DXF للمخططات وCSV للجدول. يمكنك إنشاء STL وOBJ من المساحة عبر «المزيد».",
                                "DXF for CAD, CSV for data. STL/OBJ from an area are under More."),
                                fontSize = 12.sp)
                            Button(onClick = {
                                if (measured != null) {
                                    showExportOptions = false
                                    export(
                                        if (mode == ToolMode.LENGTH) "length_mm.dxf" else "area_mm.dxf",
                                        Exports.dxf(vertices, mode == ToolMode.AREA, dxfCoordinates)
                                    )
                                }
                            }, enabled = measured != null && anchorsStable, modifier = Modifier.fillMaxWidth()) {
                                Text("DXF")
                            }
                            OutlinedButton(onClick = {
                                if (measured != null) {
                                    showExportOptions = false
                                    export(
                                        if (mode == ToolMode.LENGTH) "length.csv" else "area.csv",
                                        Exports.measurementCsv(
                                            if (mode == ToolMode.LENGTH) "LENGTH" else "AREA",
                                            vertices, measured!!, perimeter
                                        )
                                    )
                                }
                            }, enabled = measured != null && anchorsStable, modifier = Modifier.fillMaxWidth()) {
                                Text("CSV")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showExportOptions = false }) {
                        Text(tr("إغلاق", "Close"))
                    }
                }
            )
        }
        if (pendingHome) {
            AlertDialog(
                onDismissRequest = { pendingHome = false },
                title = { Text(tr("العودة للرئيسية؟", "Return to home?")) },
                text = { Text(tr(
                    "سيتم مسح نقاط القياس الحالية غير المحفوظة. ستبقى القياسات المحفوظة في المشروع.",
                    "Unsaved capture points will be cleared. Saved project records are preserved."
                )) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingHome = false
                        clearAnchors()
                        resetScan()
                        referenceResult = null
                        showAdvanced = false
                        showHome = true
                    }) { Text(tr("العودة", "Home")) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingHome = false }) { Text(tr("إلغاء", "Cancel")) }
                }
            )
        }

        if (availableUpdate != null && !updateDismissed) {
            val update = availableUpdate!!
            AlertDialog(
                onDismissRequest = { if (!updateDownloading) updateDismissed = true },
                title = { Text(tr("تحديث BIM Measure 3D", "BIM Measure 3D update")) },
                text = {
                    Column {
                        Text(tr(
                            "الإصدار الجديد: ${update.versionName}. يحتفظ Android بالمشاريع والقياسات عند التحديث بنفس شهادة التوقيع.",
                            "New version: ${update.versionName}. Android preserves local projects when the signing certificate matches."
                        ))
                        Spacer(Modifier.height(6.dp))
                        Text(tr(
                            "يتحقق التطبيق من SHA-256 للملف. يطلب Android موافقتك قبل تثبيت التحديث.",
                            "The APK is verified with SHA-256. Android will ask you to approve installation."
                        ), fontSize = 12.sp, color = paleBlue)
                        if (updateDownloading) CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                        if (updateStatus != null) Text(updateStatus!!, color = warningAmber, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !updateDownloading,
                        onClick = {
                            updateDownloading = true
                            uiScope.launch {
                                try {
                                    val cached = downloadedUpdate
                                    val file = if (cached != null && cached.first == update.versionCode &&
                                        AppUpdater.verifyCached(cached.second, update)) {
                                        cached.second
                                    } else {
                                        AppUpdater.downloadVerified(context, update).also {
                                            downloadedUpdate = update.versionCode to it
                                        }
                                    }
                                    val activity = context as? Activity
                                        ?: throw IllegalStateException("Activity unavailable")
                                    val started = AppUpdater.requestInstall(activity, file)
                                    updateStatus = if (started)
                                        tr("وافق على تثبيت التحديث في شاشة Android.", "Confirm the update in Android installer.")
                                    else
                                        tr("فعّل السماح بالتثبيت من هذا التطبيق، ثم اضغط تثبيت مرة أخرى.",
                                            "Allow installs from this app in Settings, then tap Install again.")
                                } catch (e: Exception) {
                                    updateStatus = tr("فشل تنزيل التحديث أو التحقق منه: ", "Update failed verification: ") +
                                        (e.localizedMessage ?: "")
                                } finally { updateDownloading = false }
                            }
                        }
                    ) { Text(if (downloadedUpdate?.first == update.versionCode) tr("تثبيت", "Install") else tr("تنزيل وتثبيت", "Download & install")) }
                },
                dismissButton = {
                    TextButton(enabled = !updateDownloading, onClick = { updateDismissed = true }) {
                        Text(tr("لاحقًا", "Later"))
                    }
                }
            )
        }
        if (pendingProjectId != null) {
            AlertDialog(
                onDismissRequest = { pendingProjectId = null },
                title = { Text(tr("تغيير المشروع؟", "Change project?")) },
                text = { Text(tr(
                    "هناك قياسات أو نقاط مسح غير محفوظة. سيتم التخلص منها قبل الانتقال. القياسات المحفوظة لن تُحذف.",
                    "There are unsaved measurement/scan points. Changing projects discards them. Saved measurements remain."
                )) },
                confirmButton = {
                    TextButton(onClick = {
                        val destination = pendingProjectId!!
                        pendingProjectId = null
                        uiScope.launch {
                            try {
                                withContext(Dispatchers.IO) { projectStore.select(destination) }
                                clearAnchors()
                                resetScan()
                                referenceResult = null
                                projectId = destination
                            } catch (e: Exception) {
                                message = tr("تعذر تغيير المشروع: ", "Project change failed: ") +
                                    (e.localizedMessage ?: "")
                            }
                        }
                    }) { Text(tr("الانتقال", "Switch")) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingProjectId = null }) { Text(tr("إلغاء", "Cancel")) }
                }
            )
        }
        if (backupRestoreData != null && backupRestoreInfo != null) {
            AlertDialog(
                onDismissRequest = { backupRestoreData = null; backupRestoreInfo = null },
                title = { Text(tr("تأكيد استعادة النسخة الاحتياطية", "Confirm backup restore")) },
                text = {
                    Text(
                        tr("سيتم استبدال جميع مشاريع وقياسات الجهاز بالنسخة المختارة. ",
                           "All locally saved projects and measurements will be REPLACED by this backup. ") +
                        tr("المشاريع: ${backupRestoreInfo!!.projects}، القياسات: ${backupRestoreInfo!!.measurements}. ",
                           "Projects: ${backupRestoreInfo!!.projects}, Measurements: ${backupRestoreInfo!!.measurements}. ") +
                        tr("صدّر نسخة احتياطية من بياناتك الحالية قبل المتابعة.",
                           "Export your current data before proceeding.")
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        try {
                            val data = backupRestoreData!!
                            uiScope.launch {
                                try {
                                    val snapshot = withContext(Dispatchers.IO) {
                                        ProjectBackup.restore(context, data)
                                        FieldDatabase.get(context).snapshot()
                                    }
                                    clearAnchors()
                                    resetScan()
                                    projects = snapshot.projects
                                    projectId = snapshot.selectedProject
                                    savedRecords = snapshot.measurements
                                    unit = snapshot.unit
                                    storageReady = true
                                    message = tr("تمت استعادة المشاريع والقياسات.", "Projects and measurements restored.")
                                } catch (e: Exception) {
                                    message = tr("فشلت عملية الاستعادة: ", "Restore failed: ") +
                                        (e.localizedMessage ?: "")
                                }
                            }
                        } catch (e: Exception) {
                            message = tr("فشلت عملية الاستعادة: ", "Restore failed: ") +
                                (e.localizedMessage ?: "")
                        } finally {
                            backupRestoreData = null
                            backupRestoreInfo = null
                        }
                    }) { Text(tr("استعادة واستبدال", "Replace and restore"), color = warningAmber) }
                },
                dismissButton = {
                    TextButton(onClick = { backupRestoreData = null; backupRestoreInfo = null }) {
                        Text(tr("إلغاء", "Cancel"))
                    }
                }
            )
        }
        if (solidPreview != null) {
            SolidPreviewDialog(mesh = solidPreview!!, arabic = arabic, onDismiss = { solidPreview = null })
        }
        if (projectPicker) {
            AlertDialog(
                onDismissRequest = { projectPicker = false },
                title = { Text(tr("المشاريع المحلية", "Local projects")) },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        projects.forEach { project ->
                            TextButton(
                                onClick = {
                                    if (project.id != projectId && (anchors.isNotEmpty() || sampler.count() > 0)) {
                                        pendingProjectId = project.id
                                    } else {
                                        uiScope.launch {
                                            try {
                                                withContext(Dispatchers.IO) { projectStore.select(project.id) }
                                                projectId = project.id
                                            } catch (e: Exception) {
                                                projectError = e.localizedMessage ?: "Project selection failed"
                                            }
                                        }
                                    }
                                    projectPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    (if (project.id == projectId) "✓  " else "") +
                                        (if (project.id == "general") tr("عام", "General") else project.name),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider()
                        OutlinedTextField(
                            value = projectInput,
                            onValueChange = { if (it.length <= 40) projectInput = it },
                            label = { Text(tr("اسم مشروع جديد", "New project name")) },
                            singleLine = true
                        )
                        if (projectError.isNotEmpty()) Text(projectError, color = warningAmber, fontSize = 12.sp)
                        TextButton(onClick = {
                            try {
                                require(anchors.isEmpty() && sampler.count() == 0) {
                                    if (arabic) "احفظ القياسات أو امسحها قبل إنشاء مشروع." else
                                        "Save or clear the current measurement before creating a project."
                                }
                                val nameToCreate = projectInput
                                uiScope.launch {
                                    try {
                                        val (created, updatedProjects) = withContext(Dispatchers.IO) {
                                            val project = projectStore.create(nameToCreate)
                                            project to projectStore.load()
                                        }
                                        projects = updatedProjects
                                        projectId = created.id
                                        projectInput = ""
                                        projectError = ""
                                        projectPicker = false
                                    } catch (e: Exception) {
                                        projectError = e.localizedMessage ?: "Project creation failed"
                                    }
                                }
                            } catch (e: Exception) {
                                projectError = e.localizedMessage ?: tr("اسم المشروع غير صالح", "Invalid project name")
                            }
                        }) { Text(tr("إنشاء مشروع", "Create project")) }
                        HorizontalDivider()
                        Text(
                            tr("نسخ احتياطي محلي لجميع المشاريع والقياسات. احفظه في مكان آمن.",
                               "Offline backup for all projects and measurements. Store it securely."),
                            fontSize = 12.sp, color = paleBlue
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                uiScope.launch {
                                    try {
                                        val data = withContext(Dispatchers.IO) { ProjectBackup.export(context) }
                                        export("BIMMeasure3D-all-projects-backup.json", data)
                                    } catch (e: Exception) {
                                        projectError = e.localizedMessage ?: "Backup failed"
                                    }
                                }
                            }) { Text(tr("تصدير نسخة", "Export backup")) }
                            TextButton(onClick = {
                                backupImporter.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                            }) { Text(tr("استعادة نسخة", "Restore backup")) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { projectPicker = false }) { Text(tr("إغلاق", "Close")) } }
            )
        }
        if (referenceDialog) {
            AlertDialog(
                onDismissRequest = { referenceDialog = false },
                title = { Text(tr("التحقق بقياس مرجعي", "Reference validation")) },
                text = {
                    Column {
                        Text(tr("أدخل الطول المعروف باستخدام شريط قياس بوحدة المتر. النتيجة للمقارنة فقط ولا تُصحح معايرة الكاميرا.",
                            "Enter the tape-measured length in metres. This is an independent comparison, not camera calibration."),
                            fontSize = 13.sp)
                        OutlinedTextField(
                            value = referenceInput,
                            onValueChange = { if (it.length <= 14) referenceInput = it },
                            label = { Text(tr("الطول المرجعي (م)", "Reference length (m)")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        if (referenceError.isNotBlank()) Text(referenceError, color = warningAmber, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        try {
                            val reference = referenceInput.trim().replace(',', '.').toDoubleOrNull()
                                ?: throw IllegalArgumentException("Enter numeric length")
                            referenceResult = ReferenceCheck.compare(measured ?: 0.0, reference)
                            referenceDialog = false
                            message = tr("فرق القياس المرجعي: ", "Measured reference error: ") +
                                String.format(Locale.US, "%.2f%%", referenceResult!!.errorPercent)
                        } catch (e: Exception) { referenceError = e.localizedMessage ?: "Invalid reference" }
                    }) { Text(tr("احسب الفرق", "Compare")) }
                },
                dismissButton = { TextButton(onClick = { referenceDialog = false }) { Text(tr("إلغاء", "Cancel")) } }
            )
        }
        if (showHistory) {
            AlertDialog(
                onDismissRequest = { showHistory = false },
                title = { Text(tr("سجل المشروع", "Project history")) },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        if (savedRecords.none { it.projectId == projectId }) {
                            Text(tr("لا توجد قياسات محفوظة بعد.", "No saved measurements yet."))
                        }
                        savedRecords.filter { it.projectId == projectId }.forEach { record ->
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                (if (record.kind == "LENGTH") tr("الطول", "Length") else tr("المساحة", "Area")) +
                                    (if (record.source == "MANUAL") tr(" · إدخال يدوي", " · manual input")
                                        else tr(" · ARCore", " · ARCore")) +
                                    "  •  " + SimpleDateFormat("yyyy-MM-dd HH:mm", currentLocale).format(Date(record.timestampMillis)),
                                fontSize = 12.sp, color = paleBlue
                            )
                            Text(if (record.kind == "LENGTH") MeasureFormat.distance(record.valueMeters, unit)
                                 else MeasureFormat.area(record.valueMeters, unit), fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    export("measurement_${record.id.take(8)}.dxf",
                                        Exports.dxf(
                                            record.points,
                                            record.kind == "AREA",
                                            if (record.source == "MANUAL") DxfCoordinateSystem.CAD_Z_UP
                                            else dxfCoordinates
                                        ))
                                }) { Text("DXF") }
                                TextButton(onClick = {
                                    export("measurement_${record.id.take(8)}.csv",
                                        Exports.measurementCsv(
                                            if (record.source == "MANUAL") "MANUAL_${record.kind}" else record.kind,
                                            record.points, record.valueMeters, record.perimeterMeters
                                        ))
                                }) { Text("CSV") }
                                TextButton(onClick = {
                                    uiScope.launch {
                                        try {
                                            savedRecords = withContext(Dispatchers.IO) {
                                                store.delete(record.id)
                                                store.load()
                                            }
                                        } catch (e: Exception) {
                                            message = tr("تعذر حذف السجل: ", "Delete failed: ") +
                                                (e.localizedMessage ?: "")
                                        }
                                    }
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
