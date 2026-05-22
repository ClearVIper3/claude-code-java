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
 * 统一 LLM 客户端。支持两种协议（自动根据 LLM_PROVIDER 选择）：
 *   - anthropic（默认）：Anthropic Messages API，地址 /v1/messages
 *   - openai：OpenAI 兼容（DeepSeek 等），地址 /v1/chat/completions
 *
 * 类名沿用 AnthropicClient 以保持原代码兼容。
 */
public class AnthropicClient {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final String authToken;
    private final String provider; // "anthropic" | "openai"

    public AnthropicClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        String b = Config.getBaseUrl();
        String p = Config.getProvider();
        this.provider = (p == null || p.isBlank()) ? "anthropic" : p.toLowerCase();
        if (b == null || b.isBlank()) {
            this.baseUrl = "openai".equals(this.provider)
                    ? "https://api.openai.com" : "https://api.anthropic.com";
        } else {
            this.baseUrl = trimSlash(b);
        }
        this.apiKey = Config.getApiKey();
        this.authToken = Config.getAuthToken();
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public AnthropicResponse createMessage(String model,
                                           String system,
                                           List<Message> messages,
                                           List<ToolDefinition> tools,
                                           int maxTokens) {
        return "openai".equals(provider)
                ? createOpenAi(model, system, messages, tools, maxTokens)
                : createAnthropic(model, system, messages, tools, maxTokens);
    }

    // ============================== Anthropic 协议 ==============================

    private AnthropicResponse createAnthropic(String model, String system,
                                              List<Message> messages,
                                              List<ToolDefinition> tools, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        if (system != null && !system.isBlank()) body.put("system", system);
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
            if (apiKey != null && !apiKey.isBlank()) rb.header("x-api-key", apiKey);
            if (authToken != null && !authToken.isBlank()) rb.header("Authorization", "Bearer " + authToken);
            HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new RuntimeException("Anthropic API error " + resp.statusCode() + ": " + resp.body());
            }
            return parseResponse(resp.body());
        } catch (Exception e) {
            throw new RuntimeException("Anthropic call failed: " + e.getMessage(), e);
        }
    }

    // ============================== OpenAI 协议（DeepSeek） ==============================

    private AnthropicResponse createOpenAi(String model, String system,
                                           List<Message> messages,
                                           List<ToolDefinition> tools, int maxTokens) {
        List<Map<String, Object>> oaiMsgs = new ArrayList<>();
        if (system != null && !system.isBlank()) {
            oaiMsgs.add(Map.of("role", "system", "content", system));
        }
        for (Message m : messages) {
            oaiMsgs.addAll(toOpenAiMessages(m));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("messages", oaiMsgs);
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> oaiTools = new ArrayList<>();
            for (ToolDefinition t : tools) {
                oaiTools.add(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", t.name,
                                "description", t.description,
                                "parameters", t.inputSchema)));
            }
            body.put("tools", oaiTools);
        }
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            String key = (apiKey != null && !apiKey.isBlank()) ? apiKey : authToken;
            if (key != null && !key.isBlank()) rb.header("Authorization", "Bearer " + key);
            HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new RuntimeException("OpenAI API error " + resp.statusCode() + ": " + resp.body());
            }
            return parseOpenAiResponse(resp.body());
        } catch (Exception e) {
            throw new RuntimeException("OpenAI call failed: " + e.getMessage(), e);
        }
    }

    /** 把内部 Message 转成一条或多条 OpenAI 风格消息（包括 tool_use -> assistant.tool_calls / tool_result -> role=tool）。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toOpenAiMessages(Message m) {
        List<Map<String, Object>> out = new ArrayList<>();
        Object c = m.content;
        if (c instanceof String s) {
            out.add(Map.of("role", m.role, "content", s));
            return out;
        }
        if ("assistant".equals(m.role) && c instanceof List<?> blocks) {
            StringBuilder textBuf = new StringBuilder();
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            for (Object b : blocks) {
                if (b instanceof ContentBlock cb) {
                    if ("text".equals(cb.type) && cb.text != null) textBuf.append(cb.text);
                    else if ("tool_use".equals(cb.type)) {
                        try {
                            toolCalls.add(Map.of(
                                    "id", cb.id,
                                    "type", "function",
                                    "function", Map.of(
                                            "name", cb.name,
                                            "arguments", MAPPER.writeValueAsString(
                                                    cb.input == null ? Map.of() : cb.input))));
                        } catch (Exception ignored) {}
                    }
                }
            }
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", "assistant");
            msg.put("content", textBuf.length() == 0 ? null : textBuf.toString());
            if (!toolCalls.isEmpty()) msg.put("tool_calls", toolCalls);
            out.add(msg);
            return out;
        }
        if ("user".equals(m.role) && c instanceof List<?> parts) {
            // 在我们的代码里，user.content=List 时通常是 tool_result 列表
            StringBuilder textBuf = new StringBuilder();
            for (Object p : parts) {
                if (p instanceof Map<?, ?> mp && "tool_result".equals(mp.get("type"))) {
                    Map<String, Object> toolMsg = new LinkedHashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", String.valueOf(mp.get("tool_use_id")));
                    Object content = mp.get("content");
                    toolMsg.put("content", content == null ? "" : content.toString());
                    out.add(toolMsg);
                } else if (p instanceof Map<?, ?> mp && "text".equals(mp.get("type"))) {
                    textBuf.append(String.valueOf(mp.get("text")));
                } else if (p instanceof ContentBlock cb && "text".equals(cb.type)) {
                    if (cb.text != null) textBuf.append(cb.text);
                }
            }
            if (textBuf.length() > 0) {
                out.add(Map.of("role", "user", "content", textBuf.toString()));
            }
            if (out.isEmpty()) {
                // fallback：原样 JSON 化
                try {
                    out.add(Map.of("role", "user", "content", MAPPER.writeValueAsString(parts)));
                } catch (Exception ignored) {
                    out.add(Map.of("role", "user", "content", String.valueOf(parts)));
                }
            }
            return out;
        }
        out.add(Map.of("role", m.role, "content", c == null ? "" : c.toString()));
        return out;
    }

    private static AnthropicResponse parseOpenAiResponse(String body) throws Exception {
        JsonNode root = MAPPER.readTree(body);
        AnthropicResponse r = new AnthropicResponse();
        r.role = "assistant";
        r.model = root.path("model").asText(null);
        JsonNode msg = root.path("choices").path(0).path("message");
        String finishReason = root.path("choices").path(0).path("finish_reason").asText("stop");
        List<ContentBlock> blocks = new ArrayList<>();
        String text = msg.path("content").asText("");
        if (text != null && !text.isEmpty()) {
            blocks.add(ContentBlock.text(text));
        }
        JsonNode tcs = msg.path("tool_calls");
        boolean hasTool = false;
        if (tcs.isArray() && !tcs.isEmpty()) {
            hasTool = true;
            for (JsonNode tc : tcs) {
                ContentBlock b = new ContentBlock();
                b.type = "tool_use";
                b.id = tc.path("id").asText();
                b.name = tc.path("function").path("name").asText();
                String args = tc.path("function").path("arguments").asText("{}");
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> input = MAPPER.readValue(args, Map.class);
                    b.input = input;
                } catch (Exception e) {
                    b.input = new LinkedHashMap<>();
                }
                blocks.add(b);
            }
        }
        r.content = blocks;
        r.stopReason = (hasTool || "tool_calls".equals(finishReason)) ? "tool_use" : "end_turn";
        return r;
    }

    // ============================== 通用序列化（Anthropic 协议） ==============================

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
