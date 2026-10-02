package dev.filetidy.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnsiTest {

    private static final Map<String, String> EMPTY = Map.of();

    @Test
    void overrideWinsOverEverything() {
        assertTrue(Ansi.decide("always", Map.of("NO_COLOR", "1"), false));
        assertFalse(Ansi.decide("never", Map.of("TERM", "xterm"), true));
    }

    @Test
    void noColorConventionDisablesColor() {
        assertFalse(Ansi.decide(null, Map.of("NO_COLOR", "1", "TERM", "xterm"), true));
        assertTrue(Ansi.decide(null, Map.of("NO_COLOR", ""), true), "空字符串按约定不算禁用");
    }

    @Test
    void autoDetectsRealTerminals() {
        assertTrue(Ansi.decide(null, EMPTY, true), "拿到控制台就着色");
        assertTrue(Ansi.decide(null, Map.of("TERM", "xterm-256color"), false), "Git Bash 靠 TERM 判断");
        assertFalse(Ansi.decide(null, EMPTY, false), "重定向到文件/CI 时保持纯文本");
    }

    @Test
    void outputIsPlainUntilInstalled() {
        // 未调用 install() 时始终是纯文本，保证测试断言不受颜色码干扰
        assertFalse(Ansi.enabled());
        assertTrue(Ansi.ok("已整理").equals("已整理"));
        assertTrue(Ansi.warn("注意").equals("注意"));
    }
}
