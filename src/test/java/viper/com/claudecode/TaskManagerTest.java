package viper.com.claudecode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import viper.com.claudecode.managers.TaskManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskManagerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testCreateGetUpdateList() throws Exception {
        TaskManager mgr = new TaskManager();
        String created = mgr.create("Implement feature X", "Description here");
        Map<?, ?> task = MAPPER.readValue(created, Map.class);
        int id = ((Number) task.get("id")).intValue();
        assertEquals("Implement feature X", task.get("subject"));
        assertEquals("pending", task.get("status"));

        String got = mgr.get(id);
        assertTrue(got.contains("Implement feature X"));

        String updated = mgr.update(id, "in_progress", null, null);
        assertTrue(updated.contains("in_progress"));

        String list = mgr.listAll();
        assertTrue(list.contains("#" + id));

        // mark completed -> dependent cleanup
        String done = mgr.update(id, "completed", null, null);
        assertTrue(done.contains("completed"));
    }

    @Test
    void testBlockedBy() throws Exception {
        TaskManager mgr = new TaskManager();
        Map<?, ?> a = MAPPER.readValue(mgr.create("A", null), Map.class);
        Map<?, ?> b = MAPPER.readValue(mgr.create("B", null), Map.class);
        int aid = ((Number) a.get("id")).intValue();
        int bid = ((Number) b.get("id")).intValue();
        mgr.update(bid, null, List.of(aid), null);
        String bGet = mgr.get(bid);
        assertTrue(bGet.contains(String.valueOf(aid)), "B should be blocked by A; got: " + bGet);
        // 完成 A 后 B 的 blockedBy 自动移除
        mgr.update(aid, "completed", null, null);
        String bAfter = mgr.get(bid);
        Map<?, ?> bMap = MAPPER.readValue(bAfter, Map.class);
        List<?> bb = (List<?>) bMap.get("blockedBy");
        assertTrue(bb.isEmpty(), "blockedBy should be cleared after A completed");
    }

    @Test
    void testClaim() {
        TaskManager mgr = new TaskManager();
        try {
            String created = mgr.create("Claim me", "");
            Map<?, ?> task = MAPPER.readValue(created, Map.class);
            int id = ((Number) task.get("id")).intValue();
            String r = mgr.claim(id, "alice");
            assertTrue(r.contains("alice"));
            Map<?, ?> got = MAPPER.readValue(mgr.get(id), Map.class);
            assertEquals("alice", got.get("owner"));
            assertEquals("in_progress", got.get("status"));
        } catch (Exception e) {
            fail(e);
        }
    }
}
