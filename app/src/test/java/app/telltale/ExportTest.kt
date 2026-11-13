package app.telltale

import app.telltale.data.ExportFile
import app.telltale.data.ExportFormat
import app.telltale.data.db.SampleEntity
import app.telltale.data.db.SessionEntity
import app.telltale.update.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {
    private val session = SessionEntity(id = 7, startedAt = 1_700_000_000_000, endedAt = 1_700_000_600_000, make = "Kia", model = "EV6", year = 2023)
    private val samples = listOf(
        SampleEntity(1, 7, 1_700_000_001_000, "speed.raw", 12.5, "12.50 m/s", "AVAILABLE"),
        SampleEntity(2, 7, 1_700_000_002_000, "profile.fuelTypes", null, "Electric, \"Other\"", "AVAILABLE"),
    )

    @Test fun jsonRoundTrip() {
        val file = ExportFormat.build(listOf(session to samples), now = 1)
        val back = ExportFormat.fromJson(ExportFormat.toJson(file))
        assertEquals(file, back)
        val (s, sa) = ExportFormat.toEntities(back.sessions.single())
        assertEquals(0L, s.id) // ids are reassigned on import
        assertEquals("EV6", s.model)
        assertEquals(2, sa.size)
        assertEquals(12.5, sa[0].value!!, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsForeignJson() {
        ExportFormat.fromJson("""{"format":"something-else","exportedAt":1,"sessions":[]}""")
    }

    @Test fun csvHasHeaderAndEscapes() {
        val csv = ExportFormat.toCsv(ExportFormat.build(listOf(session to samples), now = 1))
        val lines = csv.trimEnd().lines()
        assertEquals(ExportFormat.CSV_HEADER, lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1].contains(",speed.raw,12.5,12.50 m/s,AVAILABLE"))
        assertTrue(lines[2].contains("\"Electric, \"\"Other\"\"\""))
        assertEquals("", ExportFormat.csv(null))
        assertEquals("plain", ExportFormat.csv("plain"))
    }

    @Test fun emptyExport() {
        val f = ExportFile(exportedAt = 0, sessions = emptyList())
        assertEquals(ExportFormat.CSV_HEADER + "\n", ExportFormat.toCsv(f))
    }

    @Test fun versionComparison() {
        assertTrue(Versions.isNewer("v1.0.1", "1.0.0"))
        assertTrue(Versions.isNewer("v2.0", "1.9.9"))
        assertFalse(Versions.isNewer("v1.0.0", "1.0.0"))
        assertFalse(Versions.isNewer("v0.9.0", "1.0.0"))
        assertTrue(Versions.isNewer("v0.1.0", "0.0.0-dev"))
    }

    @Test fun parsesGithubRelease() {
        val json = """{"tag_name":"v1.2.0","name":"Telltale 1.2.0","body":"Notes","html_url":"https://x",
            "assets":[{"name":"notes.txt","browser_download_url":"https://a"},{"name":"telltale-1.2.0.apk","browser_download_url":"https://b.apk"}]}"""
        val r = Versions.parseRelease(json)
        assertEquals("v1.2.0", r.tag)
        assertEquals("https://b.apk", r.apkUrl) // old single-APK release: any APK
        val multi = """{"tag_name":"v0.4.0","assets":[
            {"name":"telltale-iot-v0.4.0.apk","browser_download_url":"https://iot"},
            {"name":"telltale-nav-v0.4.0.apk","browser_download_url":"https://nav"},
            {"name":"telltale-poi-v0.4.0.apk","browser_download_url":"https://poi"}]}"""
        assertEquals("https://nav", Versions.parseRelease(multi, "nav").apkUrl)
        assertEquals("https://poi", Versions.parseRelease(multi, "poi").apkUrl)
        assertEquals("Notes", r.notes)
    }
}
