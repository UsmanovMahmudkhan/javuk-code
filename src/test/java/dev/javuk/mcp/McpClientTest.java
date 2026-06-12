package dev.javuk.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.util.Json;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link McpClient} against an in-memory fake MCP server, exercising the
 * JSON-RPC framing, the initialize handshake, tools/list and tools/call — no
 * subprocess required.
 */
class McpClientTest {

    @Test
    void handshakeListAndCall() throws Exception {
        // client → server
        PipedOutputStream clientWrites = new PipedOutputStream();
        PipedInputStream serverReads = new PipedInputStream(clientWrites);
        // server → client
        PipedOutputStream serverWrites = new PipedOutputStream();
        PipedInputStream clientReads = new PipedInputStream(serverWrites);

        Thread server = new Thread(() -> fakeServer(serverReads, serverWrites));
        server.setDaemon(true);
        server.start();

        try (McpClient client = new McpClient(clientReads, clientWrites)) {
            List<McpClient.ToolDef> tools = client.initializeAndListTools();
            assertEquals(1, tools.size());
            assertEquals("echo", tools.get(0).name());
            assertEquals("string", tools.get(0).inputSchema().path("properties").path("text").path("type").asText());

            String result = client.callTool("echo", Json.parse("{\"text\":\"hi\"}"));
            assertEquals("echoed: hi", result);
        }
    }

    /** Minimal MCP server: responds to initialize, tools/list, tools/call. */
    private static void fakeServer(PipedInputStream in, PipedOutputStream out) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
             Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode req = Json.MAPPER.readTree(line);
                if (!req.has("id")) {
                    continue; // notification (e.g. notifications/initialized)
                }
                int id = req.get("id").asInt();
                String method = req.path("method").asText();
                String response = switch (method) {
                    case "initialize" -> "{\"jsonrpc\":\"2.0\",\"id\":" + id
                            + ",\"result\":{\"protocolVersion\":\"2024-11-05\"}}";
                    case "tools/list" -> "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"tools\":["
                            + "{\"name\":\"echo\",\"description\":\"Echo text\",\"inputSchema\":"
                            + "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},"
                            + "\"required\":[\"text\"]}}]}}";
                    case "tools/call" -> {
                        String text = req.path("params").path("arguments").path("text").asText();
                        yield "{\"jsonrpc\":\"2.0\",\"id\":" + id
                                + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"echoed: " + text + "\"}]}}";
                    }
                    default -> "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{}}";
                };
                writer.write(response);
                writer.write("\n");
                writer.flush();
            }
        } catch (Exception ignored) {
            // stream closed by client at end of test
        }
    }

    @Test
    void mcpToolExposesNamespacedNameAndSchema() {
        JsonNode schema = Json.parse("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}},\"required\":[\"q\"]}");
        McpClient.ToolDef def = new McpClient.ToolDef("search", "Search the web", schema);
        McpTool tool = new McpTool(null, "web", def);
        assertEquals("web__search", tool.name());
        assertTrue(tool.properties().containsKey("q"));
        assertEquals(List.of("q"), tool.required());
    }
}
