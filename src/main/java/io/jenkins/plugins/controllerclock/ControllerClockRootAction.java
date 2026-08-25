package io.jenkins.plugins.controllerclock;

import hudson.Extension;
import hudson.model.RootAction;
import hudson.model.User;
import hudson.model.UserProperty;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;

@Extension
public class ControllerClockRootAction implements RootAction {
    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Controller Clock";
    }

    @Override
    public String getUrlName() {
        return "controller-clock";
    }

    public ControllerClockData getClockData() {
        Instant now = Instant.now();
        ZoneId zoneId = ZoneId.systemDefault();
        return ControllerClockData.from(now, zoneId, getCurrentUserDisplayTimeZoneName());
    }

    private String getCurrentUserDisplayTimeZoneName() {
        User currentUser = User.current();
        if (currentUser == null) {
            return null;
        }
        for (UserProperty property : currentUser.getAllProperties()) {
            if ("hudson.model.TimeZoneProperty".equals(property.getClass().getName())) {
                try {
                    Method method = property.getClass().getMethod("getTimeZoneName");
                    Object value = method.invoke(property);
                    return value instanceof String ? (String) value : null;
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public void doSync(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.READ);
        ControllerClockData data = getClockData();
        rsp.setContentType("application/json;charset=UTF-8");
        rsp.addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        rsp.addHeader("Pragma", "no-cache");
        rsp.getWriter().write(data.toJson());
    }
}
