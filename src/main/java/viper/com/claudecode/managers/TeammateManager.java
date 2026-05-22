package viper.com.claudecode.managers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import viper.com.claudecode.AnthropicClient;
import viper.com.claudecode.Config;
import viper.com.claudecode.model.AnthropicResponse;
import viper.com.claudecode.model.ContentBlock;
import viper.com.claudecode.model.Message;
import viper.com.claudecode.model.ToolDefinition;
import viper.com.claudecode.tools.BaseTools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * s09/s11: 团队成员管理。每个 teammate 在独立线程里跑一个 work-idle 双阶段循环。
 */
public class TeammateManager {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final ObjectMapper PLAIN = new ObjectMapper();

    public static class Member {
        public String name;
        public String role;
        public String status;  // idle | working | shutdown
        public Member() {}
        public Member(String name, String role, String status) {
            this.name = name; this.role = role; this.status = status;
        }
    }

    public static class TeamConfig {
        public String teamName = "default";
        public List<Member> members = new ArrayList<>();
    }

    private final MessageBus bus;
    private final TaskManager taskMgr;
    private final AnthropicClient client;
    private final Path configPath;
    private final TeamConfig config;
    private final Map<String, Thread> threads = new ConcurrentHashMap<>();

    public TeammateManager(MessageBus bus, TaskManager taskMgr, AnthropicClient client) {
        this.bus = bus;
        this.taskMgr = taskMgr;
        this.client = client;
        try { Files.createDirectories(Config.TEAM_DIR); } catch (IOException ignored) {}
        this.configPath = Config.TEAM_DIR.resolve("config.json");
        this.config = loadConfig();
    }

    private TeamConfig loadConfig() {
        if (Files.exists(configPath)) {
            try {
                return MAPPER.readValue(Files.readString(configPath), TeamConfig.class);
            } catch (IOException ignored) {}
        }
        return new TeamConfig();
    }

    private synchronized void saveConfig() {
        try {
            Files.writeString(configPath, MAPPER.writeValueAsString(config));
        } catch (IOException ignored) {}
    }

    private synchronized Member find(String name) {
        for (Member m : config.members) if (m.name.equals(name)) return m;
        return null;
    }

    public synchronized String spawn(String name, String role, String prompt) {
        Member m = find(name);
        if (m != null) {
            if (!m.status.equals("idle") && !m.status.equals("shutdown")) {
                return "Error: '" + name + "' is currently " + m.status;
            }
            m.status = "working";
            m.role = role;
        } else {
            m = new Member(name, role, "working");
            config.members.add(m);
        }
        saveConfig();
        Thread t = new Thread(() -> teammateLoop(name, role, prompt));
        t.setDaemon(true);
        t.start();
        threads.put(name, t);
        return "Spawned '" + name + "' (role: " + role + ")";
    }

    private synchronized void setStatus(String name, String status) {
        Member m = find(name);
        if (m != null) {
            m.status = status;
            saveConfig();
        }
    }

    private List<ToolDefinition> buildTools() {
        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(new ToolDefinition("bash", "Run command.",
                Map.of("type", "object",
                       "properties", Map.of("command", Map.of("type", "string")),
                       "required", List.of("command"))));
        tools.add(new ToolDefinition("read_file", "Read file.",
                Map.of("type", "object",
                       "properties", Map.of("path", Map.of("type", "string")),
                       "required", List.of("path"))));
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
        tools.add(new ToolDefinition("send_message", "Send message.",
                Map.of("type", "object",
                       "properties", Map.of(
                               "to", Map.of("type", "string"),
                               "content", Map.of("type", "string")),
                       "required", List.of("to", "content"))));
        tools.add(new ToolDefinition("idle", "Signal no more work.",
                Map.of("type", "object", "properties", Map.of())));
        tools.add(new ToolDefinition("claim_task", "Claim task by ID.",
                Map.of("type", "object",
                       "properties", Map.of("task_id", Map.of("type", "integer")),
                       "required", List.of("task_id"))));
        return tools;
    }

    private void teammateLoop(String name, String role, String prompt) {
        String teamName = config.teamName;
        String sysPrompt = "You are '" + name + "', role: " + role + ", team: " + teamName +
                ", at " + Config.WORKDIR + ". Use idle when done with current work. " +
                "You may auto-claim tasks.";

        List<Message> messages = new ArrayList<>();
        messages.add(Message.user(prompt));
        List<ToolDefinition> tools = buildTools();

        while (true) {
            // -- WORK PHASE --
            boolean idleRequested = false;
            for (int round = 0; round < 50; round++) {
                List<Map<String, Object>> inbox = bus.readInbox(name);
                for (Map<String, Object> msg : inbox) {
                    if ("shutdown_request".equals(msg.get("type"))) {
                        setStatus(name, "shutdown");
                        return;
                    }
                    try {
                        messages.add(Message.user(PLAIN.writeValueAsString(msg)));
                    } catch (Exception ignored) {}
                }

                AnthropicResponse response;
                try {
                    response = client.createMessage(Config.getModelId(), sysPrompt, messages, tools, 8000);
                } catch (Exception e) {
                    setStatus(name, "shutdown");
                    return;
                }
                messages.add(Message.assistant(response.content));
                if (!"tool_use".equals(response.stopReason)) break;

                List<Map<String, Object>> results = new ArrayList<>();
                for (ContentBlock b : response.content) {
                    if (!"tool_use".equals(b.type)) continue;
                    String output;
                    try {
                        output = switch (b.name) {
                            case "idle" -> "Entering idle phase.";
                            case "claim_task" -> taskMgr.claim(((Number) b.input.get("task_id")).intValue(), name);
                            case "send_message" -> bus.send(name, str(b.input.get("to")), str(b.input.get("content")));
                            case "bash" -> BaseTools.runBash(str(b.input.get("command")), b.id);
                            case "read_file" -> BaseTools.runRead(str(b.input.get("path")), b.id, null);
                            case "write_file" -> BaseTools.runWrite(str(b.input.get("path")), str(b.input.get("content")));
                            case "edit_file" -> BaseTools.runEdit(str(b.input.get("path")),
                                    str(b.input.get("old_text")), str(b.input.get("new_text")));
                            default -> "Unknown";
                        };
                    } catch (Exception e) {
                        output = "Error: " + e.getMessage();
                    }
                    System.out.println("  [" + name + "] " + b.name + ": " +
                            (output.length() > 120 ? output.substring(0, 120) : output));
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("type", "tool_result");
                    result.put("tool_use_id", b.id);
                    result.put("content", output);
                    results.add(result);
                    if ("idle".equals(b.name)) idleRequested = true;
                }
                messages.add(Message.user(results));
                if (idleRequested) break;
            }

            // -- IDLE PHASE --
            setStatus(name, "idle");
            boolean resume = false;
            int rounds = Config.IDLE_TIMEOUT / Math.max(Config.POLL_INTERVAL, 1);
            for (int i = 0; i < rounds; i++) {
                try { Thread.sleep(Config.POLL_INTERVAL * 1000L); } catch (InterruptedException ignored) {}
                List<Map<String, Object>> inbox = bus.readInbox(name);
                if (!inbox.isEmpty()) {
                    for (Map<String, Object> msg : inbox) {
                        if ("shutdown_request".equals(msg.get("type"))) {
                            setStatus(name, "shutdown");
                            return;
                        }
                        try {
                            messages.add(Message.user(PLAIN.writeValueAsString(msg)));
                        } catch (Exception ignored) {}
                    }
                    resume = true;
                    break;
                }
                // 寻找未认领任务
                Map<String, Object> picked = findUnclaimedTask();
                if (picked != null) {
                    int tid = ((Number) picked.get("id")).intValue();
                    taskMgr.claim(tid, name);
                    if (messages.size() <= 3) {
                        messages.add(0, Message.user("<identity>You are '" + name + "', role: " + role +
                                ", team: " + teamName + ".</identity>"));
                        messages.add(1, Message.assistant("I am " + name + ". Continuing."));
                    }
                    messages.add(Message.user("<auto-claimed>Task #" + tid + ": " + picked.get("subject") + "\n" +
                            picked.getOrDefault("description", "") + "</auto-claimed>"));
                    messages.add(Message.assistant("Claimed task #" + tid + ". Working on it."));
                    resume = true;
                    break;
                }
            }
            if (!resume) {
                setStatus(name, "shutdown");
                return;
            }
            setStatus(name, "working");
        }
    }

    // Reserved for future per-thread flags
    @SuppressWarnings("unused")
    private final ThreadLocal<Boolean> idleRequestedFlag = ThreadLocal.withInitial(() -> false);

    @SuppressWarnings("unchecked")
    private Map<String, Object> findUnclaimedTask() {
        try (Stream<Path> stream = Files.list(Config.TASKS_DIR)) {
            for (Path f : stream.sorted().toList()) {
                if (!f.getFileName().toString().startsWith("task_")) continue;
                Map<String, Object> t;
                try {
                    t = PLAIN.readValue(Files.readString(f), Map.class);
                } catch (IOException e) { continue; }
                if (!"pending".equals(t.get("status"))) continue;
                if (t.get("owner") != null) continue;
                Object blocked = t.get("blockedBy");
                if (blocked instanceof List<?> l && !l.isEmpty()) continue;
                return t;
            }
        } catch (IOException ignored) {}
        return null;
    }

    public synchronized String listAll() {
        if (config.members.isEmpty()) return "No teammates.";
        StringBuilder sb = new StringBuilder("Team: ").append(config.teamName);
        for (Member m : config.members) {
            sb.append("\n  ").append(m.name).append(" (").append(m.role).append("): ").append(m.status);
        }
        return sb.toString();
    }

    public synchronized List<String> memberNames() {
        List<String> out = new ArrayList<>();
        for (Member m : config.members) out.add(m.name);
        return out;
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }
}
