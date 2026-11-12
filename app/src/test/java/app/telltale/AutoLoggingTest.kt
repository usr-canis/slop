package app.telltale

import app.telltale.data.AppSettings
import app.telltale.data.ConnectionState
import app.telltale.data.SessionState
import app.telltale.service.needsCarScreenReminder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLoggingTest {
    @Test fun recordingIsOnByDefault() = assertTrue(AppSettings().recording)

    @Test fun reminderOnlyWhenConnectedWithoutSession() {
        assertTrue(needsCarScreenReminder(ConnectionState.PROJECTION, SessionState.NONE))
        assertFalse(needsCarScreenReminder(ConnectionState.PROJECTION, SessionState.ACTIVE))
        assertFalse(needsCarScreenReminder(ConnectionState.NOT_CONNECTED, SessionState.NONE))
        assertFalse(needsCarScreenReminder(ConnectionState.UNKNOWN, SessionState.NONE))
    }
}
