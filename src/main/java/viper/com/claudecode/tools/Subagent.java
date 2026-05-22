package viper.com.claudecode.tools;

import viper.com.claudecode.AnthropicClient;
import viper.com.claudecode.Config;
import viper.com.claudecode.model.AnthropicResponse;
import viper.com.claudecode.model.ContentBlock;
import viper.com.claudecode.model.Message;
import viper.com.claudecode.model.ToolDefinition;

import java.util.*;

/**
 * s04: 子代理。可在隔离的对话循环内执行有限工具。
 *  - "Explore" 类型只允许 bash + read_file
 *  - 其他类型允许 write_file 与 edit_file
 */
public final class Subagent {

    private Subagent() {}

    public static String run(String prompt, String agentType, AnthropicClient client) {
        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(new ToolDefinition("bash", "Run command.",
                Map.of("type", "object",
                       "properties", Map.of("command", Map.of("type", "string")),
                       "required", List.of("command"))));
        tools.add(new ToolDefinition("read_file", "Read file.",
                Map.of("type", "object",
                       "properties", Map.of("path", Map.of("type", "string")),
                       "required", List.of("path"))));
        if (!"Explore".equals(agentType)) {
            tools.add(new ToolDefinition("write_file", "Write file.",
                    Map.of("type", "object",
                           "properties", Map.of(
                                   "path", Map.of("type", "string"),
                                   "content", Map.of("type", "string")),
                           "required", List.of("path", "content"))));
            tools.add(new ToolDefinition("edit_file", "Edit file.",
                    Map.of("type", "object",
                           "properties", Map.of(
                                   "path", Map.of("type", "string"),
                                   "old_text", Map.of("type", "string"),
                                   "new_text", Map.of("type", "string")),
                           "required", List.of("path", "old_text", "new_text"))));
        }

        List<Message> msgs = new ArrayList<>();
        msgs.add(Message.user(prompt));
        AnthropicResponse resp = null;

        for (int i = 0; i < 30; i++) {
            resp = client.createMessage(Config.getModelId(), null, msgs, tools, 8000);
            msgs.add(Message.assistant(resp.content));
            if (!"tool_use".equals(resp.stopReason)) break;

            List<Map<String, Object>> results = new ArrayList<>();
            for (ContentBlock b : resp.content) {
                if (!"tool_use".equals(b.type)) continue;
                String output;
                try {
                    output = switch (b.name) {
                        case "bash" -> BaseTools.runBash(asString(b.input.get("command")), b.id);
                        case "read_file" -> BaseTools.runRead(asString(b.input.get("path")), b.id, null);
                        case "write_file" -> BaseTools.runWrite(asString(b.input.get("path")),
                                asString(b.input.get("content")));
                        case "edit_file" -> BaseTools.runEdit(asString(b.input.get("path")),
                                asString(b.input.get("old_text")),
                                asString(b.input.get("new_text")));
                        default -> "Unknown tool";
                    };
                } catch (Exception e) {
                    output = "Error: " + e.getMessage();
                }
                if (output.length() > 50000) output = output.substring(0, 50000);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "tool_result");
                result.put("tool_use_id", b.id);
                result.put("content", output);
                results.add(result);
            }
            msgs.add(Message.user(results));
        }

        if (resp != null) {
            StringBuilder sb = new StringBuilder();
            for (ContentBlock b : resp.content) {
                if ("text".equals(b.type) && b.text != null) sb.append(b.text);
            }
            return sb.length() == 0 ? "(no summary)" : sb.toString();
        }
        return "(subagent failed)";
    }

    private static String asString(Object o) {
        return o == null ? "" : o.toString();
    }
}
