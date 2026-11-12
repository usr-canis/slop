package app.telltale

import app.telltale.model.CarDataMapper
import app.telltale.model.FieldStatus
import app.telltale.model.UnitPref
import app.telltale.model.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDataMapperTest {
    @Test fun statusCodesMapToFieldStatus() {
        assertEquals(FieldStatus.AVAILABLE, CarDataMapper.status(1))
        assertEquals(FieldStatus.UNIMPLEMENTED, CarDataMapper.status(2))
        assertEquals(FieldStatus.UNAVAILABLE, CarDataMapper.status(3))
        assertEquals(FieldStatus.UNKNOWN, CarDataMapper.status(0))
        assertEquals(FieldStatus.UNKNOWN, CarDataMapper.status(99))
    }

    @Test fun successWithoutValueIsUnknown() {
        assertEquals(FieldStatus.UNKNOWN, CarDataMapper.statusOf(1, null))
        assertEquals(FieldStatus.AVAILABLE, CarDataMapper.statusOf(1, 42f))
        assertEquals(FieldStatus.UNAVAILABLE, CarDataMapper.statusOf(3, 42f))
    }

    @Test fun codeNames() {
        assertEquals("Electric", CarDataMapper.fuelType(10))
        assertEquals("Unleaded", CarDataMapper.fuelType(1))
        assertEquals("CCS2", CarDataMapper.evConnector(5))
        assertEquals("Not inserted", CarDataMapper.tollState(3))
        assertEquals("km/h", CarDataMapper.unit(102))
        assertEquals("mi", CarDataMapper.unit(4))
        assertEquals("Code 77", CarDataMapper.fuelType(77))
    }

    @Test fun conversions() {
        assertEquals(36.0, CarDataMapper.msToKmh(10.0), 1e-9)
        assertEquals(22.369, CarDataMapper.msToMph(10.0), 1e-3)
        assertEquals(1.0, CarDataMapper.kmToMi(1.609344), 1e-9)
        assertEquals("36", Units.speed(10.0, imperial = false))
        assertEquals("—", Units.speed(null, imperial = false))
    }

    @Test fun cardinalDirections() {
        assertEquals("N", CarDataMapper.cardinal(0.0))
        assertEquals("N", CarDataMapper.cardinal(359.0))
        assertEquals("E", CarDataMapper.cardinal(90.0))
        assertEquals("SW", CarDataMapper.cardinal(225.0))
        assertEquals("W", CarDataMapper.cardinal(-90.0))
    }

    @Test fun unitPreference() {
        assertTrue(Units.imperial(UnitPref.CAR, CarDataMapper.UNIT_MPH))
        assertFalse(Units.imperial(UnitPref.CAR, 102))
        assertFalse(Units.imperial(UnitPref.CAR, null))
        assertTrue(Units.imperial(UnitPref.IMPERIAL, 102))
        assertFalse(Units.imperial(UnitPref.METRIC, CarDataMapper.UNIT_MILE))
    }

    @Test fun exteriorDimensionsFormatting() {
        assertEquals("height 1500 mm, length 4500 mm", CarDataMapper.exteriorDimensions(listOf(1500, 4500)))
        assertEquals("—", CarDataMapper.exteriorDimensions(listOf(null)))
    }
}
