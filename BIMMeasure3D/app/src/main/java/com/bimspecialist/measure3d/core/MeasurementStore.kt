package com.bimspecialist.measure3d.core

import android.content.Context

data class SavedMeasurement(
    val id: String,
    val kind: String,
    val valueMeters: Double,
    val perimeterMeters: Double?,
    val points: List<Vec3>,
    val timestampMillis: Long,
    val projectId: String = "general",
    val source: String = "ARCORE"
)

/** Compatibility façade: no read-modify-write of the entire measurement list. */
class MeasurementStore(context: Context) {
    private val db = FieldDatabase.get(context)

    fun load(): List<SavedMeasurement> = db.measurements()

    fun add(
        kind: String, value: Double, perimeter: Double?,
        points: List<Vec3>, projectId: String = "general", source: String = "ARCORE"
    ): SavedMeasurement = db.addMeasurement(kind, value, perimeter, points, projectId, source)

    fun delete(id: String) = db.deleteMeasurement(id)
}
