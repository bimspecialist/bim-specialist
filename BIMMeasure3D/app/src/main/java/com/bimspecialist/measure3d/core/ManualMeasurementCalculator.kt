package com.bimspecialist.measure3d.core

enum class ManualMeasureMode { LENGTH, RECTANGULAR_AREA }

/** Geometric results computed exclusively from dimensions entered by the user. */
data class ManualMeasurement(
    val kind: String,
    val valueMetresOrSquareMetres: Double,
    val perimeterMetres: Double?,
    val points: List<Vec3>,
    val lengthMetres: Double,
    val widthMetres: Double?
)

object ManualMeasurementCalculator {
    private fun normalizedNumeric(raw: String): String = raw.trim().map { c ->
        when (c) {
            in '٠'..'٩' -> ('0'.code + c.code - '٠'.code).toChar()
            in '۰'..'۹' -> ('0'.code + c.code - '۰'.code).toChar()
            '٫', ',' -> '.'
            else -> c
        }
    }.joinToString("")

    fun parseLength(raw: String, unit: DisplayUnit): Double {
        val number = normalizedNumeric(raw)
        // Reject thousands separators, exponents, signs, expressions and infinity.
        require(number.matches(Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) {
            "Enter a positive numeric dimension"
        }
        val metres = (number.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number")) / unit.factor
        require(metres.isFinite() && metres in 0.001..1000.0) {
            "Enter a length between 1 mm and 1000 m"
        }
        return metres
    }

    fun calculate(
        mode: ManualMeasureMode,
        lengthInput: String,
        widthInput: String,
        unit: DisplayUnit
    ): ManualMeasurement {
        val length = parseLength(lengthInput, unit)
        return when (mode) {
            ManualMeasureMode.LENGTH -> ManualMeasurement(
                "LENGTH", length, null,
                listOf(Vec3(0.0, 0.0, 0.0), Vec3(length, 0.0, 0.0)),
                length, null
            )
            ManualMeasureMode.RECTANGULAR_AREA -> {
                val width = parseLength(widthInput, unit)
                val footprint = listOf(
                    Vec3(0.0, 0.0, 0.0),
                    Vec3(length, 0.0, 0.0),
                    // Horizontal plane in ARCore-like local coordinates: Y is up.
                    // CAD_Z_UP rotates it onto the horizontal XY plane in DXF.
                    Vec3(length, 0.0, width),
                    Vec3(0.0, 0.0, width)
                )
                ManualMeasurement(
                    "AREA", length * width, 2 * (length + width),
                    footprint, length, width
                )
            }
        }
    }
}
