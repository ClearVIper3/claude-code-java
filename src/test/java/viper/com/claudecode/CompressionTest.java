package viper.com.claudecode;

import org.junit.jupiter.api.Test;
import viper.com.claudecode.compress.Compression;
import viper.com.claudecode.model.ContentBlock;
import viper.com.claudecode.model.Message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompressionTest {

    @Test
    void testEstimateTokens() {
        List<Message> ms = List.of(Message.user("a".repeat(4000)));
        int t = Compression.estimateTokens(ms);
        assertTrue(t > 500); // 4000 char ~ 1000 tokens
    }

    @Test
    void testMicrocompactReplacesOldToolResults() {
        List<Message> messages = new ArrayList<>();
        // 添加多个 tool_use / tool_result 对
        for (int i = 0; i < 10; i++) {
            ContentBlock tu = ContentBlock.toolUse("id_" + i, "bash", Map.of("command", "echo " + i));
            messages.add(Message.assistant(List.of(tu)));

            Map<String, Object> tr = new LinkedHashMap<>();
            tr.put("type", "tool_result");
            tr.put("tool_use_id", "id_" + i);
            tr.put("content", "x".repeat(500));
            messages.add(Message.user(List.of(tr)));
        }
        Compression.microcompact(messages);

        // 检查最早几条 tool_result 是否被替换成占位符
        int placeholders = 0;
        for (Message m : messages) {
            if (m.content instanceof List<?> list) {
                for (Object p : list) {
                    if (p instanceof Map<?, ?> mp && "tool_result".equals(mp.get("type"))) {
                        Object c = mp.get("content");
                        if (c instanceof String s && s.startsWith("[Previous: used")) placeholders++;
                    }
                }
            }
        }
        assertTrue(placeholders >= 1, "expected some tool_results to be compacted, got " + placeholders);
    }

    @Test
    void testMicrocompactKeepsRecent() {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ContentBlock tu = ContentBlock.toolUse("id_" + i, "bash", Map.of("command", "echo " + i));
            messages.add(Message.assistant(List.of(tu)));
            Map<String, Object> tr = new LinkedHashMap<>();
            tr.put("type", "tool_result");
            tr.put("tool_use_id", "id_" + i);
            tr.put("content", "y".repeat(500));
            messages.add(Message.user(List.of(tr)));
        }
        Compression.microcompact(messages);

        // 后 3 条不应被替换
        int kept = 0;
        for (int i = messages.size() - 1, c = 0; i >= 0 && c < 6; i--) {
            Message m = messages.get(i);
            if (m.content instanceof List<?> list) {
                for (Object p : list) {
                    if (p instanceof Map<?, ?> mp && "tool_result".equals(mp.get("type"))) {
                        Object content = mp.get("content");
                        if (content instanceof String s && !s.startsWith("[Previous")) kept++;
                        c++;
                    }
                }
            }
        }
        assertTrue(kept >= 3, "expected last 3 tool_results to remain, got " + kept);
    }
}
