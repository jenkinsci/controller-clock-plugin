package io.jenkins.plugins.controllerclock;

import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import hudson.model.User;
import hudson.model.TimeZoneProperty;
import jenkins.model.Jenkins;
import org.htmlunit.WebClient;
import org.htmlunit.MockWebConnection;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
public class ControllerClockRootActionTest {
    private JenkinsRule jenkins;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jenkins = rule;
    }

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
    public void globalHeaderInjectsClockChipAndBootstrap() throws Exception {
        WebClient client = jenkins.createWebClient();
        client.getOptions().setJavaScriptEnabled(false);
        client.getOptions().setCssEnabled(false);
        String body = client.getPage(jenkins.getURL()).getWebResponse().getContentAsString();
        int headEnd = body.indexOf("</head>");
        assertTrue(body.contains("controller-clock-global"));
        assertTrue(body.contains("controller-clock-global-value"));
        assertTrue(headEnd >= 0);
        assertTrue(body.indexOf("controller-clock-global") > headEnd);
        assertTrue(body.contains("aria-label=\"Controller time\""));
        assertTrue(body.contains("data-sync-url"));
        assertTrue(!body.contains("controller-clock-global-popup"));
        assertTrue(!body.contains("aria-haspopup=\"dialog\""));
        assertTrue(!body.contains("aria-expanded=\"false\""));
        assertTrue(!body.contains("aria-controls=\"controller-clock-global-popup\""));
        String button = body.substring(body.indexOf("controller-clock-global"), body.indexOf("controller-clock-global-value"));
        assertTrue(button.contains("<svg"));
        assertTrue(button.contains("aria-hidden=\"true\""));
        assertTrue(!body.contains("dd:custom"));
    }

    @Test
    public void pluginAssetsAreServedAndClockRendersInBrowser() throws Exception {
        WebClient client = jenkins.createWebClient();
        client.getOptions().setJavaScriptEnabled(true);
        org.htmlunit.html.HtmlPage page = client.getPage(jenkins.getURL());
        client.waitForBackgroundJavaScript(5000);
        assertTrue(page.asNormalizedText().contains("REST API"));
        assertTrue(page.getElementById("controller-clock") != null);
        assertEquals(1, page.querySelectorAll("#controller-clock").size());
    }

    @Test
    public void scriptDoesNothingWhenClockAnchorIsMissing() throws Exception {
        URL pageUrl = new URL("http://example.test/");
        URL scriptUrl = new URL("http://example.test/controller-clock.js");
        MockWebConnection connection = new MockWebConnection();
        connection.setResponse(pageUrl, """
                <!doctype html>
                <html>
                  <head>
                    <script src="/controller-clock.js"></script>
                  </head>
                  <body>
                    <p>plain page</p>
                  </body>
                </html>
                """, "text/html");
        connection.setResponse(scriptUrl, readResource("/io/jenkins/plugins/controllerclock/controller-clock.js"), "application/javascript");

        WebClient client = new WebClient();
        client.setWebConnection(connection);
        client.getOptions().setJavaScriptEnabled(true);
        client.getOptions().setCssEnabled(false);
        org.htmlunit.html.HtmlPage page = client.getPage(pageUrl);
        client.waitForBackgroundJavaScript(1000);
        assertTrue(page.asNormalizedText().contains("plain page"));
        assertEquals(2, connection.getRequestCount());
        assertTrue(page.getElementById("controller-clock") == null);
    }

    @Test
    public void dedicatedClockPageIsNotExposedAnymore() throws Exception {
        WebClient client = jenkins.createWebClient();
        client.getOptions().setJavaScriptEnabled(false);
        client.getOptions().setCssEnabled(false);
        client.getOptions().setThrowExceptionOnFailingStatusCode(false);
        org.htmlunit.Page page = client.getPage(jenkins.getURL() + "controller-clock/");
        assertEquals(404, page.getWebResponse().getStatusCode());
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
    @SuppressRestrictedWarnings(TimeZoneProperty.class)
    public void currentUserTimezoneOverrideIsCaptured() throws Exception {
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
        jenkins.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().to("alice"));
        User alice = User.getById("alice", true);
        alice.addProperty(new TimeZoneProperty("Asia/Kolkata"));
        ControllerClockRootAction action = new ControllerClockRootAction();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "alice",
                "alice",
                AuthorityUtils.createAuthorityList("ROLE_AUTHENTICATED"));
        try (ACLContext ignored = ACL.as2(authentication)) {
            ControllerClockData data = getClockData(action);
            assertEquals("Asia/Kolkata", data.getDisplayTimeZoneId());
            assertTrue(data.isDisplayTimeZoneValid());
            assertEquals(Integer.valueOf(330), data.getDisplayUtcOffsetMinutes());
        }
    }

    @Test
    public void clockDataHelperIsNotPubliclyExposed() throws Exception {
        assertTrue(Modifier.isPrivate(ControllerClockRootAction.class.getDeclaredMethod("getClockData").getModifiers()));

        WebClient client = jenkins.createWebClient();
        client.getOptions().setThrowExceptionOnFailingStatusCode(false);
        org.htmlunit.Page page = client.getPage(jenkins.getURL() + "controller-clock/clockData");
        assertEquals(404, page.getWebResponse().getStatusCode());
    }

    private static ControllerClockData getClockData(ControllerClockRootAction action) throws Exception {
        Method method = ControllerClockRootAction.class.getDeclaredMethod("getClockData");
        method.setAccessible(true);
        return (ControllerClockData) method.invoke(action);
    }

    private static String readResource(String path) throws Exception {
        try (InputStream in = ControllerClockRootActionTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
