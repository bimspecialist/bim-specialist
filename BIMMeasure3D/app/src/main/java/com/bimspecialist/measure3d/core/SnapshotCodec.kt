package com.bimspecialist.measure3d.core

import org.json.JSONArray
import org.json.JSONObject

/** Validated, versioned representation for backup, import and legacy migration. */
data class FieldSnapshot(
    val projects: List<FieldProject>,
    val selectedProject: String,
    val measurements: List<SavedMeasurement>,
    val unit: DisplayUnit
)

object SnapshotCodec {
    private const val MAX_BYTES = 20_000_000

    fun decode(bytes: ByteArray): FieldSnapshot {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Backup is empty or exceeds 20 MB" }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getString("format") == "BIMMeasure3D-Backup" && root.getInt("schemaVersion") == 1) {
            "Unsupported backup format"
        }
        val rawProjects = root.getJSONArray("projects")
        val rawMeasurements = root.getJSONArray("measurements")
        require(rawProjects.length() <= 30 && rawMeasurements.length() <= 100_000) { "Backup limit exceeded" }
        val projects = ArrayList<FieldProject>(rawProjects.length() + 1)
        projects.add(FieldProject("general", "General", 0L))
        val ids = mutableSetOf("general")
        val names = mutableSetOf("general")
        for (i in 0 until rawProjects.length()) {
            val p = rawProjects.getJSONObject(i)
            val id = p.getString("id")
            val name = p.getString("name").trim()
            val created = p.getLong("createdAtMillis")
            require(id.length in 1..80 && id != "general" && ids.add(id)) { "Duplicate or invalid project id" }
            require(name.length in 2..40 && names.add(name.lowercase())) { "Duplicate or invalid project name" }
            require(created >= 0) { "Invalid project timestamp" }
            projects.add(FieldProject(id, name, created))
        }
        val selected = root.optString("selectedProject", "general")
        require(selected in ids) { "Selected project is missing" }
        val unit = DisplayUnit.entries.firstOrNull {
            it.name == root.optString("unit", DisplayUnit.METRE.name)
        } ?: throw IllegalArgumentException("Unknown measurement unit")

        val measurements = ArrayList<SavedMeasurement>(rawMeasurements.length())
        val measurementIds = HashSet<String>()
        for (i in 0 until rawMeasurements.length()) {
            val m = rawMeasurements.getJSONObject(i)
            val id = m.getString("id")
            val kind = m.getString("kind")
            val value = m.getDouble("valueMeters")
            val perimeter = if (m.isNull("perimeterMeters")) null else m.getDouble("perimeterMeters")
            val timestamp = m.getLong("timestampMillis")
            val projectId = m.optString("projectId", "general").ifBlank { "general" }
            val source = m.optString("source", "ARCORE")
            val rawPoints = m.getJSONArray("points")
            require(id.length in 1..80 && measurementIds.add(id)) { "Invalid or duplicate record id" }
            require(kind == "LENGTH" || kind == "AREA") { "Unsupported measurement kind" }
            require(source in setOf("ARCORE", "MANUAL")) { "Unsupported measurement source" }
            require(value.isFinite() && value >= 0.0) { "Invalid measurement" }
            require(perimeter == null || (perimeter.isFinite() && perimeter >= 0.0))
            require(timestamp >= 0 && projectId in ids) { "Invalid project association" }
            require(rawPoints.length() in (if (kind == "LENGTH") 2..2 else 3..64)) {
                "Invalid number of measured points"
            }
            val points = buildList {
                for (j in 0 until rawPoints.length()) {
                    val p = rawPoints.getJSONArray(j)
                    require(p.length() == 3) { "Bad point dimension" }
                    val v = Vec3(p.getDouble(0), p.getDouble(1), p.getDouble(2))
                    require(v.isFinite()) { "Non-finite point" }
                    add(v)
                }
            }
            measurements.add(SavedMeasurement(id, kind, value, perimeter, points, timestamp, projectId, source))
        }
        return FieldSnapshot(projects, selected, measurements, unit)
    }

    fun encode(snapshot: FieldSnapshot): ByteArray {
        require(snapshot.projects.isNotEmpty() && snapshot.projects.first().id == "general")
        val projects = JSONArray()
        snapshot.projects.filter { it.id != "general" }.forEach { p ->
            projects.put(JSONObject().put("id", p.id).put("name", p.name)
                .put("createdAtMillis", p.createdAtMillis))
        }
        val records = JSONArray()
        snapshot.measurements.forEach { m ->
            val points = JSONArray()
            m.points.forEach { p -> points.put(JSONArray().put(p.x).put(p.y).put(p.z)) }
            records.put(JSONObject()
                .put("id", m.id).put("kind", m.kind)
                .put("valueMeters", m.valueMeters)
                .put("perimeterMeters", m.perimeterMeters ?: JSONObject.NULL)
                .put("timestampMillis", m.timestampMillis)
                .put("projectId", m.projectId)
                .put("source", m.source)
                .put("points", points))
        }
        val root = JSONObject()
            .put("format", "BIMMeasure3D-Backup")
            .put("schemaVersion", 1)
            .put("projects", projects)
            .put("selectedProject", snapshot.selectedProject)
            .put("measurements", records)
            .put("unit", snapshot.unit.name)
        val bytes = root.toString(2).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Backup exceeds 20 MB; reduce or split the project" }
        // Validate before offering the serialized backup to the document picker.
        decode(bytes)
        return bytes
    }
}
