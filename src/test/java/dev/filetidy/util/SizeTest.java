package dev.filetidy.util;

import dev.filetidy.FiletidyException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SizeTest {

    @Test
    void parsesCommonUnits() {
        assertEquals(1024L, Size.parse("1024"));
        assertEquals(1024L, Size.parse("1KB"));
        assertEquals(1024L * 1024, Size.parse("1MB"));
        assertEquals(1024L * 1024 * 1024, Size.parse("1GB"));
        assertEquals(150L * 1024 * 1024, Size.parse("150 MB"));
        assertEquals(1536L, Size.parse("1.5KB"));
        assertEquals(512L, Size.parse("0.5KB"));
    }

    @Test
    void isCaseInsensitiveAndAcceptsAliases() {
        assertEquals(Size.parse("100mb"), Size.parse("100MB"));
        assertEquals(Size.parse("100MiB"), Size.parse("100MB"));
        assertEquals(2048L, Size.parse("2k"));
    }

    @Test
    void rejectsGarbageWithReadableMessage() {
        assertTrue(assertThrows(FiletidyException.class, () -> Size.parse("abc"))
                .getMessage().contains("无法识别"));
        assertTrue(assertThrows(FiletidyException.class, () -> Size.parse("100PB"))
                .getMessage().contains("单位"));
        assertTrue(assertThrows(FiletidyException.class, () -> Size.parse("0"))
                .getMessage().contains("大于 0"));
        assertTrue(assertThrows(FiletidyException.class, () -> Size.parse("  "))
                .getMessage().contains("不能为空"));
    }

    @Test
    void formatsReadableText() {
        assertEquals("512 B", Size.format(512));
        assertEquals("1 KB", Size.format(1024));
        assertEquals("1.5 KB", Size.format(1536));
        assertEquals("1 MB", Size.format(1024 * 1024));
        assertEquals("1.5 GB", Size.format((long) (1.5 * 1024 * 1024 * 1024)));
        assertEquals("100 MB", Size.format(100L * 1024 * 1024));
    }
}
