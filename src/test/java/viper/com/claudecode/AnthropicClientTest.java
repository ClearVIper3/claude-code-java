package viper.com.claudecode;

import org.junit.jupiter.api.Test;
import viper.com.claudecode.AnthropicClient;
import viper.com.claudecode.model.AnthropicResponse;
import viper.com.claudecode.model.ContentBlock;
import viper.com.claudecode.model.Message;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicClientTest {

    @Test
    void testParseResponseWithText() throws Exception {
        String body = """
            {
              "role": "assistant",
              "model": "test",
              "stop_reason": "end_turn",
              "content": [
                { "type": "text", "text": "hello" }
              ]
            }
            """;
        AnthropicResponse r = AnthropicClient.parseResponse(body);
        assertEquals("end_turn", r.stopReason);
        assertEquals(1, r.content.size());
        assertEquals("hello", r.content.get(0).text);
    }

    @Test
    void testParseResponseWithToolUse() throws Exception {
        String body = """
            {
              "role": "assistant",
              "stop_reason": "tool_use",
              "content": [
                { "type": "tool_use", "id": "tu1", "name": "bash", "input": {"command": "ls"} }
              ]
            }
            """;
        AnthropicResponse r = AnthropicClient.parseResponse(body);
        assertTrue(r.isToolUse());
        ContentBlock b = r.content.get(0);
        assertEquals("tu1", b.id);
        assertEquals("bash", b.name);
        assertEquals("ls", b.input.get("command"));
    }

    @Test
    void testSerializeMessageString() {
        Map<String, Object> out = AnthropicClient.serializeMessage(Message.user("hi"));
        assertEquals("user", out.get("role"));
        assertEquals("hi", out.get("content"));
    }

    @Test
    void testSerializeMessageWithBlocks() {
        Message m = Message.assistant(List.of(
                ContentBlock.text("a"),
                ContentBlock.toolUse("id1", "bash", Map.of("command", "ls"))
        ));
        Map<String, Object> out = AnthropicClient.serializeMessage(m);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) out.get("content");
        assertEquals(2, content.size());
        assertEquals("text", content.get(0).get("type"));
        assertEquals("tool_use", content.get(1).get("type"));
    }
}
