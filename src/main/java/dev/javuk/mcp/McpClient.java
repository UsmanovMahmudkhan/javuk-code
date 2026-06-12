package dev.javuk.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.javuk.util.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A minimal synchronous MCP (Model Context Protocol) client speaking JSON-RPC
 * 2.0 over newline-delimited stdio. It performs the {@code initialize} handshake,
 * lists tools, and calls them. Built on generic streams so it can be unit-tested
 * against an in-memory fake server; {@link #spawn} wires it to a real subprocess.
 */
public final class McpClient implements AutoCloseable {

    /** A tool advertised by the server. */
    public record ToolDef(String name, String description, JsonNode inputSchema) {
    }

    private final BufferedReader in;
    private final Writer out;
    private final Process process; // null when driven by in-memory streams (tests)
    private int nextId = 1;

    public McpClient(InputStream in, OutputStream out) {
        this(in, out, null);
    }

    private McpClient(InputStream in, OutputStream out, Process process) {
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8);
        this.process = process;
    }

    /** Launches the configured server as a subprocess and connects over its stdio. */
    public static McpClient spawn(McpServerConfig config) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(config.command());
        command.addAll(config.args());
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(config.env());
        pb.redirectError(ProcessBuilder.Redirect.DISCARD); // keep stderr off our stdout
        Process p = pb.start();
        return new McpClient(p.getInputStream(), p.getOutputStream(), p);
    }

    /** Performs the initialize handshake and returns the server's tool list. */
    public List<ToolDef> initializeAndListTools() throws IOException {
        ObjectNode initParams = Json.MAPPER.createObjectNode();
        initParams.put("protocolVersion", "2024-11-05");
        initParams.set("capabilities", Json.MAPPER.createObjectNode());
        ObjectNode clientInfo = Json.MAPPER.createObjectNode();
        clientInfo.put("name", "javuk");
        clientInfo.put("version", "1.0");
        initParams.set("clientInfo", clientInfo);

        request("initialize", initParams);
        notification("notifications/initialized", Json.MAPPER.createObjectNode());

        JsonNode result = request("tools/list", Json.MAPPER.createObjectNode());
        List<ToolDef> tools = new ArrayList<>();
        JsonNode list = result.path("tools");
        if (list.isArray()) {
            for (JsonNode t : list) {
                tools.add(new ToolDef(
                        t.path("name").asText(),
                        t.path("description").asText(""),
                        t.get("inputSchema")));
            }
        }
        return tools;
    }

    /** Calls a tool and returns its textual content joined together. */
    public String callTool(String name, JsonNode arguments) throws IOException {
        ObjectNode params = Json.MAPPER.createObjectNode();
        params.put("name", name);
        params.set("arguments", arguments == null ? Json.MAPPER.createObjectNode() : arguments);

        JsonNode result = request("tools/call", params);
        StringBuilder sb = new StringBuilder();
        JsonNode content = result.path("content");
        if (content.isArray()) {
            for (JsonNode part : content) {
                if ("text".equals(part.path("type").asText())) {
                    sb.append(part.path("text").asText());
                }
            }
        }
        if (result.path("isError").asBoolean(false)) {
            return "Error from MCP tool: " + sb;
        }
        return sb.isEmpty() ? "(no content)" : sb.toString();
    }

    private JsonNode request(String method, JsonNode params) throws IOException {
        int id = nextId++;
        ObjectNode req = Json.MAPPER.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", method);
        req.set("params", params);
        writeLine(req);
        return awaitResponse(id);
    }

    private void notification(String method, JsonNode params) throws IOException {
        ObjectNode note = Json.MAPPER.createObjectNode();
        note.put("jsonrpc", "2.0");
        note.put("method", method);
        note.set("params", params);
        writeLine(note);
    }

    /** Reads lines until a JSON-RPC response with the expected id arrives. */
    private JsonNode awaitResponse(int expectedId) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode msg;
            try {
                msg = Json.MAPPER.readTree(line);
            } catch (Exception e) {
                continue; // ignore non-JSON noise
            }
            if (msg.has("id") && msg.get("id").asInt() == expectedId) {
                if (msg.has("error")) {
                    throw new IOException("MCP error: " + msg.get("error").toString());
                }
                return msg.path("result");
            }
            // otherwise a notification or unrelated message — skip
        }
        throw new IOException("MCP server closed the connection before responding");
    }

    private synchronized void writeLine(JsonNode node) throws IOException {
        // JsonNode.toString() is always compact — required for newline-delimited JSON-RPC.
        out.write(node.toString());
        out.write("\n");
        out.flush();
    }

    @Override
    public void close() {
        try {
            in.close();
        } catch (IOException ignored) {
        }
        try {
            out.close();
        } catch (IOException ignored) {
        }
        if (process != null) {
            process.destroy();
        }
    }
}
