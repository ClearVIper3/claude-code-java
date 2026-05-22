package viper.com.claudecode.managers;

import viper.com.claudecode.Config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * s08: 后台命令执行。
 */
public class BackgroundManager {

    public static class Task {
        public String status;
        public String command;
        public String result;
        public Task(String status, String command, String result) {
            this.status = status; this.command = command; this.result = result;
        }
    }

    public final Map<String, Task> tasks = new ConcurrentHashMap<>();
    public final ConcurrentLinkedQueue<Map<String, Object>> notifications = new ConcurrentLinkedQueue<>();

    public String run(String command, int timeout) {
        String tid = UUID.randomUUID().toString().substring(0, 8);
        tasks.put(tid, new Task("running", command, null));
        Thread t = new Thread(() -> exec(tid, command, timeout));
        t.setDaemon(true);
        t.start();
        return "Background task " + tid + " started: " + command.substring(0, Math.min(80, command.length()));
    }

    private void exec(String tid, String command, int timeout) {
        Task task = tasks.get(tid);
        try {
            ProcessBuilder pb;
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("bash", "-c", command);
            }
            pb.directory(Config.WORKDIR.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (var r = p.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = r.read(buf)) >= 0) {
                    out.append(new String(buf, 0, n));
                    if (out.length() > 5_000_000) break;
                }
            }
            boolean done = p.waitFor(timeout, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                task.status = "error";
                task.result = "Timeout";
            } else {
                String text = out.toString().trim();
                if (text.length() > 50000) text = text.substring(0, 50000);
                task.status = "completed";
                task.result = text.isEmpty() ? "(no output)" : text;
            }
        } catch (Exception e) {
            task.status = "error";
            task.result = e.getMessage();
        }
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("task_id", tid);
        n.put("status", task.status);
        String preview = task.result == null ? "" : task.result;
        if (preview.length() > 500) preview = preview.substring(0, 500);
        n.put("result", preview);
        notifications.add(n);
    }

    public String check(String tid) {
        if (tid != null) {
            Task t = tasks.get(tid);
            if (t == null) return "Unknown: " + tid;
            return "[" + t.status + "] " + (t.result == null ? "(running)" : t.result);
        }
        if (tasks.isEmpty()) return "No bg tasks.";
        StringBuilder sb = new StringBuilder();
        for (var e : tasks.entrySet()) {
            String cmd = e.getValue().command;
            if (cmd.length() > 60) cmd = cmd.substring(0, 60);
            sb.append(e.getKey()).append(": [").append(e.getValue().status).append("] ").append(cmd).append('\n');
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    public List<Map<String, Object>> drain() {
        java.util.ArrayList<Map<String, Object>> out = new java.util.ArrayList<>();
        Map<String, Object> n;
        while ((n = notifications.poll()) != null) out.add(n);
        return out;
    }
}
