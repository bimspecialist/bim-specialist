package com.bimspecialist.measure3d.core

import org.junit.Assert.*
import org.junit.Test

class DeviceCompatibilityAndManualTests {
    @Test fun arCapabilityIsMappedWithoutAssumingHardwareSupport() {
        assertEquals(ArCapability.READY, ArCapabilityPolicy.fromGoogleAvailability("SUPPORTED_INSTALLED"))
        assertEquals(ArCapability.SERVICE_REQUIRED, ArCapabilityPolicy.fromGoogleAvailability("SUPPORTED_APK_TOO_OLD"))
        assertEquals(ArCapability.SERVICE_REQUIRED, ArCapabilityPolicy.fromGoogleAvailability("SUPPORTED_NOT_INSTALLED"))
        assertEquals(ArCapability.UNSUPPORTED, ArCapabilityPolicy.fromGoogleAvailability("UNSUPPORTED_DEVICE_NOT_CAPABLE"))
        assertEquals(ArCapability.CHECKING, ArCapabilityPolicy.fromGoogleAvailability("UNKNOWN_CHECKING"))
        assertEquals(ArCapability.UNKNOWN, ArCapabilityPolicy.fromGoogleAvailability("UNKNOWN_ERROR"))
        assertFalse(ArCapabilityPolicy.mayStartCamera(ArCapability.UNSUPPORTED, true))
        assertFalse(ArCapabilityPolicy.mayStartCamera(ArCapability.READY, false))
        assertTrue(ArCapabilityPolicy.mayStartCamera(ArCapability.READY, true))
    }

    @Test fun decimalCommaAndArabicDigitsAreAccepted() {
        assertEquals(1.25, ManualMeasurementCalculator.parseLength("١٫٢٥", DisplayUnit.METRE), 0.0000001)
        assertEquals(2.5, ManualMeasurementCalculator.parseLength("۲,۵", DisplayUnit.METRE), 0.0000001)
        assertEquals(0.25, ManualMeasurementCalculator.parseLength("25", DisplayUnit.CENTIMETRE), 0.0000001)
        assertEquals(0.125, ManualMeasurementCalculator.parseLength("125", DisplayUnit.MILLIMETRE), 0.0000001)
    }

    @Test fun invalidAndAmbiguousDimensionsAreRejected() {
        val bad = listOf("", "0", "-1", "1e3", "NaN", "Infinity", "1,2,3", "1 000", "1/2", "0.0001", "1001")
        for (input in bad) {
            try {
                ManualMeasurementCalculator.parseLength(input, DisplayUnit.METRE)
                fail("Invalid dimension accepted: $input")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun manualRectangleProducesCorrectAreaPerimeterAndGeometry() {
        val result = ManualMeasurementCalculator.calculate(
            ManualMeasureMode.RECTANGULAR_AREA, "٢", "٣", DisplayUnit.METRE
        )
        assertEquals("AREA", result.kind)
        assertEquals(6.0, result.valueMetresOrSquareMetres, 1e-9)
        assertEquals(10.0, result.perimeterMetres!!, 1e-9)
        assertEquals(6.0, Geometry.planarArea(result.points), 1e-9)
    }

    @Test fun manualLengthIsRepresentedByTwoPoints() {
        val result = ManualMeasurementCalculator.calculate(
            ManualMeasureMode.LENGTH, "150", "", DisplayUnit.CENTIMETRE
        )
        assertEquals("LENGTH", result.kind)
        assertEquals(1.5, result.valueMetresOrSquareMetres, 1e-9)
        assertEquals(2, result.points.size)
        assertEquals(1.5, Geometry.length(result.points[0], result.points[1]), 1e-9)
        assertNull(result.perimeterMetres)
    }

    @Test fun backupRetainsManualProvenanceAndLegacyDefaultsToArcore() {
        val result = ManualMeasurementCalculator.calculate(
            ManualMeasureMode.RECTANGULAR_AREA, "2", "3", DisplayUnit.METRE
        )
        val manual = SavedMeasurement(
            "manual-1", result.kind, result.valueMetresOrSquareMetres,
            result.perimeterMetres, result.points, 100L, "general", "MANUAL"
        )
        val original = FieldSnapshot(listOf(FieldProject("general","General",0L)),
            "general", listOf(manual), DisplayUnit.METRE)
        val archive = SnapshotCodec.encode(original)
        assertEquals(original, SnapshotCodec.decode(archive))

        val json = org.json.JSONObject(archive.toString(Charsets.UTF_8))
        json.getJSONArray("measurements").getJSONObject(0).remove("source")
        val legacy = SnapshotCodec.decode(json.toString().toByteArray(Charsets.UTF_8))
        assertEquals("ARCORE", legacy.measurements.first().source)
    }

    @Test fun unknownRecordProvenanceFailsClosed() {
        val result = ManualMeasurementCalculator.calculate(
            ManualMeasureMode.LENGTH, "1", "", DisplayUnit.METRE
        )
        val snapshot = FieldSnapshot(
            listOf(FieldProject("general","General",0L)),
            "general", listOf(SavedMeasurement(
                "manual-2", "LENGTH", 1.0, null, result.points, 100L, "general", "UNKNOWN"
            )), DisplayUnit.METRE
        )
        try {
            SnapshotCodec.encode(snapshot)
            fail("Unrecognized measurement source must not be saved")
        } catch (_: IllegalArgumentException) {}
    }
}
