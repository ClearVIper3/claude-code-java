package viper.com.claudecode;

import org.junit.jupiter.api.Test;
import viper.com.claudecode.tools.BaseTools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BaseToolsTest {

    @Test
    void testWriteAndRead() throws IOException {
        Path tmp = Files.createTempFile(Config.WORKDIR, "test_", ".txt");
        String rel = Config.WORKDIR.relativize(tmp).toString();
        try {
            String resp = BaseTools.runWrite(rel, "hello world\n");
            assertTrue(resp.startsWith("Wrote"));
            String content = BaseTools.runRead(rel, "tid", null);
            assertTrue(content.contains("hello world"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void testEdit() throws IOException {
        Path tmp = Files.createTempFile(Config.WORKDIR, "edit_", ".txt");
        String rel = Config.WORKDIR.relativize(tmp).toString();
        try {
            BaseTools.runWrite(rel, "foo bar baz");
            String resp = BaseTools.runEdit(rel, "bar", "BAR");
            assertTrue(resp.startsWith("Edited"));
            String content = Files.readString(tmp);
            assertEquals("foo BAR baz", content);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void testEditTextNotFound() throws IOException {
        Path tmp = Files.createTempFile(Config.WORKDIR, "edit_", ".txt");
        String rel = Config.WORKDIR.relativize(tmp).toString();
        try {
            BaseTools.runWrite(rel, "foo bar");
            String resp = BaseTools.runEdit(rel, "missing", "x");
            assertTrue(resp.startsWith("Error"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void testBash() {
        String out = BaseTools.runBash("echo hello-bash", "tid");
        assertTrue(out.contains("hello-bash"));
    }

    @Test
    void testBashDangerousBlocked() {
        String out = BaseTools.runBash("rm -rf /", "tid");
        assertTrue(out.startsWith("Error"));
    }

    @Test
    void testSafePathEscape() {
        assertThrows(IllegalArgumentException.class, () -> BaseTools.safePath("../../../etc/passwd"));
    }
}
