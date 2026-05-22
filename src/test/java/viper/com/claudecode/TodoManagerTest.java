package viper.com.claudecode;

import org.junit.jupiter.api.Test;
import viper.com.claudecode.managers.TodoManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TodoManagerTest {

    @Test
    void testBasicUpdate() {
        TodoManager mgr = new TodoManager();
        String out = mgr.update(List.of(
                Map.of("content", "Step 1", "status", "pending", "activeForm", "Doing step 1"),
                Map.of("content", "Step 2", "status", "in_progress", "activeForm", "Doing step 2"),
                Map.of("content", "Step 3", "status", "completed", "activeForm", "Done step 3")
        ));
        assertTrue(out.contains("[ ] Step 1"));
        assertTrue(out.contains("[>] Step 2"));
        assertTrue(out.contains("[x] Step 3"));
        assertTrue(out.contains("(1/3 completed)"));
        assertTrue(mgr.hasOpenItems());
    }

    @Test
    void testMaxOneInProgress() {
        TodoManager mgr = new TodoManager();
        assertThrows(IllegalArgumentException.class, () -> mgr.update(List.of(
                Map.of("content", "A", "status", "in_progress", "activeForm", "Aing"),
                Map.of("content", "B", "status", "in_progress", "activeForm", "Bing")
        )));
    }

    @Test
    void testInvalidStatus() {
        TodoManager mgr = new TodoManager();
        assertThrows(IllegalArgumentException.class, () -> mgr.update(List.of(
                Map.of("content", "A", "status", "wrong", "activeForm", "Aing")
        )));
    }

    @Test
    void testHasOpenItemsFalseWhenAllCompleted() {
        TodoManager mgr = new TodoManager();
        mgr.update(List.of(
                Map.of("content", "A", "status", "completed", "activeForm", "Aing")
        ));
        assertFalse(mgr.hasOpenItems());
    }
}
