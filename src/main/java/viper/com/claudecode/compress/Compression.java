package viper.com.claudecode.compress;

import com.fasterxml.jackson.core.JsonProcessingException;
import viper.com.claudecode.AnthropicClient;
import viper.com.claudecode.Config;
import viper.com.claudecode.model.AnthropicResponse;
import viper.com.claudecode.model.ContentBlock;
import viper.com.claudecode.model.Message;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * s06: 压缩管线。
 *  - estimateTokens: 粗略 token 估算
 *  - microcompact: 把旧的 tool_result 替换为占位提示
 *  - autoCompact: 调 LLM 总结整段对话，仅保留摘要
 */
public final class Compression {

    private Compression() {}

    public static int estimateTokens(List<Message> messages) {
        try {
            String json = AnthropicClient.MAPPER.writeValueAsString(
                    messages.stream().map(AnthropicClient::serializeMessage).toList());
            return json.length() / 4;
        } catch (JsonProcessingException e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    public static void microcompact(List<Message> messages) {
        // 收集所有 tool_result（在 user 消息中以 List<Map> 形式存在）
        List<Map<String, Object>> toolResults = new ArrayList<>();
        for (Message msg : messages) {
            if ("user".equals(msg.role) && msg.content instanceof List<?> list) {
                for (Object part : list) {
                    if (part instanceof Map<?, ?> mp && "tool_result".equals(mp.get("type"))) {
                        toolResults.add((Map<String, Object>) mp);
                    }
                }
            }
        }
        if (toolResults.size() <= Config.KEEP_RECENT) return;

        // 建 toolUseId -> toolName 映射（来自 assistant 消息）
        Map<String, String> nameMap = new HashMap<>();
        for (Message msg : messages) {
            if (!"assistant".equals(msg.role)) continue;
            if (msg.content instanceof List<?> list) {
                for (Object block : list) {
                    if (block instanceof ContentBlock b && "tool_use".equals(b.type)) {
                        nameMap.put(b.id, b.name);
                    } else if (block instanceof Map<?, ?> mp && "tool_use".equals(mp.get("type"))) {
                        Object id = mp.get("id");
                        Object nm = mp.get("name");
                        if (id != null && nm != null) nameMap.put(id.toString(), nm.toString());
                    }
                }
            }
        }

        int cutoff = toolResults.size() - Config.KEEP_RECENT;
        for (int i = 0; i < cutoff; i++) {
            Map<String, Object> part = toolResults.get(i);
            Object content = part.get("content");
            if (!(content instanceof String s) || s.length() <= 100) continue;
            String toolId = String.valueOf(part.getOrDefault("tool_use_id", ""));
            String toolName = nameMap.getOrDefault(toolId, "unknown");
            if (Config.PRESERVE_RESULT_TOOLS.contains(toolName)) continue;
            part.put("content", "[Previous: used " + toolName + "]");
        }
    }

    public static List<Message> autoCompact(List<Message> messages, String focus,
                                            AnthropicClient client, String model) {
        try {
            Files.createDirectories(Config.TRANSCRIPT_DIR);
            Path transcript = Config.TRANSCRIPT_DIR.resolve(
                    "transcript_" + (System.currentTimeMillis() / 1000) + ".jsonl");
            try (BufferedWriter w = Files.newBufferedWriter(transcript)) {
                for (Message m : messages) {
                    w.write(AnthropicClient.MAPPER.writeValueAsString(AnthropicClient.serializeMessage(m)));
                    w.newLine();
                }
            }
        } catch (IOException ignored) {}

        String convText;
        try {
            convText = AnthropicClient.MAPPER.writeValueAsString(
                    messages.stream().map(AnthropicClient::serializeMessage).toList());
        } catch (JsonProcessingException e) {
            convText = "";
        }
        if (convText.length() > 80000) convText = convText.substring(0, 80000);

        StringBuilder prompt = new StringBuilder();
        prompt.append("Summarize this conversation for continuity. Structure your summary:\n")
              .append("1) Task overview: core request, success criteria, constraints\n")
              .append("2) Current state: completed work, files touched, artifacts created\n")
              .append("3) Key decisions and discoveries: constraints, errors, failed approaches\n")
              .append("4) Next steps: remaining actions, blockers, priority order\n")
              .append("5) Context to preserve: user preferences, domain details, commitments\n")
              .append("Be concise but preserve critical details.\n");
        if (focus != null && !focus.isBlank()) {
            prompt.append("\nPay special attention to: ").append(focus).append('\n');
        }
        prompt.append('\n').append(convText);

        AnthropicResponse resp = client.createMessage(
                model, null,
                List.of(Message.user(prompt.toString())),
                List.of(),
                4000);
        String summary = resp.content.isEmpty() ? "" : (resp.content.get(0).text == null ? "" : resp.content.get(0).text);

        String continuation =
                "This session is being continued from a previous conversation that ran out " +
                "of context. The summary below covers the earlier portion of the conversation.\n\n" +
                summary + "\n\n" +
                "Please continue the conversation from where we left it off without asking " +
                "the user any further questions.";

        List<Message> result = new ArrayList<>();
        result.add(Message.user(continuation));
        return result;
    }
}
