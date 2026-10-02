package dev.filetidy.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleOutputTest {

    private static final Map<String, String> PLAIN_CONSOLES = Map.of();
    private static final Map<String, String> GIT_BASH = Map.of("MSYSTEM", "MINGW64", "TERM", "xterm-256color");

    @Test
    void keepsNativeEncodingWhenAlreadyUtf8() {
        assertNull(ConsoleOutput.decide("UTF-8", true, GIT_BASH, null));
    }

    @Test
    void switchesOnWindowsUtf8CapableTerminal() {
        assertEquals(StandardCharsets.UTF_8, ConsoleOutput.decide("GBK", true, GIT_BASH, null));
        assertEquals(StandardCharsets.UTF_8,
                ConsoleOutput.decide("GBK", true, Map.of("WT_SESSION", "abc"), null));
    }

    @Test
    void keepsLegacyWindowsConsoleUntouched() {
        // 老式 conhost：代码页 936，写 UTF-8 反而会花屏
        assertNull(ConsoleOutput.decide("GBK", true, PLAIN_CONSOLES, null));
    }

    @Test
    void switchesOnNonWindowsTerminals() {
        assertEquals(StandardCharsets.UTF_8, ConsoleOutput.decide("US-ASCII", false, PLAIN_CONSOLES, null));
    }

    @Test
    void overrideWins() {
        assertEquals(StandardCharsets.UTF_8, ConsoleOutput.decide("GBK", true, PLAIN_CONSOLES, "UTF-8"));
        assertEquals(StandardCharsets.ISO_8859_1, ConsoleOutput.decide("UTF-8", false, PLAIN_CONSOLES, "ISO-8859-1"));
        assertNull(ConsoleOutput.decide("GBK", true, GIT_BASH, "native"));
    }

    @Test
    void unknownOverrideIsRejectedWithReadableMessage() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ConsoleOutput.decide("GBK", true, GIT_BASH, "no-such-charset"));
        assertTrue(error.getMessage().contains("不支持的输出编码"), error.getMessage());
    }

    @Test
    void missingStdoutEncodingMeansNoChange() {
        assertNull(ConsoleOutput.decide(null, true, GIT_BASH, null));
    }
}
