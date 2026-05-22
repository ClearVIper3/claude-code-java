package viper.com.claudecode.managers;

import com.fasterxml.jackson.databind.ObjectMapper;
import viper.com.claudecode.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * s09: 团队消息总线。基于文件系统的 inbox：每个成员对应 .team/inbox/&lt;name&gt;.jsonl。
 */
public class MessageBus {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public MessageBus() {
        try {
            Files.createDirectories(Config.INBOX_DIR);
        } catch (IOException ignored) {}
    }

    public synchronized String send(String sender, String to, String content,
                                    String msgType, Map<String, Object> extra) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", msgType == null ? "message" : msgType);
        msg.put("from", sender);
        msg.put("content", content);
        msg.put("timestamp", System.currentTimeMillis() / 1000.0);
        if (extra != null) msg.putAll(extra);
        try {
            Path p = Config.INBOX_DIR.resolve(to + ".jsonl");
            Files.createDirectories(p.getParent());
            Files.writeString(p,
                    MAPPER.writeValueAsString(msg) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            return "Error sending: " + e.getMessage();
        }
        return "Sent " + msg.get("type") + " to " + to;
    }

    public String send(String sender, String to, String content) {
        return send(sender, to, content, "message", null);
    }

    @SuppressWarnings("unchecked")
    public synchronized List<Map<String, Object>> readInbox(String name) {
        Path path = Config.INBOX_DIR.resolve(name + ".jsonl");
        if (!Files.exists(path)) return new ArrayList<>();
        try {
            String text = Files.readString(path);
            List<Map<String, Object>> out = new ArrayList<>();
            for (String line : text.split("\\r?\\n")) {
                if (line.isBlank()) continue;
                out.add(MAPPER.readValue(line, Map.class));
            }
            Files.writeString(path, "");
            return out;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public synchronized String broadcast(String sender, String content, List<String> names) {
        int count = 0;
        for (String n : names) {
            if (!n.equals(sender)) {
                send(sender, n, content, "broadcast", null);
                count++;
            }
        }
        return "Broadcast to " + count + " teammates";
    }
}
