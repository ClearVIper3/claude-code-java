package viper.com.claudecode.model;

import java.util.List;
import java.util.Map;

/**
 * 助手返回的内容块。简化版：一个 block 可以是 text 或 tool_use。
 * 对应 Anthropic API 的 content block。
 */
public class ContentBlock {
    public String type;          // "text" | "tool_use" | "tool_result"
    public String text;          // type=text
    public String id;            // type=tool_use, 工具调用 id
    public String name;          // type=tool_use
    public Map<String, Object> input; // type=tool_use
    // for tool_result
    public String toolUseId;
    public Object content;       // string or list

    public static ContentBlock text(String txt) {
        ContentBlock b = new ContentBlock();
        b.type = "text";
        b.text = txt;
        return b;
    }

    public static ContentBlock toolUse(String id, String name, Map<String, Object> input) {
        ContentBlock b = new ContentBlock();
        b.type = "tool_use";
        b.id = id;
        b.name = name;
        b.input = input;
        return b;
    }

    public static ContentBlock toolResult(String toolUseId, Object content) {
        ContentBlock b = new ContentBlock();
        b.type = "tool_result";
        b.toolUseId = toolUseId;
        b.content = content;
        return b;
    }
}
