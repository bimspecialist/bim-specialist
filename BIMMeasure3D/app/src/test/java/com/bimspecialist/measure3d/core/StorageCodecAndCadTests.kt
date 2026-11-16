package com.bimspecialist.measure3d.core

import org.junit.Assert.*
import org.json.JSONObject
import org.junit.Test

class StorageCodecAndCadTests {
    private val rectangle = listOf(
        Vec3(0.0,0.0,0.0), Vec3(2.0,0.0,0.0),
        Vec3(2.0,1.0,0.0), Vec3(0.0,1.0,0.0)
    )
    private val example = FieldSnapshot(
        listOf(FieldProject("general", "General", 0),
            FieldProject("job-101", "Factory A", 100)),
        "job-101",
        listOf(SavedMeasurement("measurement-1", "AREA", 2.0, 6.0, rectangle, 1234, "job-101")),
        DisplayUnit.MILLIMETRE
    )

    @Test fun backupsRoundTripWithoutLosingProjectReferences() {
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(example))
        assertEquals(example, decoded)
    }

    @Test fun damagedBackupRejectedBeforeAnyDatabaseMutation() {
        val json = JSONObject(SnapshotCodec.encode(example).toString(Charsets.UTF_8))
        json.getJSONArray("measurements").getJSONObject(0).put("projectId", "missing")
        val damaged = json.toString()
        try {
            SnapshotCodec.decode(damaged.toByteArray())
            fail("Orphan project must be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun truncatedJsonRejected() {
        val bytes = SnapshotCodec.encode(example)
        try {
            SnapshotCodec.decode(bytes.copyOf(bytes.size / 2))
            fail("Truncated file must not be accepted")
        } catch (_: Exception) {}
    }

    @Test fun identicalIdsRejected() {
        val duplicate = example.copy(measurements = example.measurements + example.measurements.first())
        try {
            SnapshotCodec.decode(SnapshotCodec.encode(duplicate))
            fail("Duplicate rows must not be accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun extraLegacyProjectIdDefaultsToGeneral() {
        val json = JSONObject(SnapshotCodec.encode(example).toString(Charsets.UTF_8))
        json.getJSONArray("measurements").getJSONObject(0).remove("projectId")
        val raw = json.toString()
        // For backwards compatibility: older backups did not always contain a projectId field.
        val parsed = SnapshotCodec.decode(raw.toByteArray())
        assertEquals("general", parsed.measurements.first().projectId)
    }

    @Test fun cadTransformPreservesDistancesAndRoundTrips() {
        val ar = Vec3(1.5, 2.5, -3.5)
        val cad = CadAxes.fromArCore(ar, DxfCoordinateSystem.CAD_Z_UP)
        assertEquals(Vec3(1.5, 3.5, 2.5), cad)
        assertEquals(ar, CadAxes.toArCore(cad, DxfCoordinateSystem.CAD_Z_UP))
        assertEquals(ar.norm(), cad.norm(), 1e-9)
    }

    @Test fun dxfCadModeHasZUpAndPreservesRawMode() {
        val pts = listOf(Vec3(1.0, 2.0, 3.0), Vec3(2.0, 2.0, 3.0))
        val cad = Exports.dxf(pts, false, DxfCoordinateSystem.CAD_Z_UP).toString(Charsets.US_ASCII)
        assertTrue(cad.contains("10\n1000.000000\n20\n-3000.000000\n30\n2000.000000"))
        val raw = Exports.dxf(pts, false).toString(Charsets.US_ASCII)
        assertTrue(raw.contains("10\n1000.000000\n20\n2000.000000\n30\n3000.000000"))
    }

    @Test fun stabilityRequiresRepeatedConsistentFrames() {
        val s = ArStabilityMonitor(requiredFrames=3, toleranceMetres=0.01)
        val p = listOf(Vec3(1.0, 1.0, 1.0), Vec3(2.0, 1.0, 1.0))
        assertFalse(s.update(p, true))
        assertFalse(s.update(p, true))
        assertTrue(s.update(p, true))
        assertFalse(s.update(listOf(Vec3(1.1,1.0,1.0), p.last()), true))
        assertFalse(s.update(p, false))
        assertFalse(s.update(p, true))
    }

    @Test fun stabilityResetsWhenAnchorCountChanges() {
        val s = ArStabilityMonitor(requiredFrames=2)
        val one = listOf(Vec3(0.0,0.0,0.0))
        assertFalse(s.update(one,true))
        assertTrue(s.update(one,true))
        assertFalse(s.update(one + Vec3(1.0,0.0,0.0),true))
    }
}
