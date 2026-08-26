package io.jenkins.plugins.controllerclock;

import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class ControllerClockData {
    private final long epochMillis;
    private final String controllerTimeZoneId;
    private final int controllerUtcOffsetMinutes;
    private final String displayTimeZoneId;
    private final boolean displayTimeZoneValid;
    private final Integer displayUtcOffsetMinutes;

    ControllerClockData(
            long epochMillis,
            String controllerTimeZoneId,
            int controllerUtcOffsetMinutes,
            String displayTimeZoneId,
            boolean displayTimeZoneValid,
            Integer displayUtcOffsetMinutes) {
        this.epochMillis = epochMillis;
        this.controllerTimeZoneId = controllerTimeZoneId;
        this.controllerUtcOffsetMinutes = controllerUtcOffsetMinutes;
        this.displayTimeZoneId = displayTimeZoneId;
        this.displayTimeZoneValid = displayTimeZoneValid;
        this.displayUtcOffsetMinutes = displayUtcOffsetMinutes;
    }

    static ControllerClockData from(Instant now, ZoneId controllerZoneId, String displayTimeZoneName) {
        ZoneOffset controllerOffset = controllerZoneId.getRules().getOffset(now);
        String normalizedDisplayZone = null;
        boolean displayTimeZoneValid = false;
        Integer displayUtcOffsetMinutes = null;
        if (displayTimeZoneName != null && !displayTimeZoneName.isBlank()) {
            normalizedDisplayZone = displayTimeZoneName;
            try {
                ZoneId displayZoneId = ZoneId.of(displayTimeZoneName);
                displayUtcOffsetMinutes = displayZoneId.getRules().getOffset(now).getTotalSeconds() / 60;
                displayTimeZoneValid = true;
            } catch (DateTimeException ignored) {
                displayTimeZoneValid = false;
            }
        }
        return new ControllerClockData(
                now.toEpochMilli(),
                controllerZoneId.getId(),
                controllerOffset.getTotalSeconds() / 60,
                normalizedDisplayZone,
                displayTimeZoneValid,
                displayUtcOffsetMinutes);
    }

    long getEpochMillis() {
        return epochMillis;
    }

    String getControllerTimeZoneId() {
        return controllerTimeZoneId;
    }

    int getControllerUtcOffsetMinutes() {
        return controllerUtcOffsetMinutes;
    }

    String getDisplayTimeZoneId() {
        return displayTimeZoneId;
    }

    boolean isDisplayTimeZoneValid() {
        return displayTimeZoneValid;
    }

    Integer getDisplayUtcOffsetMinutes() {
        return displayUtcOffsetMinutes;
    }

    String toJson() {
        JSONObject json = new JSONObject();
        json.put("epochMillis", epochMillis);
        json.put("controllerTimeZoneId", controllerTimeZoneId);
        json.put("controllerUtcOffsetMinutes", controllerUtcOffsetMinutes);
        json.put("displayTimeZoneId", orJsonNull(displayTimeZoneId));
        json.put("displayTimeZoneValid", displayTimeZoneValid);
        json.put("displayUtcOffsetMinutes", orJsonNull(displayUtcOffsetMinutes));
        return json.toString();
    }

    private static Object orJsonNull(Object value) {
        return value != null ? value : JSONNull.getInstance();
    }
}
