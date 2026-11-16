package com.bimspecialist.measure3d.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs SQLite storage and legacy migration using a simulated Android runtime. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FieldDatabaseRobolectricTests {
    private lateinit var context: Context
    private val dbName = "bim_measure_3d_records_v1.db"

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        FieldDatabase.resetForTests()
        context.deleteDatabase(dbName)
        for (name in listOf("bim_measure_projects_v1", "bim_measure_3d", "bim_measure_display")) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @After fun cleanup() {
        FieldDatabase.resetForTests()
        context.deleteDatabase(dbName)
    }

    private fun demoPoints() = listOf(
        Vec3(0.0,0.0,0.0), Vec3(1.0,0.0,0.0)
    )

    @Test fun legacyPreferencesMigrateWithoutDeletingSourceRecords() {
        val projectPrefs = context.getSharedPreferences("bim_measure_projects_v1", Context.MODE_PRIVATE)
        val recordPrefs = context.getSharedPreferences("bim_measure_3d", Context.MODE_PRIVATE)
        val displayPrefs = context.getSharedPreferences("bim_measure_display", Context.MODE_PRIVATE)
        val projects = JSONArray().put(JSONObject()
            .put("id", "legacy-job").put("name", "Existing project").put("createdAtMillis", 44))
        val points = JSONArray().put(JSONArray().put(0).put(0).put(0))
            .put(JSONArray().put(2).put(0).put(0))
        val oldRecord = JSONObject()
            .put("id", "legacy-measure").put("kind", "LENGTH").put("valueMeters", 2.0)
            .put("perimeterMeters", JSONObject.NULL).put("points", points)
            .put("timestampMillis", 45).put("projectId", "legacy-job")
        val rawMeasurement = JSONArray().put(oldRecord).toString()
        assertTrue(projectPrefs.edit().putString("projects", projects.toString())
            .putString("selected", "legacy-job").commit())
        assertTrue(recordPrefs.edit().putString("measurements_v2", rawMeasurement).commit())
        assertTrue(displayPrefs.edit().putString("unit", "CENTIMETRE").commit())

        val store = FieldDatabase.get(context)
        val snapshot = store.snapshot()
        assertEquals(2, snapshot.projects.size)
        assertEquals("legacy-job", snapshot.selectedProject)
        assertEquals(DisplayUnit.CENTIMETRE, snapshot.unit)
        assertEquals(1, snapshot.measurements.size)
        assertEquals("legacy-job", snapshot.measurements[0].projectId)
        assertEquals(rawMeasurement, recordPrefs.getString("measurements_v2", null))
    }

    @Test fun malformedLegacyDataFailsClosedAndPreservesOriginalFile() {
        val prefs = context.getSharedPreferences("bim_measure_3d", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putString("measurements_v2", "{bad-json").commit())
        try {
            FieldDatabase.get(context).snapshot()
            fail("Broken migration must fail rather than initialize an empty database")
        } catch (_: Exception) {
            assertEquals("{bad-json", prefs.getString("measurements_v2", null))
        }
    }

    @Test fun corruptRecordNeverCausesDestructiveRewriting() {
        val db = FieldDatabase.get(context)
        val first = db.addMeasurement("LENGTH", 1.0, null, demoPoints(), "general")
        db.writableDatabase.execSQL(
            "UPDATE measurements SET points_json='{broken' WHERE id=?", arrayOf(first.id))
        try {
            db.measurements()
            fail("Corrupted row must be reported, not quietly dropped")
        } catch (_: Exception) {}
        db.addMeasurement("LENGTH", 2.0, null, demoPoints(), "general")
        val count = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM measurements", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(2, count)
    }

    @Test fun restoreDatabaseIsAllOrNothingOnSqlException() {
        val db = FieldDatabase.get(context)
        db.addMeasurement("LENGTH", 1.0, null, demoPoints(), "general")
        val original = db.snapshot()
        val updated = FieldSnapshot(
            listOf(FieldProject("general","General",0), FieldProject("site-a","Site A",100)),
            "site-a",
            listOf(SavedMeasurement("new-1","LENGTH",2.0,null,demoPoints(),101,"site-a")),
            DisplayUnit.CENTIMETRE
        )
        db.writableDatabase.execSQL(
            "CREATE TEMP TRIGGER block_insert BEFORE INSERT ON measurements " +
                "BEGIN SELECT RAISE(ABORT, 'forced failure'); END"
        )
        try {
            db.restore(updated)
            fail("Simulated disk write failure should abort whole transaction")
        } catch (_: Exception) {
            assertEquals(original, db.snapshot())
        } finally {
            db.writableDatabase.execSQL("DROP TRIGGER IF EXISTS block_insert")
        }
    }

    @Test fun validRestoreUpdatesAllTablesTogether() {
        val db = FieldDatabase.get(context)
        db.addMeasurement("LENGTH", 1.0, null, demoPoints(), "general")
        val updated = FieldSnapshot(
            listOf(FieldProject("general","General",0), FieldProject("site-b","Site B",101)),
            "site-b",
            listOf(SavedMeasurement("restored","LENGTH",3.0,null,demoPoints(),102,"site-b")),
            DisplayUnit.MILLIMETRE
        )
        db.restore(updated)
        assertEquals(updated, db.snapshot())
        assertEquals(updated.measurements, MeasurementStore(context).load())
    }
    @Test fun sqliteVersionOneUpgradesWithoutDeletingMeasurements() {
        val oldDb = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        oldDb.execSQL("""CREATE TABLE projects (
            id TEXT PRIMARY KEY NOT NULL,
            name TEXT NOT NULL COLLATE NOCASE UNIQUE,
            created_at INTEGER NOT NULL
        )""")
        oldDb.execSQL("""CREATE TABLE measurements (
            id TEXT PRIMARY KEY NOT NULL,
            kind TEXT NOT NULL,
            value_m REAL NOT NULL,
            perimeter_m REAL,
            points_json TEXT NOT NULL,
            recorded_at INTEGER NOT NULL,
            project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE RESTRICT
        )""")
        oldDb.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        oldDb.execSQL("INSERT INTO projects(id,name,created_at) VALUES('general','General',0)")
        oldDb.execSQL("INSERT INTO settings(key,value) VALUES('selectedProject','general')")
        oldDb.execSQL("INSERT INTO settings(key,value) VALUES('unit','METRE')")
        oldDb.execSQL("""INSERT INTO measurements(
            id,kind,value_m,perimeter_m,points_json,recorded_at,project_id
        ) VALUES('older-line','LENGTH',2.0,NULL,'[[0,0,0],[2,0,0]]',123,'general')""")
        oldDb.version = 1
        oldDb.close()

        val newer = FieldDatabase.get(context)
        val saved = newer.measurements()
        assertEquals(1, saved.size)
        assertEquals("older-line", saved[0].id)
        assertEquals("ARCORE", saved[0].source)
        val manuallyEntered = newer.addMeasurement(
            "LENGTH", 1.0, null, demoPoints(), "general", source = "MANUAL"
        )
        assertEquals("MANUAL", manuallyEntered.source)
        assertEquals(2, newer.measurements().size)
        val restored = SnapshotCodec.decode(ProjectBackup.export(context))
        assertEquals(1, restored.measurements.count { it.source == "MANUAL" })
        assertEquals(1, restored.measurements.count { it.source == "ARCORE" })
    }

}
