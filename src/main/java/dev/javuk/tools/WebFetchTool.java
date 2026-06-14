package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.util.Json;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches a URL over HTTP(S) and returns its content as text, stripping HTML
 * tags to plain text. Output is capped. Only http/https schemes are allowed.
 *
 * <p><b>SSRF guard:</b> by default the tool refuses to connect to private,
 * loopback, link-local (incl. the {@code 169.254.169.254} cloud-metadata
 * address), or otherwise non-public hosts, and re-validates every redirect hop
 * against the same rules. This matters because WebFetch is read-only and so is
 * auto-approved — without the guard, content the model reads could steer it into
 * fetching internal services. Set {@code allowPrivateHosts} to opt out.
 */
public final class WebFetchTool implements Tool {

    private static final int MAX_CHARS = 20_000;
    private static final int MAX_REDIRECTS = 5;

    private final boolean allowPrivateHosts;

    // Redirects are validated manually (see fetch), so the client must not follow them itself.
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** Secure default: private/internal hosts are refused. */
    public WebFetchTool() {
        this(false);
    }

    public WebFetchTool(boolean allowPrivateHosts) {
        this.allowPrivateHosts = allowPrivateHosts;
    }

    @Override
    public String name() {
        return "WebFetch";
    }

    @Override
    public String description() {
        return "Fetch a URL and return its content as plain text (HTML tags stripped). "
                + "Useful for reading documentation or pages referenced in the task.";
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of(
                "url", Map.of("type", "string", "description", "The http(s) URL to fetch")
        );
    }

    @Override
    public List<String> required() {
        return List.of("url");
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) throws Exception {
        String url = Json.required(args, "url");
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            return "Error: invalid URL: " + url;
        }

        URI current = uri;
        for (int hop = 0; ; hop++) {
            String reject = validateTarget(current);
            if (reject != null) {
                return reject;
            }

            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Javuk/1.0 (+https://github.com)")
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status >= 300 && status < 400) {
                if (hop >= MAX_REDIRECTS) {
                    return "Error: too many redirects fetching " + url;
                }
                Optional<String> location = response.headers().firstValue("Location");
                if (location.isEmpty()) {
                    return "Error: HTTP " + status + " redirect with no Location fetching " + url;
                }
                current = current.resolve(location.get()); // resolves relative redirects; re-validated above
                continue;
            }
            if (status >= 400) {
                return "Error: HTTP " + status + " fetching " + url;
            }

            String text = htmlToText(response.body());
            if (text.length() > MAX_CHARS) {
                text = text.substring(0, MAX_CHARS) + "\n… [content truncated]";
            }
            return text;
        }
    }

    /** @return an error message if the target must be refused, or null if it is allowed. */
    String validateTarget(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return "Error: only http/https URLs are allowed";
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "Error: invalid URL (missing host): " + uri;
        }
        if (!allowPrivateHosts && resolvesToBlockedAddress(host)) {
            return "Error: refused to fetch private/internal address: " + host
                    + " (set webFetch.allowPrivateHosts or pass --allow-private-fetch to override)";
        }
        return null;
    }

    /** True if any address the host resolves to is non-public (or it cannot be resolved). */
    private static boolean resolvesToBlockedAddress(String host) {
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()
                        || addr.isMulticastAddress() || isUniqueLocalV6(addr)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            return true; // unresolvable host — refuse rather than risk DNS-rebinding tricks
        }
    }

    /** IPv6 unique-local addresses (fc00::/7) — not covered by {@code isSiteLocalAddress}. */
    private static boolean isUniqueLocalV6(InetAddress addr) {
        return addr instanceof Inet6Address && (addr.getAddress()[0] & 0xfe) == 0xfc;
    }

    /** Crude but dependency-free HTML→text: drop script/style, strip tags, collapse whitespace. */
    static String htmlToText(String html) {
        String noScript = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ");
        String noTags = noScript.replaceAll("(?s)<[^>]+>", " ");
        String unescaped = noTags
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
        return unescaped.replaceAll("[ \t]+", " ").replaceAll("\\n\\s*\\n\\s*\\n+", "\n\n").strip();
    }
}
