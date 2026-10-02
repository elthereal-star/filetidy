package dev.filetidy.util;

import dev.filetidy.FiletidyException;

import java.util.Locale;

/**
 * 人可读的文件大小解析与格式化。
 * <p>
 * 采用 1024 进制（1KB = 1024B），与文件管理器的习惯一致。
 */
public final class Size {

    private static final long KB = 1024L;
    private static final long MB = KB * 1024;
    private static final long GB = MB * 1024;
    private static final long TB = GB * 1024;

    private Size() {
    }

    /**
     * 解析 {@code "100MB"}、{@code "1.5 GB"}、{@code "512KB"}、{@code "1024"} 这类写法。
     * 不带单位时按字节处理。
     *
     * @throws FiletidyException 写法无法识别时
     */
    public static long parse(String text) {
        if (text == null || text.isBlank()) {
            throw new FiletidyException("文件大小不能为空");
        }
        String normalized = text.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        String digits = normalized.replaceAll("[^0-9.]", "");
        String unit = normalized.substring(digits.length());
        if (digits.isEmpty()) {
            throw new FiletidyException("无法识别的文件大小: " + text);
        }
        double value;
        try {
            value = Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            throw new FiletidyException("无法识别的文件大小: " + text);
        }
        long multiplier = switch (unit) {
            case "", "B" -> 1L;
            case "K", "KB", "KIB" -> KB;
            case "M", "MB", "MIB" -> MB;
            case "G", "GB", "GIB" -> GB;
            case "T", "TB", "TIB" -> TB;
            default -> throw new FiletidyException("无法识别的文件大小单位: " + text + "（支持 B/KB/MB/GB/TB）");
        };
        long bytes = Math.round(value * multiplier);
        if (bytes <= 0) {
            throw new FiletidyException("文件大小必须大于 0: " + text);
        }
        return bytes;
    }

    /** 把字节数格式化成便于阅读的形式，例如 {@code 1.5 MB}。 */
    public static String format(long bytes) {
        if (bytes < KB) {
            return bytes + " B";
        }
        if (bytes < MB) {
            return trim(bytes / (double) KB) + " KB";
        }
        if (bytes < GB) {
            return trim(bytes / (double) MB) + " MB";
        }
        if (bytes < TB) {
            return trim(bytes / (double) GB) + " GB";
        }
        return trim(bytes / (double) TB) + " TB";
    }

    private static String trim(double value) {
        return value >= 100 || value == Math.floor(value)
                ? String.valueOf(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }
}
