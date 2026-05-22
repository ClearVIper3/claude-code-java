package viper.com.claudecode;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * 全局配置常量。对应 s_full.py 顶部的常量定义。
 */
public final class Config {
    private Config() {}

    public static final Path WORKDIR = Paths.get("").toAbsolutePath();

    public static final Path TEAM_DIR = WORKDIR.resolve(".team");
    public static final Path INBOX_DIR = TEAM_DIR.resolve("inbox");
    public static final Path TASKS_DIR = WORKDIR.resolve(".tasks");
    public static final Path SKILLS_DIR = WORKDIR.resolve("skills");
    public static final Path TRANSCRIPT_DIR = WORKDIR.resolve(".transcripts");

    public static final int TOKEN_THRESHOLD = 100000;
    public static final int POLL_INTERVAL = 5;
    public static final int IDLE_TIMEOUT = 60;

    public static final Path TASK_OUTPUT_DIR = WORKDIR.resolve(".task_outputs");
    public static final Path TOOL_RESULTS_DIR = TASK_OUTPUT_DIR.resolve("tool-results");

    public static final int PERSIST_OUTPUT_TRIGGER_CHARS_DEFAULT = 50000;
    public static final int PERSIST_OUTPUT_TRIGGER_CHARS_BASH = 30000;
    public static final int CONTEXT_TRUNCATE_CHARS = 50000;

    public static final String PERSISTED_OPEN = "<persisted-output>";
    public static final String PERSISTED_CLOSE = "</persisted-output>";
    public static final int PERSISTED_PREVIEW_CHARS = 2000;
    public static final int KEEP_RECENT = 3;

    public static final Set<String> PRESERVE_RESULT_TOOLS = Set.of("read_file");

    public static final Set<String> VALID_MSG_TYPES = Set.of(
            "message", "broadcast", "shutdown_request",
            "shutdown_response", "plan_approval_response"
    );

    public static String getModelId() {
        String model = System.getProperty("MODEL_ID");
        if (model == null || model.isBlank()) {
            model = System.getenv("MODEL_ID");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("MODEL_ID environment variable is required");
        }
        return model;
    }

    public static String getBaseUrl() {
        String v = System.getProperty("ANTHROPIC_BASE_URL");
        if (v == null || v.isBlank()) v = System.getenv("ANTHROPIC_BASE_URL");
        return v;
    }

    public static String getApiKey() {
        String v = System.getProperty("ANTHROPIC_API_KEY");
        if (v == null || v.isBlank()) v = System.getenv("ANTHROPIC_API_KEY");
        return v;
    }

    public static String getAuthToken() {
        if (getBaseUrl() != null && !getBaseUrl().isBlank()) return null;
        String v = System.getProperty("ANTHROPIC_AUTH_TOKEN");
        if (v == null || v.isBlank()) v = System.getenv("ANTHROPIC_AUTH_TOKEN");
        return v;
    }
}
