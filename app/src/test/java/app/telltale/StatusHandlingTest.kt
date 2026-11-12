package app.telltale

import app.telltale.data.CarDataRepository
import app.telltale.data.Downsampler
import app.telltale.model.CapabilityReport
import app.telltale.model.FieldReading
import app.telltale.model.FieldStatus
import app.telltale.model.Fields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusHandlingTest {
    @Test fun repositoryKeepsLatestReadingAndRate() {
        val repo = CarDataRepository()
        repo.put(FieldReading(Fields.SPEED_RAW, FieldStatus.AVAILABLE, "1", 1.0, timestampMillis = 1000))
        repo.put(FieldReading(Fields.SPEED_RAW, FieldStatus.AVAILABLE, "2", 2.0, timestampMillis = 1100))
        val r = repo.fields.value[Fields.SPEED_RAW]!!
        assertEquals(2.0, r.numeric!!, 0.0)
        assertEquals(10.0, r.ratePerSecond, 0.01)
    }

    @Test fun seriesOnlyStoresAvailableValuesAndIsBounded() {
        val repo = CarDataRepository()
        repeat(CarDataRepository.SERIES_LENGTH + 20) { i ->
            repo.put(FieldReading(Fields.ACCEL, FieldStatus.AVAILABLE, values = listOf(i.toFloat(), 0f, 0f), timestampMillis = i.toLong()))
        }
        repo.put(FieldReading(Fields.ACCEL, FieldStatus.UNAVAILABLE, values = listOf(9f), timestampMillis = 9999))
        assertEquals(CarDataRepository.SERIES_LENGTH, repo.series(Fields.ACCEL).size)
    }

    @Test fun putStatusMarksEveryKey() {
        val repo = CarDataRepository()
        repo.putStatus(listOf(Fields.FUEL, Fields.BATTERY), FieldStatus.NO_PERMISSION)
        assertEquals(FieldStatus.NO_PERMISSION, repo.fields.value[Fields.FUEL]!!.status)
        assertEquals(FieldStatus.NO_PERMISSION, repo.fields.value[Fields.BATTERY]!!.status)
    }

    @Test fun reportSummaryAndText() {
        val f = mapOf(
            Fields.MAKE to FieldReading(Fields.MAKE, FieldStatus.AVAILABLE, "Skoda"),
            Fields.MODEL_NAME to FieldReading(Fields.MODEL_NAME, FieldStatus.AVAILABLE, "Octavia"),
            Fields.YEAR to FieldReading(Fields.YEAR, FieldStatus.AVAILABLE, "2022"),
            Fields.SPEED_RAW to FieldReading(Fields.SPEED_RAW, FieldStatus.NO_PERMISSION),
        )
        assertEquals("2022 Skoda Octavia", CapabilityReport.carTitle(f))
        assertEquals(3, CapabilityReport.summary(f).first)
        val text = CapabilityReport.text(f, "1.0.0")
        assertTrue(text.contains("✗ Raw speed: No permission"))
        assertTrue(text.contains("… Odometer: Waiting for data"))
        assertEquals("Unknown car", CapabilityReport.carTitle(emptyMap()))
    }

    @Test fun downsamplerRespectsIntervalAndTextChanges() {
        val d = Downsampler(1000)
        fun n(t: Long) = FieldReading("k", FieldStatus.AVAILABLE, "x", 1.0, timestampMillis = t)
        assertTrue(d.accept(n(0)))
        assertFalse(d.accept(n(500)))
        assertTrue(d.accept(n(1000)))
        fun s(t: Long, txt: String) = FieldReading("u", FieldStatus.AVAILABLE, txt, timestampMillis = t)
        assertTrue(d.accept(s(0, "km")))
        assertFalse(d.accept(s(5000, "km")))
        assertTrue(d.accept(s(5001, "mi")))
    }

    @Test fun everyFieldKeyIsUnique() {
        assertEquals(Fields.all.size, Fields.all.map { it.key }.toSet().size)
    }
}
