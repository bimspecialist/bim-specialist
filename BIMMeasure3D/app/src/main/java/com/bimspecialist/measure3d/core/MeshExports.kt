package com.bimspecialist.measure3d.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

object MeshExports {
    private fun coord(v: Double): String = String.format(Locale.US, "%.6f", v * 1000.0)

    /** Binary STL is unitless by definition; this exporter writes numeric coordinates in mm. */
    fun stlMillimetres(mesh: SolidMesh): ByteArray {
        require(mesh.faces.size <= 1_000_000)
        val size = 84L + mesh.faces.size * 50L
        require(size <= Int.MAX_VALUE)
        val buffer = ByteBuffer.allocate(size.toInt()).order(ByteOrder.LITTLE_ENDIAN)
        val header = "BIM Measure 3D | mm | measured outline extrusion | NOT SCAN".toByteArray(Charsets.US_ASCII)
        buffer.put(header, 0, minOf(80, header.size))
        buffer.position(80)
        buffer.putInt(mesh.faces.size)
        for (face in mesh.faces) {
            val a = mesh.vertices[face.a]
            val b = mesh.vertices[face.b]
            val c = mesh.vertices[face.c]
            val cross = (b - a).cross(c - a)
            val length = cross.norm()
            require(length.isFinite() && length > 1e-12) { "Degenerate mesh triangle" }
            buffer.putFloat((cross.x / length).toFloat())
            buffer.putFloat((cross.y / length).toFloat())
            buffer.putFloat((cross.z / length).toFloat())
            for (p in listOf(a, b, c)) {
                buffer.putFloat((p.x * 1000).toFloat())
                buffer.putFloat((p.y * 1000).toFloat())
                buffer.putFloat((p.z * 1000).toFloat())
            }
            buffer.putShort(0)
        }
        return buffer.array()
    }

    fun objMillimetres(mesh: SolidMesh): ByteArray {
        val out = StringBuilder("# BIM Measure 3D - polygon extrusion\n# Coordinate unit: millimetres\n")
        out.append("o measured_outline_solid\n")
        for (v in mesh.vertices) out.append("v ").append(coord(v.x)).append(' ')
            .append(coord(v.y)).append(' ').append(coord(v.z)).append('\n')
        for (face in mesh.faces) out.append("f ").append(face.a + 1).append(' ')
            .append(face.b + 1).append(' ').append(face.c + 1).append('\n')
        return out.toString().toByteArray(Charsets.US_ASCII)
    }
}
