package com.bimspecialist.measure3d.core

import kotlin.math.abs

data class ReferenceResult(
    val measuredMetres: Double,
    val referenceMetres: Double,
    val errorMetres: Double,
    val errorPercent: Double
)

object ReferenceCheck {
    fun compare(measuredMetres: Double, referenceMetres: Double): ReferenceResult {
        require(measuredMetres.isFinite() && measuredMetres >= 0)
        require(referenceMetres.isFinite() && referenceMetres > 0)
        val error = measuredMetres - referenceMetres
        return ReferenceResult(measuredMetres, referenceMetres, error, abs(error) / referenceMetres * 100)
    }
}
