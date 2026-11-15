package com.bimspecialist.measure3d.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BackupContents(val projects: Int, val measurements: Int)

/**
 * Local, user-selected JSON archive. It contains measured coordinates and must be treated
 * as potentially confidential. Imported data is validated before modifying app storage.
 */
object ProjectBackup {
    private const val MAX_BACKUP_BYTES = 2_000_000

    fun export(context: Context): ByteArray {
        val app = context.applicationContext
        val projectPrefs = app.getSharedPreferences("bim_measure_projects_v1", Context.MODE_PRIVATE)
        val measurePrefs = app.getSharedPreferences("bim_measure_3d", Context.MODE_PRIVATE)
        val displayPrefs = app.getSharedPreferences("bim_measure_display", Context.MODE_PRIVATE)
        val data = JSONObject()
            .put("format", "BIMMeasure3D-Backup")
            .put("schemaVersion", 1)
            .put("projects", JSONArray(projectPrefs.getString("projects", "[]") ?: "[]"))
            .put("selectedProject", projectPrefs.getString("selected", "general") ?: "general")
            .put("measurements", JSONArray(measurePrefs.getString("measurements_v2", "[]") ?: "[]"))
            .put("unit", displayPrefs.getString("unit", "METRE") ?: "METRE")
        return data.toString(2).toByteArray(Charsets.UTF_8)
    }

    private fun validated(bytes: ByteArray): Pair<JSONObject, BackupContents> {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BACKUP_BYTES) { "Backup file is too large or empty" }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optString("format") == "BIMMeasure3D-Backup" && root.optInt("schemaVersion") == 1) {
            "Unsupported backup format"
        }
        val projects = root.getJSONArray("projects")
        val measurements = root.getJSONArray("measurements")
        require(projects.length() <= 30 && measurements.length() <= 100) { "Too many records" }

        val validIds = mutableSetOf("general")
        val validNames = mutableSetOf("general")
        for (i in 0 until projects.length()) {
            val project = projects.getJSONObject(i)
            val id = project.getString("id")
            val name = project.getString("name").trim()
            require(id.length in 1..80 && id != "general" && validIds.add(id))
            require(name.length in 2..40 && validNames.add(name.lowercase()))
            require(project.getLong("createdAtMillis") >= 0)
        }
        require(root.getString("selectedProject") in validIds)
        require(root.getString("unit") in DisplayUnit.entries.map { it.name })

        val ids = mutableSetOf<String>()
        for (i in 0 until measurements.length()) {
            val m = measurements.getJSONObject(i)
            val id = m.getString("id")
            val kind = m.getString("kind")
            val measured = m.getDouble("valueMeters")
            require(id.isNotBlank() && id.length <= 80 && ids.add(id))
            require(kind == "LENGTH" || kind == "AREA")
            require(measured.isFinite() && measured in 0.0..1_000_000_000.0)
            if (!m.isNull("perimeterMeters")) {
                val perimeter = m.getDouble("perimeterMeters")
                require(perimeter.isFinite() && perimeter in 0.0..1_000_000_000.0)
            }
            require(m.getLong("timestampMillis") >= 0)
            require(m.optString("projectId", "general") in validIds)
            val points = m.getJSONArray("points")
            require(points.length() in (if (kind == "LENGTH") 2..2 else 3..64))
            for (j in 0 until points.length()) {
                val p = points.getJSONArray(j)
                require(p.length() == 3)
                for (k in 0..2) {
                    val v = p.getDouble(k)
                    require(v.isFinite() && v in -1_000_000.0..1_000_000.0)
                }
            }
        }
        return root to BackupContents(projects.length() + 1, measurements.length())
    }

    fun inspect(bytes: ByteArray): BackupContents = validated(bytes).second

    /**
     * Replace all locally saved projects and measurements after explicit UI confirmation.
     * The original values are restored on a failed SharedPreferences commit.
     */
    fun restore(context: Context, bytes: ByteArray): BackupContents {
        val (root, summary) = validated(bytes)
        val app = context.applicationContext
        val projects = app.getSharedPreferences("bim_measure_projects_v1", Context.MODE_PRIVATE)
        val measures = app.getSharedPreferences("bim_measure_3d", Context.MODE_PRIVATE)
        val display = app.getSharedPreferences("bim_measure_display", Context.MODE_PRIVATE)
        val oldProjects = projects.getString("projects", "[]")
        val oldSelected = projects.getString("selected", "general")
        val oldMeasures = measures.getString("measurements_v2", "[]")
        val oldUnit = display.getString("unit", "METRE")
        try {
            check(projects.edit()
                .putString("projects", root.getJSONArray("projects").toString())
                .putString("selected", root.getString("selectedProject"))
                .commit()) { "Cannot restore project data" }
            check(measures.edit()
                .putString("measurements_v2", root.getJSONArray("measurements").toString())
                .commit()) { "Cannot restore measurement data" }
            check(display.edit().putString("unit", root.getString("unit")).commit()) {
                "Cannot restore display settings"
            }
        } catch (e: Exception) {
            projects.edit().putString("projects", oldProjects).putString("selected", oldSelected).commit()
            measures.edit().putString("measurements_v2", oldMeasures).commit()
            display.edit().putString("unit", oldUnit).commit()
            throw e
        }
        return summary
    }
}
