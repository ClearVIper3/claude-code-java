package viper.com.claudecode;

import viper.com.claudecode.core.AgentLoop;
import viper.com.claudecode.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 端到端冒烟测试：使用 DeepSeek 跑一轮 Agent Loop。
 * 通过环境变量/系统属性设置（在测试入口由 SmokeRunner 注入）。
 */
public final class SmokeRunner {

    public static void main(String[] args) {
        // 强制注入 DeepSeek 配置（如未设置）
        if (sys("LLM_PROVIDER") == null) System.setProperty("LLM_PROVIDER", "openai");
        if (sys("ANTHROPIC_BASE_URL") == null) System.setProperty("ANTHROPIC_BASE_URL", "https://api.deepseek.com");
        if (sys("ANTHROPIC_API_KEY") == null) System.setProperty("ANTHROPIC_API_KEY",
                "sk-e45e4cff58ec4cd3999fb5aa6d271be4");
        if (sys("MODEL_ID") == null) System.setProperty("MODEL_ID", "deepseek-chat");

        String prompt = args.length > 0 ? String.join(" ", args)
                : "Use the bash tool to run `echo SMOKE_OK_TOKEN_42`. Then briefly confirm.";

        System.out.println("\n=== SmokeRunner ===");
        System.out.println("Provider: " + sys("LLM_PROVIDER"));
        System.out.println("BaseURL : " + sys("ANTHROPIC_BASE_URL"));
        System.out.println("Model   : " + sys("MODEL_ID"));
        System.out.println("Prompt  : " + prompt);
        System.out.println("===================\n");

        List<Message> history = new ArrayList<>();
        history.add(Message.user(prompt));

        try {
            AgentLoop.run(history);
            System.out.println("\n[smoke OK] agent loop finished without error.");
        } catch (Throwable t) {
            System.err.println("[smoke FAIL] " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static String sys(String k) {
        String v = System.getProperty(k);
        if (v == null || v.isBlank()) v = System.getenv(k);
        return (v == null || v.isBlank()) ? null : v;
    }
}
