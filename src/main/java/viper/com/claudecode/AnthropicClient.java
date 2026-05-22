package viper.com.claudecode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import viper.com.claudecode.model.AnthropicResponse;
import viper.com.claudecode.model.ContentBlock;
import viper.com.claudecode.model.Message;
import viper.com.claudecode.model.ToolDefinition;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Anthropic Messages API 客户端。
 * 直接使用 Java 内置 HttpClient + Jackson，不依赖第三方 SDK。
 *
 * 兼容两种使用方式：
 *   - 设置 ANTHROPIC_BASE_URL 走自定义网关（这种情况下不发送 ANTHROPIC_AUTH_TOKEN）
 *   - 否则走默认 https://api.anthropic.com
 */
public class AnthropicClient {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final String authToken;

    public AnthropicClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        String b = Config.getBaseUrl();
        this.baseUrl = (b == null || b.isBlank()) ? "https://api.anthropic.com" : trimSlash(b);
        this.apiKey = Config.getApiKey();
        this.authToken = Config.getAuthToken();
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * 调用 messages.create。
     */
    public AnthropicResponse createMessage(String model,
                                           String system,
                                           List<Message> messages,
                                           List<ToolDefinition> tools,
                                           int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        if (system != null && !system.isBlank()) {
            body.put("system", system);
        }
        body.put("messages", messages.stream().map(AnthropicClient::serializeMessage).toList());
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools.stream().map(ToolDefinition::toApiMap).toList());
        }

        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/messages"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (apiKey != null && !apiKey.isBlank()) {
                rb.header("x-api-key", apiKey);
            }
            if (authToken != null && !authToken.isBlank()) {
                rb.header("Authorization", "Bearer " + authToken);
            }
            HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new RuntimeException("Anthropic API error " + resp.statusCode() + ": " + resp.body());
            }
            return parseResponse(resp.body());
        } catch (Exception e) {
            throw new RuntimeException("Anthropic call failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> serializeMessage(Message m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", m.role);
        Object c = m.content;
        if (c instanceof String s) {
            map.put("content", s);
        } else if (c instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof ContentBlock b) {
                    out.add(serializeBlock(b));
                } else if (item instanceof Map<?, ?> mp) {
                    out.add(mp);
                } else {
                    out.add(item);
                }
            }
            map.put("content", out);
        } else if (c instanceof ContentBlock b) {
            map.put("content", List.of(serializeBlock(b)));
        } else {
            map.put("content", c);
        }
        return map;
    }

    public static Map<String, Object> serializeBlock(ContentBlock b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", b.type);
        switch (b.type) {
            case "text" -> m.put("text", b.text == null ? "" : b.text);
            case "tool_use" -> {
                m.put("id", b.id);
                m.put("name", b.name);
                m.put("input", b.input == null ? Map.of() : b.input);
            }
            case "tool_result" -> {
                m.put("tool_use_id", b.toolUseId);
                m.put("content", b.content == null ? "" : b.content);
            }
            default -> {}
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    public static AnthropicResponse parseResponse(String body) throws Exception {
        JsonNode node = MAPPER.readTree(body);
        AnthropicResponse r = new AnthropicResponse();
        r.role = node.path("role").asText("assistant");
        r.model = node.path("model").asText(null);
        r.stopReason = node.path("stop_reason").asText(null);
        List<ContentBlock> blocks = new ArrayList<>();
        JsonNode arr = node.path("content");
        if (arr.isArray()) {
            for (JsonNode bn : arr) {
                ContentBlock b = new ContentBlock();
                b.type = bn.path("type").asText();
                switch (b.type) {
                    case "text" -> b.text = bn.path("text").asText("");
                    case "tool_use" -> {
                        b.id = bn.path("id").asText();
                        b.name = bn.path("name").asText();
                        JsonNode in = bn.path("input");
                        if (in.isObject()) {
                            b.input = MAPPER.convertValue(in, Map.class);
                        } else {
                            b.input = new LinkedHashMap<>();
                        }
                    }
                    default -> {}
                }
                blocks.add(b);
            }
        }
        r.content = blocks;
        return r;
    }
}
