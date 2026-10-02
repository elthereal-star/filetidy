package dev.filetidy.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextWidthTest {

    @Test
    void countsCjkAsTwoColumns() {
        assertEquals(2, TextWidth.width("01"));
        assertEquals(4, TextWidth.width("图片"));
        assertEquals(4, TextWidth.width("01图"));
        assertEquals(7, TextWidth.width("01-图片"));
    }

    @Test
    void ignoresSurrogatePairWidth() {
        // emoji 属于宽字符，占两列；用码点计数不能把代理对算成两个字符
        assertEquals(2, TextWidth.width("\uD83D\uDCC1"));
    }

    @Test
    void padsUsingDisplayWidth() {
        // "01-图片" 显示宽度是 7，补齐到 11 需要 4 个空格
        assertEquals("01-图片    ", TextWidth.padRight("01-图片", 11));
        assertEquals("abc  ", TextWidth.padRight("abc", 5));
    }

    @Test
    void doesNotTruncateOverlongText() {
        assertEquals("abcdef", TextWidth.padRight("abcdef", 3));
    }
}
