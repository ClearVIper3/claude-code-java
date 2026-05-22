package viper.com.claudecode.tools;

import viper.com.claudecode.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * s06: persisted-output。把过大的工具输出落盘，并替换为带预览的 marker。
 */
public final class PersistedOutput {

    private PersistedOutput() {}

    public static Path persistToolResult(String toolUseId, String content) {
        try {
            Files.createDirectories(Config.TOOL_RESULTS_DIR);
            String safeId = (toolUseId == null ? "unknown" : toolUseId).replaceAll("[^a-zA-Z0-9_.-]", "_");
            Path path = Config.TOOL_RESULTS_DIR.resolve(safeId + ".txt");
            if (!Files.exists(path)) {
                Files.writeString(path, content);
            }
            return Config.WORKDIR.relativize(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String formatSize(int size) {
        if (size < 1024) return size + "B";
        if (size < 1024 * 1024) return String.format("%.1fKB", size / 1024.0);
        return String.format("%.1fMB", size / (1024.0 * 1024.0));
    }

    public static PreviewSlice previewSlice(String text, int limit) {
        if (text.length() <= limit) return new PreviewSlice(text, false);
        int idx = text.substring(0, limit).lastIndexOf('\n');
        int cut = idx > (limit * 0.5) ? idx : limit;
        return new PreviewSlice(text.substring(0, cut), true);
    }

    public static String buildPersistedMarker(Path storedPath, String content) {
        PreviewSlice ps = previewSlice(content, Config.PERSISTED_PREVIEW_CHARS);
        StringBuilder sb = new StringBuilder();
        sb.append(Config.PERSISTED_OPEN).append('\n')
          .append("Output too large (").append(formatSize(content.length())).append("). ")
          .append("Full output saved to: ").append(storedPath).append("\n\n")
          .append("Preview (first ").append(formatSize(Config.PERSISTED_PREVIEW_CHARS)).append("):\n")
          .append(ps.text);
        if (ps.hasMore) sb.append("\n...");
        sb.append('\n').append(Config.PERSISTED_CLOSE);
        return sb.toString();
    }

    public static String maybePersistOutput(String toolUseId, String output, Integer triggerChars) {
        if (output == null) return "";
        int trigger = triggerChars == null ? Config.PERSIST_OUTPUT_TRIGGER_CHARS_DEFAULT : triggerChars;
        if (output.length() <= trigger) return output;
        Path stored = persistToolResult(toolUseId, output);
        return buildPersistedMarker(stored, output);
    }

    public static String maybePersistOutput(String toolUseId, String output) {
        return maybePersistOutput(toolUseId, output, null);
    }

    public record PreviewSlice(String text, boolean hasMore) {}
}
