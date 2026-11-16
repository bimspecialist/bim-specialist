package com.bimspecialist.measure3d.core

import java.util.Locale

object Exports {
    private fun number(v: Double): String {
        require(v.isFinite()) { "Invalid coordinate" }
        return String.format(Locale.US, "%.6f", v)
    }

    fun ply(points: List<Vec3>): ByteArray {
        require(points.all { it.isFinite() })
        val output = StringBuilder("ply\nformat ascii 1.0\ncomment Units: meters. Coordinates are local to AR tracking session.\n")
        output.append("element vertex ${points.size}\nproperty float x\nproperty float y\nproperty float z\nend_header\n")
        points.forEach { p -> output.append("${number(p.x)} ${number(p.y)} ${number(p.z)}\n") }
        return output.toString().toByteArray(Charsets.US_ASCII)
    }

    fun xyz(points: List<Vec3>): ByteArray {
        require(points.all { it.isFinite() })
        return points.joinToString(separator = "\n", postfix = "\n") { p ->
            "${number(p.x)} ${number(p.y)} ${number(p.z)}"
        }.toByteArray(Charsets.US_ASCII)
    }

    fun measurementCsv(kind: String, points: List<Vec3>, value: Double, perimeter: Double?): ByteArray {
        require(kind == "LENGTH" || kind == "AREA")
        require(value.isFinite() && (perimeter == null || perimeter.isFinite()) && points.all { it.isFinite() })
        val lines = StringBuilder("kind,length_m,area_m2,perimeter_m,point,x_m,y_m,z_m\r\n")
        val lengthValue = if (kind == "LENGTH") number(value) else ""
        val areaValue = if (kind == "AREA") number(value) else ""
        points.forEachIndexed { index, p ->
            lines.append("${kind},${lengthValue},${areaValue},${perimeter?.let(::number) ?: ""},${index + 1},${number(p.x)},${number(p.y)},${number(p.z)}\r\n")
        }
        return lines.toString().toByteArray(Charsets.UTF_8)
    }

    fun dxf(points: List<Vec3>, closed: Boolean, axes: DxfCoordinateSystem = DxfCoordinateSystem.ARCORE_RAW): ByteArray {
        require(points.size >= 2 && points.all { it.isFinite() })
        val output = StringBuilder("0\nSECTION\n2\nHEADER\n9\n\u0024ACADVER\n1\nAC1015\n9\n\u0024INSUNITS\n70\n4\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n")
        output.append("999\\nBIMMeasure3D local session; coordinate axes: ${axes.name}; units: mm\\n")
        for (i in 0 until (if (closed) points.size else points.size - 1)) {
            val a = CadAxes.fromArCore(points[i], axes)
            val b = CadAxes.fromArCore(points[(i + 1) % points.size], axes)
            output.append("0\nLINE\n8\nBIM_MEASURE\n")
            output.append("10\n${number(a.x * 1000)}\n20\n${number(a.y * 1000)}\n30\n${number(a.z * 1000)}\n")
            output.append("11\n${number(b.x * 1000)}\n21\n${number(b.y * 1000)}\n31\n${number(b.z * 1000)}\n")
        }
        output.append("0\nENDSEC\n0\nEOF\n")
        return output.toString().toByteArray(Charsets.US_ASCII)
    }
}
