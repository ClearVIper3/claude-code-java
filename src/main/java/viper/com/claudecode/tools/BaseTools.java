package viper.com.claudecode.tools;

import viper.com.claudecode.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 基础工具：bash / read_file / write_file / edit_file。
 * 对应 s_full.py 中的 base_tools 部分。
 */
public final class BaseTools {

    private BaseTools() {}

    public static Path safePath(String p) {
        Path resolved = Config.WORKDIR.resolve(p).normalize().toAbsolutePath();
        if (!resolved.startsWith(Config.WORKDIR)) {
            throw new IllegalArgumentException("Path escapes workspace: " + p);
        }
        return resolved;
    }

    public static String runBash(String command, String toolUseId) {
        String[] dangerous = {"rm -rf /", "sudo", "shutdown", "reboot", "> /dev/"};
        for (String d : dangerous) {
            if (command.contains(d)) return "Error: Dangerous command blocked";
        }
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
                    if (out.length() > 5_000_000) break; // hard cap
                }
            }
            boolean done = p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return "Error: Timeout (120s)";
            }
            String text = out.toString().trim();
            if (text.isEmpty()) return "(no output)";
            String persisted = PersistedOutput.maybePersistOutput(
                    toolUseId, text, Config.PERSIST_OUTPUT_TRIGGER_CHARS_BASH);
            return truncate(persisted);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static String runRead(String path, String toolUseId, Integer limit) {
        try {
            Path fp = safePath(path);
            List<String> lines = Files.readAllLines(fp);
            if (limit != null && limit < lines.size()) {
                lines = new java.util.ArrayList<>(lines.subList(0, limit));
                lines.add("... (" + (Files.readAllLines(fp).size() - limit) + " more)");
            }
            String out = String.join("\n", lines);
            out = PersistedOutput.maybePersistOutput(toolUseId, out);
            return truncate(out);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static String runWrite(String path, String content) {
        try {
            Path fp = safePath(path);
            Files.createDirectories(fp.getParent());
            Files.writeString(fp, content);
            return "Wrote " + content.length() + " bytes to " + path;
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    public static String runEdit(String path, String oldText, String newText) {
        try {
            Path fp = safePath(path);
            String c = Files.readString(fp);
            if (!c.contains(oldText)) {
                return "Error: Text not found in " + path;
            }
            int idx = c.indexOf(oldText);
            String replaced = c.substring(0, idx) + newText + c.substring(idx + oldText.length());
            Files.writeString(fp, replaced);
            return "Edited " + path;
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= Config.CONTEXT_TRUNCATE_CHARS ? s : s.substring(0, Config.CONTEXT_TRUNCATE_CHARS);
    }
}
