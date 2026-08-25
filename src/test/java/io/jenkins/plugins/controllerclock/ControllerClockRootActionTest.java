package io.jenkins.plugins.controllerclock;

import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import hudson.model.User;
import hudson.model.UserProperty;
import jenkins.model.Jenkins;
import org.htmlunit.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.JenkinsRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ControllerClockRootActionTest {
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void syncEndpointReturnsControllerClockJsonAndCacheHeaders() throws Exception {
        WebClient client = jenkins.createWebClient();
        org.htmlunit.Page page = client.getPage(jenkins.getURL() + "controller-clock/sync");
        String body = page.getWebResponse().getContentAsString();
        assertTrue(body.contains("\"epochMillis\""));
        assertTrue(body.contains("\"controllerTimeZoneId\""));
        assertTrue(body.contains("\"controllerUtcOffsetMinutes\""));
        assertTrue(body.contains("\"displayTimeZoneId\""));
        assertEquals("application/json", page.getWebResponse().getContentType());
        assertEquals("no-store, no-cache, must-revalidate, max-age=0", page.getWebResponse().getResponseHeaderValue("Cache-Control"));
        assertEquals("no-cache", page.getWebResponse().getResponseHeaderValue("Pragma"));
    }

    @Test
    public void globalDecoratorInjectsClockAssetsAndBootstrap() throws Exception {
        WebClient client = jenkins.createWebClient();
        client.getOptions().setJavaScriptEnabled(false);
        client.getOptions().setCssEnabled(false);
        String body = client.getPage(jenkins.getURL()).getWebResponse().getContentAsString();
        assertTrue(body.contains("controller-clock.css"));
        assertTrue(body.contains("controller-clock.js"));
        assertTrue(body.contains("controller-clock-bootstrap"));
        assertTrue(body.contains("controller-clock-trigger"));
        assertTrue(body.contains("controller-clock-popup-time"));
        assertTrue(body.contains("data-sync-url"));

        assertTrue(client.getPage(jenkins.getURL() + "plugin/controller-clock/controller-clock.css")
                .getWebResponse()
                .getContentAsString()
                .contains("#controller-clock"));
        assertTrue(client.getPage(jenkins.getURL() + "plugin/controller-clock/controller-clock.js")
                .getWebResponse()
                .getContentAsString()
                .contains("controller-clock-trigger"));
    }

    @Test
    public void syncEndpointRejectsAnonymousWhenSecurityIsEnabled() throws Exception {
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
        jenkins.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().toAuthenticated());

        ControllerClockRootAction action = new ControllerClockRootAction();
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "test",
                ACL.ANONYMOUS_USERNAME,
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        try (ACLContext ignored = ACL.as2(anonymous)) {
            assertThrows(AccessDeniedException3.class, () -> action.doSync(null, null));
        }
    }

    @Test
    public void currentUserTimezoneOverrideIsCaptured() throws Exception {
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
        jenkins.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().to("alice"));
        User alice = User.getById("alice", true);
        UserProperty timeZoneProperty = (UserProperty) Class.forName("hudson.model.TimeZoneProperty")
                .getConstructor(String.class)
                .newInstance("Europe/London");
        alice.addProperty(timeZoneProperty);
        ControllerClockRootAction action = new ControllerClockRootAction();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "alice",
                "alice",
                AuthorityUtils.createAuthorityList("ROLE_AUTHENTICATED"));
        try (ACLContext ignored = ACL.as2(authentication)) {
            ControllerClockData data = action.getClockData();
            assertEquals("Europe/London", data.getDisplayTimeZoneId());
            assertTrue(data.isDisplayTimeZoneValid());
        }
    }
}
