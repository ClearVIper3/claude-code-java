package viper.com.claudecode;

import org.junit.jupiter.api.Test;
import viper.com.claudecode.managers.MessageBus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageBusTest {

    @Test
    void testSendAndRead() {
        MessageBus bus = new MessageBus();
        String id = "tester_" + System.nanoTime();
        bus.send("alice", id, "hi there");
        bus.send("bob", id, "second msg", "broadcast", null);
        List<Map<String, Object>> inbox = bus.readInbox(id);
        assertEquals(2, inbox.size());
        assertEquals("alice", inbox.get(0).get("from"));
        assertEquals("hi there", inbox.get(0).get("content"));
        assertEquals("broadcast", inbox.get(1).get("type"));

        // drained
        List<Map<String, Object>> again = bus.readInbox(id);
        assertTrue(again.isEmpty());
    }

    @Test
    void testBroadcast() {
        MessageBus bus = new MessageBus();
        String stamp = String.valueOf(System.nanoTime());
        String a = "a_" + stamp;
        String b = "b_" + stamp;
        String c = "c_" + stamp;
        bus.broadcast(a, "hello team", List.of(a, b, c));
        assertEquals(0, bus.readInbox(a).size()); // sender excluded
        assertEquals(1, bus.readInbox(b).size());
        assertEquals(1, bus.readInbox(c).size());
    }
}
