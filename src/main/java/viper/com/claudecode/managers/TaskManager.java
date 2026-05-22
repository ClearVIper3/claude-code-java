package viper.com.claudecode.managers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import viper.com.claudecode.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * s07: 文件型任务系统。任务以 JSON 文件落盘到 .tasks/ 下。
 */
public class TaskManager {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public TaskManager() {
        try {
            Files.createDirectories(Config.TASKS_DIR);
        } catch (IOException ignored) {}
    }

    private synchronized int nextId() {
        int max = 0;
        try (Stream<Path> stream = Files.list(Config.TASKS_DIR)) {
            for (Path p : stream.toList()) {
                String name = p.getFileName().toString();
                if (name.startsWith("task_") && name.endsWith(".json")) {
                    try {
                        int id = Integer.parseInt(name.substring(5, name.length() - 5));
                        if (id > max) max = id;
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException ignored) {}
        return max + 1;
    }

    private Map<String, Object> load(int tid) {
        Path p = Config.TASKS_DIR.resolve("task_" + tid + ".json");
        if (!Files.exists(p)) throw new IllegalArgumentException("Task " + tid + " not found");
        try {
            //noinspection unchecked
            return MAPPER.readValue(Files.readString(p), Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void save(Map<String, Object> task) {
        try {
            Path p = Config.TASKS_DIR.resolve("task_" + task.get("id") + ".json");
            Files.writeString(p, MAPPER.writeValueAsString(task));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized String create(String subject, String description) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", nextId());
        task.put("subject", subject);
        task.put("description", description == null ? "" : description);
        task.put("status", "pending");
        task.put("owner", null);
        task.put("blockedBy", new ArrayList<>());
        task.put("blocks", new ArrayList<>());
        save(task);
        return toJson(task);
    }

    public synchronized String get(int tid) {
        return toJson(load(tid));
    }

    @SuppressWarnings("unchecked")
    public synchronized String update(int tid, String status,
                                      List<Integer> addBlockedBy, List<Integer> addBlocks) {
        Map<String, Object> task = load(tid);
        if (status != null) {
            task.put("status", status);
            if ("completed".equals(status)) {
                try (Stream<Path> stream = Files.list(Config.TASKS_DIR)) {
                    for (Path f : stream.toList()) {
                        String fn = f.getFileName().toString();
                        if (!fn.startsWith("task_") || !fn.endsWith(".json")) continue;
                        Map<String, Object> t = MAPPER.readValue(Files.readString(f), Map.class);
                        List<Object> blocked = (List<Object>) t.get("blockedBy");
                        if (blocked != null && blocked.contains(tid)) {
                            blocked.removeIf(o -> o instanceof Number n && n.intValue() == tid);
                            save(t);
                        }
                    }
                } catch (IOException ignored) {}
            }
            if ("deleted".equals(status)) {
                try {
                    Files.deleteIfExists(Config.TASKS_DIR.resolve("task_" + tid + ".json"));
                } catch (IOException ignored) {}
                return "Task " + tid + " deleted";
            }
        }
        if (addBlockedBy != null) {
            List<Object> cur = (List<Object>) task.get("blockedBy");
            Set<Integer> set = new LinkedHashSet<>();
            for (Object o : cur) if (o instanceof Number n) set.add(n.intValue());
            set.addAll(addBlockedBy);
            task.put("blockedBy", new ArrayList<>(set));
        }
        if (addBlocks != null) {
            List<Object> cur = (List<Object>) task.get("blocks");
            Set<Integer> set = new LinkedHashSet<>();
            for (Object o : cur) if (o instanceof Number n) set.add(n.intValue());
            set.addAll(addBlocks);
            task.put("blocks", new ArrayList<>(set));
        }
        save(task);
        return toJson(task);
    }

    @SuppressWarnings("unchecked")
    public synchronized String listAll() {
        List<Map<String, Object>> tasks = new ArrayList<>();
        try (Stream<Path> stream = Files.list(Config.TASKS_DIR)) {
            for (Path f : stream.sorted().toList()) {
                String fn = f.getFileName().toString();
                if (!fn.startsWith("task_") || !fn.endsWith(".json")) continue;
                tasks.add(MAPPER.readValue(Files.readString(f), Map.class));
            }
        } catch (IOException ignored) {}
        if (tasks.isEmpty()) return "No tasks.";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> t : tasks) {
            String status = String.valueOf(t.get("status"));
            String marker = switch (status) {
                case "pending" -> "[ ]";
                case "in_progress" -> "[>]";
                case "completed" -> "[x]";
                default -> "[?]";
            };
            String owner = t.get("owner") != null ? " @" + t.get("owner") : "";
            Object blockedBy = t.get("blockedBy");
            String blocked = (blockedBy instanceof List<?> l && !l.isEmpty()) ? " (blocked by: " + l + ")" : "";
            sb.append(marker).append(" #").append(t.get("id")).append(": ").append(t.get("subject"))
              .append(owner).append(blocked).append('\n');
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    public synchronized String claim(int tid, String owner) {
        Map<String, Object> task = load(tid);
        task.put("owner", owner);
        task.put("status", "in_progress");
        save(task);
        return "Claimed task #" + tid + " for " + owner;
    }

    private static String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
