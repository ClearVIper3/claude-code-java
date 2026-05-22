package viper.com.claudecode.core;

import viper.com.claudecode.Config;
import viper.com.claudecode.compress.Compression;
import viper.com.claudecode.model.AnthropicResponse;
import viper.com.claudecode.model.ContentBlock;
import viper.com.claudecode.model.Message;

import java.util.*;

/**
 * s01: 主 Agent 循环。整合所有子机制：
 *   - s06: micro/auto compaction
 *   - s08: 后台通知排空
 *   - s10: lead inbox 排空
 *   - s02: 工具分发
 *   - s03: 待办提醒
 *   - s11: 错误恢复（try/catch 调用工具）
 */
public final class AgentLoop {

    private AgentLoop() {}

    public static void run(List<Message> messages) {
        int roundsWithoutTodo = 0;

        while (true) {
            // s06: 压缩管线
            Compression.microcompact(messages);
            if (Compression.estimateTokens(messages) > Config.TOKEN_THRESHOLD) {
                System.out.println("[auto-compact triggered]");
                List<Message> compacted = Compression.autoCompact(messages, null,
                        Context.CLIENT, Config.getModelId());
                messages.clear();
                messages.addAll(compacted);
            }

            // s08: 排空后台通知
            List<Map<String, Object>> notifs = Context.BG.drain();
            if (!notifs.isEmpty()) {
                StringBuilder txt = new StringBuilder();
                for (Map<String, Object> n : notifs) {
                    if (txt.length() > 0) txt.append('\n');
                    txt.append("[bg:").append(n.get("task_id")).append("] ")
                       .append(n.get("status")).append(": ").append(n.get("result"));
                }
                messages.add(Message.user("<background-results>\n" + txt + "\n</background-results>"));
                messages.add(Message.assistant("Noted background results."));
            }

            // s10: 排空 lead inbox
            List<Map<String, Object>> inbox = Context.BUS.readInbox("lead");
            if (!inbox.isEmpty()) {
                try {
                    String json = viper.com.claudecode.AnthropicClient.MAPPER
                            .writerWithDefaultPrettyPrinter().writeValueAsString(inbox);
                    messages.add(Message.user("<inbox>" + json + "</inbox>"));
                    messages.add(Message.assistant("Noted inbox messages."));
                } catch (Exception ignored) {}
            }

            // LLM 调用
            AnthropicResponse response;
            try {
                response = Context.CLIENT.createMessage(
                        Config.getModelId(), SystemPrompt.build(),
                        messages, ToolDispatch.TOOLS, 8000);
            } catch (Exception e) {
                // s11: 错误恢复 - 把错误注入下一轮上下文，让模型尝试重试或换工具
                System.out.println("[LLM error] " + e.getMessage());
                messages.add(Message.user("<error>LLM call failed: " + e.getMessage() +
                        ". Please retry or change approach.</error>"));
                continue;
            }
            messages.add(Message.assistant(response.content));

            if (!"tool_use".equals(response.stopReason)) return;

            // 工具执行
            List<Map<String, Object>> results = new ArrayList<>();
            boolean usedTodo = false;
            boolean manualCompress = false;
            String compactFocus = null;

            for (ContentBlock block : response.content) {
                if (!"tool_use".equals(block.type)) continue;

                if ("compress".equals(block.name)) {
                    manualCompress = true;
                    if (block.input != null && block.input.get("focus") != null) {
                        compactFocus = block.input.get("focus").toString();
                    }
                }

                var handler = ToolDispatch.HANDLERS.get(block.name);
                String output;
                try {
                    Map<String, Object> input = new LinkedHashMap<>(
                            block.input == null ? Map.of() : block.input);
                    input.put("tool_use_id", block.id);
                    output = handler != null ? handler.apply(input) : ("Unknown tool: " + block.name);
                } catch (Exception e) {
                    // s11: 错误恢复 - 工具异常不杀进程，作为字符串返回
                    output = "Error: " + e.getMessage();
                }
                System.out.println("> " + block.name + ": " +
                        (output.length() > 200 ? output.substring(0, 200) : output));

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "tool_result");
                result.put("tool_use_id", block.id);
                result.put("content", output);
                results.add(result);

                if ("TodoWrite".equals(block.name)) usedTodo = true;
            }

            // s03: nag 提醒（仅在使用 todo 工作流时）
            roundsWithoutTodo = usedTodo ? 0 : roundsWithoutTodo + 1;
            if (Context.TODO.hasOpenItems() && roundsWithoutTodo >= 3) {
                Map<String, Object> reminder = new LinkedHashMap<>();
                reminder.put("type", "text");
                reminder.put("text", "<reminder>Update your todos.</reminder>");
                results.add(0, reminder);
            }

            messages.add(Message.user(results));

            // s06: 手动压缩
            if (manualCompress) {
                System.out.println("[manual compact]");
                List<Message> compacted = Compression.autoCompact(messages, compactFocus,
                        Context.CLIENT, Config.getModelId());
                messages.clear();
                messages.addAll(compacted);
            }
        }
    }
}
