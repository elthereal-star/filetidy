package dev.filetidy.util;

import java.util.Locale;
import java.util.Map;

/**
 * 终端彩色输出。
 * <p>
 * <b>默认关闭</b>，只由 {@link #install()} 在程序入口处按环境决定是否开启。
 * 这样单元测试里捕获到的输出永远是纯文本，断言不必关心颜色码。
 */
public final class Ansi {

    /** 覆盖开关：always / never / auto。 */
    public static final String PROPERTY = "filetidy.color";

    private static final String RESET = "\u001B[0m";

    private static volatile boolean enabled = false;

    private Ansi() {
    }

    /** 在程序启动时调用一次，决定本次运行是否着色。 */
    public static void install() {
        enabled = decide(System.getProperty(PROPERTY), System.getenv(), System.console() != null);
    }

    /**
     * 抽成纯函数便于测试。
     * <p>
     * 优先级：显式覆盖 &gt; NO_COLOR 约定 &gt; 自动探测。
     * 「自动」看两件事：JVM 拿到了控制台，或者终端环境变量说明这确实是个终端
     * （Git Bash / MSYS 下 {@code System.console()} 常为 null，但 TERM 在）。
     */
    static boolean decide(String override, Map<String, String> env, boolean consoleAttached) {
        if (override != null && !override.isBlank()) {
            String value = override.trim().toLowerCase(Locale.ROOT);
            if (value.equals("always") || value.equals("true") || value.equals("on")) {
                return true;
            }
            if (value.equals("never") || value.equals("false") || value.equals("off")) {
                return false;
            }
        }
        String noColor = env.get("NO_COLOR");
        if (noColor != null && !noColor.isEmpty()) {
            return false;
        }
        return consoleAttached || env.containsKey("TERM");
    }

    public static boolean enabled() {
        return enabled;
    }

    public static String ok(String text) {
        return wrap("\u001B[32m", text);
    }

    public static String warn(String text) {
        return wrap("\u001B[33m", text);
    }

    public static String error(String text) {
        return wrap("\u001B[31m", text);
    }

    public static String dim(String text) {
        return wrap("\u001B[2m", text);
    }

    public static String bold(String text) {
        return wrap("\u001B[1m", text);
    }

    private static String wrap(String code, String text) {
        return enabled ? code + text + RESET : text;
    }
}
