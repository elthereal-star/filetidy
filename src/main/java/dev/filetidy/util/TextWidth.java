package dev.filetidy.util;

/**
 * 等宽终端下的显示宽度计算。
 * <p>
 * 中日韩文字与全角符号占两列，直接 {@code String.length()} 会把表格排歪。
 */
public final class TextWidth {

    private TextWidth() {
    }

    /** 字符串在等宽终端里占用的列数。 */
    public static int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            width += isWide(codePoint) ? 2 : 1;
        }
        return width;
    }

    /** 右侧补空格到指定显示宽度；已经超宽时原样返回。 */
    public static String padRight(String text, int targetWidth) {
        int padding = targetWidth - width(text);
        return padding <= 0 ? text : text + " ".repeat(padding);
    }

    /** 常用中日韩、全角与 emoji 区段。 */
    private static boolean isWide(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x115F)
                || (codePoint >= 0x2600 && codePoint <= 0x27BF)
                || (codePoint >= 0x2E80 && codePoint <= 0x303E)
                || (codePoint >= 0x3041 && codePoint <= 0x33FF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0xA000 && codePoint <= 0xA4CF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFE30 && codePoint <= 0xFE6F)
                || (codePoint >= 0xFF00 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6)
                || (codePoint >= 0x1F300 && codePoint <= 0x1F5FF)
                || (codePoint >= 0x1F600 && codePoint <= 0x1F64F)
                || (codePoint >= 0x1F680 && codePoint <= 0x1F6FF)
                || (codePoint >= 0x1F900 && codePoint <= 0x1F9FF)
                || (codePoint >= 0x20000 && codePoint <= 0x3FFFD);
    }
}
