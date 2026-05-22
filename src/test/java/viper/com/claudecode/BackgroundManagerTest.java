package viper.com.claudecode;

import org.junit.jupiter.api.Test;
import viper.com.claudecode.managers.BackgroundManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BackgroundManagerTest {

    @Test
    void testRunCompletesAndNotifies() throws InterruptedException {
        BackgroundManager bg = new BackgroundManager();
        String os = System.getProperty("os.name", "").toLowerCase();
        String cmd = os.contains("win") ? "echo bg-test" : "echo bg-test";
        String resp = bg.run(cmd, 30);
        assertTrue(resp.startsWith("Background task"));

        // wait up to 10s
        for (int i = 0; i < 50; i++) {
            if (!bg.notifications.isEmpty()) break;
            Thread.sleep(200);
        }
        List<Map<String, Object>> drained = bg.drain();
        assertFalse(drained.isEmpty(), "expected a notification");
        Map<String, Object> n = drained.get(0);
        assertEquals("completed", n.get("status"));
        assertTrue(n.get("result").toString().contains("bg-test"));
    }
}
