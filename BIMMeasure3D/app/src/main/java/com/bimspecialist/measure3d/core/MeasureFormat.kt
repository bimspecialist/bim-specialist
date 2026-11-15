package com.bimspecialist.measure3d.core

import java.util.Locale

enum class DisplayUnit(val label: String, val factor: Double) {
    METRE("m", 1.0),
    CENTIMETRE("cm", 100.0),
    MILLIMETRE("mm", 1000.0)
}

object MeasureFormat {
    fun distance(metres: Double, unit: DisplayUnit): String {
        val decimals = if (unit == DisplayUnit.METRE) 3 else 1
        return String.format(Locale.US, "%.${decimals}f %s", metres * unit.factor, unit.label)
    }

    fun area(squareMetres: Double, unit: DisplayUnit): String {
        val decimals = when (unit) {
            DisplayUnit.METRE -> 3
            DisplayUnit.CENTIMETRE -> 1
            DisplayUnit.MILLIMETRE -> 0
        }
        return String.format(Locale.US, "%.${decimals}f %s²", squareMetres * unit.factor * unit.factor, unit.label)
    }
}
