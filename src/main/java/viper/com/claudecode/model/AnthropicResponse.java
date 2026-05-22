package viper.com.claudecode.model;

import java.util.List;

/**
 * 模型响应。对应 anthropic.messages.create 的返回。
 */
public class AnthropicResponse {
    public List<ContentBlock> content;
    public String stopReason;        // "end_turn" | "tool_use" | ...
    public String role;
    public String model;

    public boolean isToolUse() {
        return "tool_use".equals(stopReason);
    }
}
