package com.bimspecialist.measure3d.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedMeasurement(
    val id: String,
    val kind: String,
    val valueMeters: Double,
    val perimeterMeters: Double?,
    val points: List<Vec3>,
    val timestampMillis: Long,
    val projectId: String = "general"
)

class MeasurementStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bim_measure_3d", Context.MODE_PRIVATE)
    private val key = "measurements_v2"

    fun load(): List<SavedMeasurement> {
        return try {
            val entries = JSONArray(prefs.getString(key, "[]") ?: "[]")
            buildList {
                for (i in 0 until entries.length()) {
                    val item = entries.getJSONObject(i)
                    val rawPoints = item.getJSONArray("points")
                    val points = buildList {
                        for (j in 0 until rawPoints.length()) {
                            val v = rawPoints.getJSONArray(j)
                            add(Vec3(v.getDouble(0), v.getDouble(1), v.getDouble(2)))
                        }
                    }
                    add(SavedMeasurement(
                        id = item.getString("id"),
                        kind = item.getString("kind"),
                        valueMeters = item.getDouble("valueMeters"),
                        perimeterMeters = if (item.isNull("perimeterMeters")) null else item.getDouble("perimeterMeters"),
                        points = points,
                        timestampMillis = item.getLong("timestampMillis"),
                        projectId = item.optString("projectId", "general").ifBlank { "general" }
                    ))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(records: List<SavedMeasurement>) {
        val array = JSONArray()
        records.take(100).forEach { m ->
            val points = JSONArray()
            m.points.forEach { p ->
                points.put(JSONArray().put(p.x).put(p.y).put(p.z))
            }
            array.put(JSONObject()
                .put("id", m.id)
                .put("kind", m.kind)
                .put("valueMeters", m.valueMeters)
                .put("perimeterMeters", m.perimeterMeters ?: JSONObject.NULL)
                .put("points", points)
                .put("timestampMillis", m.timestampMillis)
                .put("projectId", m.projectId)
            )
        }
        check(prefs.edit().putString(key, array.toString()).commit()) { "Could not save measurements" }
    }

    fun add(kind: String, value: Double, perimeter: Double?, points: List<Vec3>, projectId: String = "general"): SavedMeasurement {
        require(projectId.isNotBlank())
        require(points.size in 2..64 && points.all { it.isFinite() })
        require(perimeter == null || perimeter.isFinite())
        require(kind == "LENGTH" || kind == "AREA")
        require(value.isFinite() && value >= 0)
        val record = SavedMeasurement(
            id = UUID.randomUUID().toString(),
            kind = kind,
            valueMeters = value,
            perimeterMeters = perimeter,
            points = points.toList(),
            timestampMillis = System.currentTimeMillis(),
            projectId = projectId
        )
        persist(listOf(record) + load())
        return record
    }

    fun delete(id: String) = persist(load().filterNot { it.id == id })
}
