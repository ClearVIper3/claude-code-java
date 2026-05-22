package viper.com.claudecode.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * s03: TodoWrite。简单的待办列表，最多 20 项，最多 1 个 in_progress。
 */
public class TodoManager {

    public static class Todo {
        public String content;
        public String status;
        public String activeForm;
        public Todo(String content, String status, String activeForm) {
            this.content = content;
            this.status = status;
            this.activeForm = activeForm;
        }
    }

    private final List<Todo> items = new ArrayList<>();

    public synchronized String update(List<Map<String, Object>> rawItems) {
        List<Todo> validated = new ArrayList<>();
        int inProgress = 0;
        for (int i = 0; i < rawItems.size(); i++) {
            Map<String, Object> item = rawItems.get(i);
            String content = String.valueOf(item.getOrDefault("content", "")).trim();
            String status = String.valueOf(item.getOrDefault("status", "pending")).toLowerCase();
            String af = String.valueOf(item.getOrDefault("activeForm", "")).trim();
            if (content.isEmpty()) throw new IllegalArgumentException("Item " + i + ": content required");
            if (!status.equals("pending") && !status.equals("in_progress") && !status.equals("completed")) {
                throw new IllegalArgumentException("Item " + i + ": invalid status '" + status + "'");
            }
            if (af.isEmpty()) throw new IllegalArgumentException("Item " + i + ": activeForm required");
            if (status.equals("in_progress")) inProgress++;
            validated.add(new Todo(content, status, af));
        }
        if (validated.size() > 20) throw new IllegalArgumentException("Max 20 todos");
        if (inProgress > 1) throw new IllegalArgumentException("Only one in_progress allowed");
        items.clear();
        items.addAll(validated);
        return render();
    }

    public synchronized String render() {
        if (items.isEmpty()) return "No todos.";
        StringBuilder sb = new StringBuilder();
        int done = 0;
        for (Todo t : items) {
            String marker = switch (t.status) {
                case "completed" -> "[x]";
                case "in_progress" -> "[>]";
                case "pending" -> "[ ]";
                default -> "[?]";
            };
            sb.append(marker).append(' ').append(t.content);
            if ("in_progress".equals(t.status)) sb.append(" <- ").append(t.activeForm);
            sb.append('\n');
            if ("completed".equals(t.status)) done++;
        }
        sb.append('\n').append('(').append(done).append('/').append(items.size()).append(" completed)");
        return sb.toString();
    }

    public synchronized boolean hasOpenItems() {
        for (Todo t : items) {
            if (!"completed".equals(t.status)) return true;
        }
        return false;
    }
}
