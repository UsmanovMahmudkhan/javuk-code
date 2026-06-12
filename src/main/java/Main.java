import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2 || !"-p".equals(args[0])) {
            System.err.println("Usage: program -p <prompt>");
            System.exit(1);
        }

        String prompt = args[1];

        String apiKey = System.getenv("OPENROUTER_API_KEY");
        String baseUrl = System.getenv("OPENROUTER_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://openrouter.ai/api/v1";
        }

        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("OPENROUTER_API_KEY is not set");
        }

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        FunctionParameters readParameters = FunctionParameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                        "file_path", Map.of(
                                "type", "string",
                                "description", "The path to the file to read"
                        )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of("file_path")))
                .build();

        ChatCompletionTool readTool = ChatCompletionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("Read")
                        .description("Read and return the contents of a file")
                        .parameters(readParameters)
                        .build())
                .build();

        FunctionParameters writeParameters = FunctionParameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                        "file_path", Map.of(
                                "type", "string",
                                "description", "The path of the file to write to"
                        ),
                        "content", Map.of(
                                "type", "string",
                                "description", "The content to write to the file"
                        )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of("file_path", "content")))
                .build();

        ChatCompletionTool writeTool = ChatCompletionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("Write")
                        .description("Write content to a file")
                        .parameters(writeParameters)
                        .build())
                .build();

        FunctionParameters bashParameters = FunctionParameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                        "command", Map.of(
                                "type", "string",
                                "description", "The command to execute"
                        )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of("command")))
                .build();

        ChatCompletionTool bashTool = ChatCompletionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("Bash")
                        .description("Execute a shell command")
                        .parameters(bashParameters)
                        .build())
                .build();

        // You can use print statements as follows for debugging, they'll be visible when running tests.
        System.err.println("Logs from your program will appear here!");

        List<ChatCompletionMessageParam> messages = new ArrayList<>();
        messages.add(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder().content(prompt).build()));

        while (true) {
            ChatCompletion response = client.chat().completions().create(
                    ChatCompletionCreateParams.builder()
                            .model("anthropic/claude-haiku-4.5")
                            .messages(messages)
                            .addTool(readTool)
                            .addTool(writeTool)
                            .addTool(bashTool)
                            .build()
            );

            if (response.choices().isEmpty()) {
                throw new RuntimeException("no choices in response");
            }

            ChatCompletionMessage message = response.choices().get(0).message();
            messages.add(ChatCompletionMessageParam.ofAssistant(message.toParam()));

            List<ChatCompletionMessageToolCall> toolCalls = message.toolCalls().orElse(List.of());

            if (toolCalls.isEmpty()) {
                System.out.print(message.content().orElse(""));
                return;
            }

            for (ChatCompletionMessageToolCall toolCall : toolCalls) {
                String name = toolCall.function().name();
                String arguments = toolCall.function().arguments();

                String result = "";
                if ("Read".equals(name)) {
                    result = executeRead(arguments);
                } else if ("Write".equals(name)) {
                    result = executeWrite(arguments);
                } else if ("Bash".equals(name)) {
                    result = executeBash(arguments);
                }

                messages.add(ChatCompletionMessageParam.ofTool(
                        ChatCompletionToolMessageParam.builder()
                                .toolCallId(toolCall.id())
                                .content(result)
                                .build()));
            }
        }
    }

    private static String executeRead(String arguments) {
        try {
            JsonNode node = new ObjectMapper().readTree(arguments);
            String filePath = node.get("file_path").asText();
            return Files.readString(Path.of(filePath));
        } catch (Exception e) {
            throw new RuntimeException("failed to execute Read tool", e);
        }
    }

    private static String executeWrite(String arguments) {
        try {
            JsonNode node = new ObjectMapper().readTree(arguments);
            String filePath = node.get("file_path").asText();
            String content = node.get("content").asText();
            Path path = Path.of(filePath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, content);
            return "Successfully wrote to " + filePath;
        } catch (Exception e) {
            throw new RuntimeException("failed to execute Write tool", e);
        }
    }

    private static String executeBash(String arguments) {
        try {
            JsonNode node = new ObjectMapper().readTree(arguments);
            String command = node.get("command").asText();

            Process process = new ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            return output;
        } catch (Exception e) {
            return "failed to execute Bash tool: " + e.getMessage();
        }
    }
}
