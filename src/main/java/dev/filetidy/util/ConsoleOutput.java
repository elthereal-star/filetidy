package dev.filetidy.util;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * 统一控制台输出编码。
 * <p>
 * JDK 18 起 {@code file.encoding} 默认为 UTF-8，但 {@code System.out} 的编码由
 * 控制台代码页决定：中文 Windows 下是 GBK。结果是同一份输出在 cmd 里正常、
 * 在 Windows Terminal / Git Bash / IDEA 里变成 {@code 01-ͼƬ} 这样的乱码。
 * <p>
 * 这里在启动时判断终端能力，必要时把 {@code System.out}/{@code System.err}
 * 换成 UTF-8 的 {@link PrintStream}，让输出在任何现代终端里都一致。
 * 老式 conhost（代码页 936）保持原样，避免反向搞乱。
 * <p>
 * 可用 {@code -Dfiletidy.output.encoding=UTF-8}（或 {@code native}）强制指定。
 */
public final class ConsoleOutput {

    /** 覆盖开关：填字符集名（如 UTF-8）强制切换，填 native 保持 JVM 原样。 */
    public static final String ENCODING_PROPERTY = "filetidy.output.encoding";

    private static final String NATIVE = "native";

    private ConsoleOutput() {
    }

    /** 把 System.out / System.err 按需换成 UTF-8。可在任意时刻调用。 */
    public static void install() {
        Charset charset;
        try {
            charset = decide(
                    System.getProperty("stdout.encoding"),
                    isWindows(System.getProperty("os.name")),
                    System.getenv(),
                    System.getProperty(ENCODING_PROPERTY));
        } catch (IllegalArgumentException e) {
            // 覆盖参数写错不该让程序起不来，退回终端默认编码即可
            System.err.println("警告: " + e.getMessage() + "，已改为保持终端默认编码。");
            return;
        }
        if (charset == null) {
            return;
        }
        System.setOut(printStream(FileDescriptor.out, charset));
        System.setErr(printStream(FileDescriptor.err, charset));
    }

    /**
     * 决定输出应该用什么编码；返回 {@code null} 表示保持 JVM 默认、不做改动。
     * <p>
     * 抽成纯函数是为了能直接对分支写单元测试，不必真的去改全局状态。
     */
    static Charset decide(String stdoutEncoding, boolean windows, Map<String, String> env, String override) {
        if (override != null && !override.isBlank()) {
            if (NATIVE.equalsIgnoreCase(override.trim())) {
                return null;
            }
            try {
                return Charset.forName(override.trim());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("不支持的输出编码: " + override, e);
            }
        }
        if (stdoutEncoding == null) {
            // 拿不到当前编码就什么都不做，改动风险大于收益
            return null;
        }
        Charset current;
        try {
            current = Charset.forName(stdoutEncoding);
        } catch (RuntimeException e) {
            return null;
        }
        if (StandardCharsets.UTF_8.equals(current)) {
            return null;
        }
        if (!windows) {
            // Unix 终端普遍按 UTF-8 解码；POSIX locale 下的 US-ASCII 反而不该要
            return StandardCharsets.UTF_8;
        }
        return looksLikeUtf8Terminal(env) ? StandardCharsets.UTF_8 : null;
    }

    /** Windows Terminal / Git Bash / MSYS / Cygwin 都按 UTF-8 解码输出。 */
    static boolean looksLikeUtf8Terminal(Map<String, String> env) {
        return env.containsKey("WT_SESSION")
                || env.containsKey("WT_PROFILE_ID")
                || env.containsKey("MSYSTEM")
                || env.containsKey("CYGWIN")
                || env.containsKey("TERM");
    }

    private static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("win");
    }

    private static PrintStream printStream(FileDescriptor descriptor, Charset charset) {
        return new PrintStream(new FileOutputStream(descriptor), true, charset);
    }
}
