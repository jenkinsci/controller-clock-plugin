package io.jenkins.plugins.controllerclock;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ControllerClockDataTest {
    @Test
    public void serializesControllerAndDisplayTimeZones() {
        ControllerClockData data = ControllerClockData.from(
                Instant.parse("2026-08-24T12:00:00Z"),
                ZoneId.of("Asia/Kolkata"),
                "America/New_York");
        String json = data.toJson();
        assertTrue(json.contains("\"epochMillis\":"));
        assertTrue(json.contains("\"controllerTimeZoneId\":\"Asia/Kolkata\""));
        assertTrue(json.contains("\"controllerUtcOffsetMinutes\":330"));
        assertTrue(json.contains("\"displayTimeZoneId\":\"America/New_York\""));
        assertTrue(json.contains("\"displayTimeZoneValid\":true"));
        assertTrue(json.contains("\"displayUtcOffsetMinutes\":-240"));
    }

    @Test
    public void handlesInvalidDisplayTimeZoneStrings() {
        ControllerClockData data = ControllerClockData.from(
                Instant.parse("2026-08-24T12:00:00Z"),
                ZoneId.of("Asia/Kolkata"),
                "Not/AZone");
        String json = data.toJson();
        assertTrue(json.contains("\"displayTimeZoneId\":\"Not/AZone\""));
        assertTrue(json.contains("\"displayTimeZoneValid\":false"));
        assertTrue(json.contains("\"displayUtcOffsetMinutes\":null"));
    }

    @Test
    public void usesTimezoneDatabaseForDstTransitions() {
        ControllerClockData before = ControllerClockData.from(
                Instant.parse("2026-03-08T06:59:59Z"),
                ZoneId.of("America/New_York"),
                null);
        ControllerClockData after = ControllerClockData.from(
                Instant.parse("2026-03-08T07:00:01Z"),
                ZoneId.of("America/New_York"),
                null);
        assertEquals(-300, before.getControllerUtcOffsetMinutes());
        assertEquals(-240, after.getControllerUtcOffsetMinutes());
    }
}
