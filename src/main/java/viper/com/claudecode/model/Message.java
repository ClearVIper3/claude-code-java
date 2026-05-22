package viper.com.claudecode.model;

/**
 * 对话消息。content 可以是 String 或 List&lt;ContentBlock&gt;。
 * 为了与 Python 字典等价，统一用 Object。序列化时 AnthropicClient 负责转换。
 */
public class Message {
    public String role;     // "user" | "assistant"
    public Object content;  // String 或 List<ContentBlock> 或 List<Map<...>>

    public Message() {}

    public Message(String role, Object content) {
        this.role = role;
        this.content = content;
    }

    public static Message user(Object content) { return new Message("user", content); }
    public static Message assistant(Object content) { return new Message("assistant", content); }
}
