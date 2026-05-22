package viper.com.claudecode;

import org.junit.jupiter.api.Test;
import viper.com.claudecode.tools.PersistedOutput;

import static org.junit.jupiter.api.Assertions.*;

class PersistedOutputTest {

    @Test
    void testFormatSize() {
        assertEquals("100B", PersistedOutput.formatSize(100));
        assertTrue(PersistedOutput.formatSize(2048).contains("KB"));
        assertTrue(PersistedOutput.formatSize(2 * 1024 * 1024).contains("MB"));
    }

    @Test
    void testPreviewSliceShort() {
        var ps = PersistedOutput.previewSlice("hello", 100);
        assertEquals("hello", ps.text());
        assertFalse(ps.hasMore());
    }

    @Test
    void testPreviewSliceLong() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) sb.append("line ").append(i).append("\n");
        var ps = PersistedOutput.previewSlice(sb.toString(), 100);
        assertTrue(ps.hasMore());
        assertTrue(ps.text().length() <= 100);
    }

    @Test
    void testMaybePersistSmallReturnsOriginal() {
        String s = "small output";
        assertEquals(s, PersistedOutput.maybePersistOutput("id1", s));
    }

    @Test
    void testMaybePersistLargeWritesFile() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200_000; i++) sb.append('x');
        String marker = PersistedOutput.maybePersistOutput("test_id_" + System.nanoTime(), sb.toString());
        assertTrue(marker.startsWith("<persisted-output>"));
        assertTrue(marker.contains("Full output saved to"));
        assertTrue(marker.endsWith("</persisted-output>"));
    }
}
