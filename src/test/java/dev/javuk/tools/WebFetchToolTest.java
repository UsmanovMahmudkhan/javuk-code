package dev.javuk.tools;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSRF-guard tests for WebFetch. These exercise {@code validateTarget} with IP
 * literals so no DNS lookup or network access is needed.
 */
class WebFetchToolTest {

    private final WebFetchTool secure = new WebFetchTool();          // default: deny private
    private final WebFetchTool permissive = new WebFetchTool(true);  // opt-out

    @Test
    void refusesLoopback() {
        assertTrue(secure.validateTarget(URI.create("http://127.0.0.1/")).contains("refused"));
    }

    @Test
    void refusesCloudMetadataAddress() {
        String r = secure.validateTarget(URI.create("http://169.254.169.254/latest/meta-data/"));
        assertTrue(r != null && r.contains("refused"), () -> "expected refusal, got: " + r);
    }

    @Test
    void refusesPrivateRanges() {
        assertTrue(secure.validateTarget(URI.create("http://10.0.0.1/")).contains("refused"));
        assertTrue(secure.validateTarget(URI.create("http://192.168.1.10/")).contains("refused"));
        assertTrue(secure.validateTarget(URI.create("http://172.16.5.5/")).contains("refused"));
    }

    @Test
    void refusesNonHttpScheme() {
        assertTrue(secure.validateTarget(URI.create("ftp://example.com/x")).contains("http/https"));
    }

    @Test
    void allowsPublicAddress() {
        // 8.8.8.8 is a public literal — parsed without DNS, not private/loopback.
        assertNull(secure.validateTarget(URI.create("http://8.8.8.8/")));
    }

    @Test
    void optOutAllowsPrivateHosts() {
        assertNull(permissive.validateTarget(URI.create("http://127.0.0.1/")));
        assertNull(permissive.validateTarget(URI.create("http://169.254.169.254/")));
    }
}
