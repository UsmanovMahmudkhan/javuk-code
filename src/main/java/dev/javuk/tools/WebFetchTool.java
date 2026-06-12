package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.util.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Fetches a URL over HTTP(S) and returns its content as text, stripping HTML
 * tags to plain text. Output is capped. Only http/https schemes are allowed.
 */
public final class WebFetchTool implements Tool {

    private static final int MAX_CHARS = 20_000;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

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
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return "Error: only http/https URLs are allowed";
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Javuk/1.0 (+https://github.com)")
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            return "Error: HTTP " + response.statusCode() + " fetching " + url;
        }

        String text = htmlToText(response.body());
        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS) + "\n… [content truncated]";
        }
        return text;
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
