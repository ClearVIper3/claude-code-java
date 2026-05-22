package viper.com.claudecode;

import io.github.cdimascio.dotenv.Dotenv;
import viper.com.claudecode.compress.Compression;
import viper.com.claudecode.core.AgentLoop;
import viper.com.claudecode.core.Context;
import viper.com.claudecode.model.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Capstone Teaching Agent — Java 版主入口（REPL）。
 * 对应 s_full.py 末尾的 __main__ 块。
 *
 * 支持命令：/compact /tasks /team /inbox  以及  q / exit / 空行 退出
 */
public class Main {

    public static void main(String[] args) throws IOException {
        // 加载 .env（如果存在），相当于 python-dotenv 的 load_dotenv(override=True)
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .ignoreIfMalformed()
                    .load();
            dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
        } catch (Exception ignored) {}

        // 与 Python 逻辑对齐：若有 BASE_URL，则不带 ANTHROPIC_AUTH_TOKEN
        if (Config.getBaseUrl() != null && !Config.getBaseUrl().isBlank()) {
            System.clearProperty("ANTHROPIC_AUTH_TOKEN");
        }

        // 触发模型 ID 检查（缺失则报错退出）
        try {
            Config.getModelId();
        } catch (Exception e) {
            System.err.println("[fatal] " + e.getMessage());
            System.exit(1);
        }

        List<Message> history = new ArrayList<>();
        BufferedReader br = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));

        while (true) {
            System.out.print("\u001B[36ms_full >> \u001B[0m");
            System.out.flush();
            String query = br.readLine();
            if (query == null) break; // EOF
            String trimmed = query.trim();
            String low = trimmed.toLowerCase();
            if (low.equals("q") || low.equals("exit") || low.isEmpty()) break;

            switch (trimmed) {
                case "/compact" -> {
                    if (!history.isEmpty()) {
                        System.out.println("[manual compact via /compact]");
                        List<Message> compacted = Compression.autoCompact(
                                history, null, Context.CLIENT, Config.getModelId());
                        history.clear();
                        history.addAll(compacted);
                    }
                    continue;
                }
                case "/tasks" -> { System.out.println(Context.TASK_MGR.listAll()); continue; }
                case "/team" -> { System.out.println(Context.TEAM.listAll()); continue; }
                case "/inbox" -> {
                    try {
                        System.out.println(AnthropicClient.MAPPER.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(Context.BUS.readInbox("lead")));
                    } catch (Exception e) {
                        System.out.println("[]");
                    }
                    continue;
                }
                default -> {}
            }

            history.add(Message.user(query));
            try {
                AgentLoop.run(history);
            } catch (Throwable t) {
                System.err.println("[agent error] " + t.getMessage());
                t.printStackTrace();
            }
            for(Message msg : history){
                System.out.print(msg.content);
            }
            System.out.println();
        }
    }
}
