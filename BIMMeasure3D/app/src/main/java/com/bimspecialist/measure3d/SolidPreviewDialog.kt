package com.bimspecialist.measure3d

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimspecialist.measure3d.core.SolidMesh
import com.bimspecialist.measure3d.core.Vec3
import kotlin.math.*

/** Offline, rotatable wireframe preview of the generated solid, independent of the camera session. */
@Composable
internal fun SolidPreviewDialog(mesh: SolidMesh, arabic: Boolean, onDismiss: () -> Unit) {
    var yaw by remember(mesh) { mutableFloatStateOf(0.5f) }
    var pitch by remember(mesh) { mutableFloatStateOf(0.25f) }
    var zoom by remember(mesh) { mutableFloatStateOf(1f) }
    val center = remember(mesh) {
        Vec3(
            mesh.vertices.map { it.x }.average(),
            mesh.vertices.map { it.y }.average(),
            mesh.vertices.map { it.z }.average()
        )
    }
    val radius = remember(mesh, center) {
        max(0.001, mesh.vertices.maxOf { (it - center).norm() })
    }
    val edges = remember(mesh) {
        buildSet {
            for (face in mesh.faces) {
                for ((a, b) in listOf(face.a to face.b, face.b to face.c, face.c to face.a)) {
                    add(minOf(a, b) to maxOf(a, b))
                }
            }
        }.toList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (arabic) "معاينة المجسم الهندسي" else "Engineering solid preview") },
        text = {
            Column {
                Text(
                    if (arabic) "اسحب لتدوير المجسم. هذا نموذج ناتج عن بثق حدود المساحة، وليس مسحًا ثلاثي الأبعاد."
                    else "Drag to rotate. This is a solid extruded from the measured outline, not a 3D scan.",
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Canvas(
                    Modifier.fillMaxWidth().height(260.dp)
                        .background(Color(0xFF0E1E33))
                        .pointerInput(mesh) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                yaw += drag.x * 0.008f
                                pitch = (pitch + drag.y * 0.008f).coerceIn(-1.5f, 1.5f)
                            }
                        }
                ) {
                    val scale = minOf(size.width, size.height) * 0.38f / radius.toFloat() * zoom
                    val cy = cos(yaw.toDouble())
                    val sy = sin(yaw.toDouble())
                    val cp = cos(pitch.toDouble())
                    val sp = sin(pitch.toDouble())
                    val points = mesh.vertices.map { v ->
                        val p = v - center
                        val x = p.x * cy - p.z * sy
                        val z = p.x * sy + p.z * cy
                        val y = p.y * cp - z * sp
                        Offset(size.width / 2f + (x * scale).toFloat(),
                            size.height / 2f - (y * scale).toFloat())
                    }
                    for ((a, b) in edges) {
                        drawLine(
                            Color(0xFF66C2FF),
                            points[a], points[b],
                            strokeWidth = 1.8.dp.toPx()
                        )
                    }
                    points.forEach { p ->
                        drawCircle(Color(0xFFA9E5FF), radius = 2.dp.toPx(), center = p)
                    }
                }
                Text(
                    (if (arabic) "الارتفاع " else "Height ") +
                        String.format(java.util.Locale.US, "%.3f m", mesh.extrudedMetres) +
                        "    ·    " + mesh.faces.size + (if (arabic) " مثلث" else " triangles"),
                    fontSize = 12.sp
                )
                Text(if (arabic) "تكبير / تصغير" else "Zoom", fontSize = 11.sp)
                Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 0.5f..2f)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (arabic) "إغلاق" else "Close") }
        }
    )
}
