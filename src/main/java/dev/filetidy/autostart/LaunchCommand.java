package dev.filetidy.autostart;

import dev.filetidy.FiletidyException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 组装「开机自启要执行什么」。
 * <p>
 * 优先用 {@code java -jar <jar>}，这样即使系统 PATH 里没有 java 也能起来；
 * 用 native image 打包时没有 jar，则退回当前进程自己的可执行文件。
 */
public final class LaunchCommand {

    private LaunchCommand() {
    }

    /**
     * 生成启动参数（不含可执行文件本身）。
     */
    public static List<String> args(Path directory, Path config) {
        List<String> args = new ArrayList<>(List.of("tray", directory.toString()));
        if (config != null) {
            args.add("-c");
            args.add(config.toString());
        }
        return args;
    }

    /** 解析当前运行方式，得到完整命令行（可执行文件 + 参数）。 */
    public static List<String> current(Path directory, Path config) {
        Path jar = runningJar();
        if (jar != null) {
            return compose(javaExecutable(), jar.toString(), directory, config);
        }
        String processCommand = currentProcessCommand();
        if (processCommand != null) {
            List<String> command = new ArrayList<>();
            command.add(processCommand);
            command.addAll(args(directory, config));
            return command;
        }
        throw new FiletidyException("无法定位 filetidy 的启动方式。"
                + "请从打包好的 target/filetidy.jar 运行后再执行 autostart。");
    }

    /** 纯函数版本，便于测试。 */
    static List<String> compose(String executable, String jarPath, Path directory, Path config) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("-jar");
        command.add(jarPath);
        command.addAll(args(directory, config));
        return command;
    }

    /**
     * 尽量把路径解析成规范形式，失败时退回绝对路径。
     * <p>
     * 启动项是一次写入、长期复用的文件，所以这里用 {@link Path#toRealPath()}：
     * Windows 上它能把 {@code C:\Users\BAISHA~1\...} 这类 8.3 短名还原成长名，
     * 避免用户在启动文件里看到一串看不懂的缩写，也避免在关闭了 8.3 短名生成的卷上失效。
     */
    public static Path canonical(Path path) {
        if (path == null) {
            return null;
        }
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    /** 当前进程如果是 {@code java -jar x.jar} 起来的，返回该 jar 的路径。 */
    static Path runningJar() {
        try {
            Path location = Path.of(LaunchCommand.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location) && location.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return location.toAbsolutePath().normalize();
            }
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
        return null;
    }

    private static String javaExecutable() {
        String home = System.getProperty("java.home");
        if (home == null || home.isBlank()) {
            return "java";
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            // javaw 不自带控制台窗口，适合开机自启的后台常驻
            Path javaw = Path.of(home, "bin", "javaw.exe");
            if (Files.isRegularFile(javaw)) {
                return javaw.toString();
            }
            return Path.of(home, "bin", "java.exe").toString();
        }
        return Path.of(home, "bin", "java").toString();
    }

    private static String currentProcessCommand() {
        return ProcessHandle.current().info().command().filter(text -> !text.isBlank()).orElse(null);
    }
}
