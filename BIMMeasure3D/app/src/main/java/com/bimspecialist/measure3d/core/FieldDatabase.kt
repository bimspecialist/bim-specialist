package com.bimspecialist.measure3d.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One SQLite transaction owns all project and measurement mutations.
 * Legacy SharedPreferences are read-only migration inputs and NEVER cleared.
 */
class FieldDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "bim_measure_3d_records_v1.db", null, 2) {

    private val app = context.applicationContext

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE projects (
            id TEXT PRIMARY KEY NOT NULL,
            name TEXT NOT NULL COLLATE NOCASE UNIQUE,
            created_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE measurements (
            id TEXT PRIMARY KEY NOT NULL,
            kind TEXT NOT NULL,
            value_m REAL NOT NULL,
            perimeter_m REAL,
            points_json TEXT NOT NULL,
            recorded_at INTEGER NOT NULL,
            project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,
            source TEXT NOT NULL DEFAULT 'ARCORE'
        )""")
        db.execSQL("CREATE INDEX measurements_by_date ON measurements(recorded_at DESC)")
        db.execSQL("CREATE INDEX measurements_by_project ON measurements(project_id,recorded_at DESC)")
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")

        // SQLiteOpenHelper runs onCreate in a transaction. Any failed legacy parse
        // rolls it back. Never erase original preference files.
        val legacyProjects = app.getSharedPreferences("bim_measure_projects_v1", Context.MODE_PRIVATE)
        val legacyMeasures = app.getSharedPreferences("bim_measure_3d", Context.MODE_PRIVATE)
        val legacyDisplay = app.getSharedPreferences("bim_measure_display", Context.MODE_PRIVATE)
        val root = JSONObject()
            .put("format", "BIMMeasure3D-Backup")
            .put("schemaVersion", 1)
            .put("projects", JSONArray(legacyProjects.getString("projects", "[]") ?: "[]"))
            .put("selectedProject", legacyProjects.getString("selected", "general") ?: "general")
            .put("measurements", JSONArray(legacyMeasures.getString("measurements_v2", "[]") ?: "[]"))
            .put("unit", legacyDisplay.getString("unit", "METRE") ?: "METRE")
        val imported = SnapshotCodec.decode(root.toString().toByteArray(Charsets.UTF_8))
        writeSnapshot(db, imported, clearExisting = false)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // SQLiteOpenHelper executes onUpgrade inside a transaction; never drop user tables.
        if (oldVersion == 1 && newVersion == 2) {
            db.execSQL("ALTER TABLE measurements ADD COLUMN source TEXT NOT NULL DEFAULT 'ARCORE'")
        } else {
            throw IllegalStateException("Unsupported database upgrade from $oldVersion to $newVersion")
        }
    }

    private fun setting(db: SQLiteDatabase, key: String, fallback: String): String {
        db.query("settings", arrayOf("value"), "key=?", arrayOf(key), null, null, null).use {
            return if (it.moveToFirst()) it.getString(0) else fallback
        }
    }

    private fun setSetting(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict("settings", null, ContentValues().apply {
            put("key", key); put("value", value)
        }, SQLiteDatabase.CONFLICT_REPLACE).also { require(it != -1L) }
    }

    private inline fun <T> atomic(db: SQLiteDatabase, block: () -> T): T {
        db.beginTransaction()
        try {
            val value = block()
            db.setTransactionSuccessful()
            return value
        } finally { db.endTransaction() }
    }

    private fun putProject(db: SQLiteDatabase, project: FieldProject) {
        db.insertOrThrow("projects", null, ContentValues().apply {
            put("id", project.id); put("name", project.name); put("created_at", project.createdAtMillis)
        })
    }

    private fun jsonPoints(points: List<Vec3>): String {
        val result = JSONArray()
        points.forEach { p ->
            require(p.isFinite())
            result.put(JSONArray().put(p.x).put(p.y).put(p.z))
        }
        return result.toString()
    }

    private fun putMeasurement(db: SQLiteDatabase, record: SavedMeasurement) {
        require(record.kind == "LENGTH" || record.kind == "AREA")
        require(record.points.size in (if (record.kind == "LENGTH") 2..2 else 3..64))
        require(record.source in setOf("ARCORE", "MANUAL"))
        require(record.valueMeters.isFinite() && record.valueMeters >= 0)
        require(record.perimeterMeters == null || (record.perimeterMeters.isFinite() && record.perimeterMeters >= 0))
        db.insertOrThrow("measurements", null, ContentValues().apply {
            put("id", record.id)
            put("kind", record.kind)
            put("value_m", record.valueMeters)
            if (record.perimeterMeters == null) putNull("perimeter_m")
            else put("perimeter_m", record.perimeterMeters)
            put("points_json", jsonPoints(record.points))
            put("recorded_at", record.timestampMillis)
            put("project_id", record.projectId)
            put("source", record.source)
        })
    }

    private fun writeSnapshot(db: SQLiteDatabase, snapshot: FieldSnapshot, clearExisting: Boolean) {
        if (clearExisting) {
            db.delete("measurements", null, null)
            db.delete("projects", null, null)
            db.delete("settings", null, null)
        }
        snapshot.projects.forEach { putProject(db, it) }
        snapshot.measurements.forEach { putMeasurement(db, it) }
        setSetting(db, "selectedProject", snapshot.selectedProject)
        setSetting(db, "unit", snapshot.unit.name)
    }

    @Synchronized fun projects(): List<FieldProject> {
        val result = ArrayList<FieldProject>()
        readableDatabase.query("projects", arrayOf("id", "name", "created_at"), null, null,
            null, null, "created_at ASC, id ASC").use { c ->
            while (c.moveToNext()) result.add(FieldProject(c.getString(0), c.getString(1), c.getLong(2)))
        }
        require(result.any { it.id == "general" }) { "General project missing" }
        return result
    }

    @Synchronized fun selectedProject(): String =
        setting(readableDatabase, "selectedProject", "general")

    @Synchronized fun unit(): DisplayUnit {
        val value = setting(readableDatabase, "unit", "METRE")
        return DisplayUnit.entries.firstOrNull { it.name == value }
            ?: throw IllegalStateException("Unknown stored unit")
    }

    @Synchronized fun saveUnit(unit: DisplayUnit) {
        val db = writableDatabase
        atomic(db) { setSetting(db, "unit", unit.name) }
    }

    @Synchronized fun selectProject(projectId: String) {
        val db = writableDatabase
        atomic(db) {
            require(projectExists(db, projectId)) { "Unknown project" }
            setSetting(db, "selectedProject", projectId)
        }
    }

    private fun projectExists(db: SQLiteDatabase, projectId: String): Boolean =
        db.rawQuery("SELECT 1 FROM projects WHERE id=? LIMIT 1", arrayOf(projectId))
            .use { it.moveToFirst() }

    @Synchronized fun createProject(rawName: String): FieldProject {
        val name = rawName.trim()
        require(name.length in 2..40)
        val record = FieldProject(UUID.randomUUID().toString(), name, System.currentTimeMillis())
        val db = writableDatabase
        atomic(db) {
            val count = db.rawQuery("SELECT COUNT(*) FROM projects", null).use {
                it.moveToFirst(); it.getInt(0)
            }
            require(count < 31) { "Project limit reached" }
            putProject(db, record)
            setSetting(db, "selectedProject", record.id)
        }
        return record
    }

    @Synchronized fun measurements(): List<SavedMeasurement> {
        val result = ArrayList<SavedMeasurement>()
        readableDatabase.query("measurements",
            arrayOf("id", "kind", "value_m", "perimeter_m", "points_json", "recorded_at", "project_id", "source"),
            null, null, null, null, "recorded_at DESC, id ASC").use { c ->
            while (c.moveToNext()) {
                val pointsJson = JSONArray(c.getString(4))
                val points = buildList {
                    for (i in 0 until pointsJson.length()) {
                        val entry = pointsJson.getJSONArray(i)
                        require(entry.length() == 3)
                        val v = Vec3(entry.getDouble(0), entry.getDouble(1), entry.getDouble(2))
                        require(v.isFinite()) { "Corrupt measurement point" }
                        add(v)
                    }
                }
                val kind = c.getString(1)
                require(kind == "LENGTH" || kind == "AREA")
                require(points.size in (if (kind == "LENGTH") 2..2 else 3..64))
                val value = c.getDouble(2)
                val perimeter = if (c.isNull(3)) null else c.getDouble(3)
                require(value.isFinite() && value >= 0)
                require(perimeter == null || (perimeter.isFinite() && perimeter >= 0))
                result.add(SavedMeasurement(c.getString(0), kind, value, perimeter, points,
                    c.getLong(5), c.getString(6), c.getString(7)))
            }
        }
        return result
    }

    @Synchronized fun addMeasurement(kind: String, value: Double, perimeter: Double?,
        points: List<Vec3>, projectId: String, source: String = "ARCORE"): SavedMeasurement {
        val record = SavedMeasurement(UUID.randomUUID().toString(), kind, value, perimeter,
            points.toList(), System.currentTimeMillis(), projectId, source)
        val db = writableDatabase
        atomic(db) { putMeasurement(db, record) }
        return record
    }

    @Synchronized fun deleteMeasurement(id: String) {
        val db = writableDatabase
        atomic(db) { db.delete("measurements", "id=?", arrayOf(id)) }
    }

    @Synchronized fun snapshot(): FieldSnapshot {
        val db = readableDatabase
        db.beginTransaction()
        try {
            val result = FieldSnapshot(projects(), selectedProject(), measurements(), unit())
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }

    @Synchronized fun restore(snapshot: FieldSnapshot) {
        // Validate all relationships *before* making any database changes.
        val valid = SnapshotCodec.decode(SnapshotCodec.encode(snapshot))
        val db = writableDatabase
        atomic(db) { writeSnapshot(db, valid, clearExisting = true) }
    }

    companion object {
        @Volatile private var instance: FieldDatabase? = null
        fun get(context: Context): FieldDatabase = instance ?: synchronized(this) {
            instance ?: FieldDatabase(context.applicationContext).also { instance = it }
        }

        /** Test-only: close database before cleaning Robolectric's sandbox. */
        internal fun resetForTests() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
