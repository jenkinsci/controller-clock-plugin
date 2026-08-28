package io.jenkins.plugins.controllerclock;

import hudson.Extension;
import hudson.model.RootAction;
import hudson.model.User;
import hudson.model.TimeZoneProperty;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.GET;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;

@Extension(ordinal = 1000)
public class ControllerClockRootAction implements RootAction {
    @Override
    public String getIconFileName() {
        return "symbol-time-outline plugin-ionicons-api";
    }

    public boolean isPrimaryAction() {
        return true;
    }

    @Override
    public String getDisplayName() {
        return "Controller Clock";
    }

    @Override
    public String getUrlName() {
        return "controller-clock";
    }

    private ControllerClockData getClockData() {
        Instant now = Instant.now();
        ZoneId zoneId = ZoneId.systemDefault();
        return ControllerClockData.from(now, zoneId, getCurrentUserDisplayTimeZoneName());
    }

    @SuppressRestrictedWarnings(TimeZoneProperty.class)
    private String getCurrentUserDisplayTimeZoneName() {
        User currentUser = User.current();
        if (currentUser == null) {
            return null;
        }
        TimeZoneProperty timeZoneProperty = currentUser.getProperty(TimeZoneProperty.class);
        return timeZoneProperty != null ? timeZoneProperty.getTimeZoneName() : null;
    }

    @GET
    public void doSync(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.READ);
        ControllerClockData data = getClockData();
        rsp.setContentType("application/json;charset=UTF-8");
        rsp.addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        rsp.addHeader("Pragma", "no-cache");
        rsp.getWriter().write(data.toJson());
    }
}
