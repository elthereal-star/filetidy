package dev.filetidy.autostart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutostartManagerTest {

    @TempDir
    Path dir;

    /** 各平台共用一份「没有空格」的命令，用来验证不必要时不加引号。 */
    private static final List<String> PLAIN_COMMAND =
            List.of("/usr/bin/java", "-jar", "/opt/filetidy/filetidy.jar", "tray", "/home/me/Downloads");

    private AutostartManager manager(AutostartManager.Platform platform) {
        String name = switch (platform) {
            case WINDOWS -> "filetidy.cmd";
            case MACOS -> "dev.filetidy.plist";
            case LINUX -> "filetidy.desktop";
        };
        return new AutostartManager(platform, dir.resolve("entry").resolve(name), PLAIN_COMMAND);
    }

    private AutostartManager withCommand(AutostartManager.Platform platform, List<String> command) {
        return new AutostartManager(platform, dir.resolve("entry").resolve("filetidy"), command);
    }

    @Test
    void windowsScriptUsesCrlf() {
        String script = manager(AutostartManager.Platform.WINDOWS).render();

        assertTrue(script.startsWith("@echo off\r\n"), script);
        assertTrue(script.contains("\r\nstart \"\" /min "), script);
        assertFalse(script.replace("\r\n", "").contains("\n"), "不应残留裸 LF");
    }

    @Test
    void windowsScriptQuotesOnlyWhenNecessary() {
        String plain = manager(AutostartManager.Platform.WINDOWS).render();
        assertTrue(plain.contains("start \"\" /min /usr/bin/java -jar /opt/filetidy/filetidy.jar tray "
                + "/home/me/Downloads"), plain);

        String spaced = withCommand(AutostartManager.Platform.WINDOWS, List.of(
                "C:\\Program Files\\Java\\bin\\javaw.exe", "-jar",
                "C:\\my tools\\filetidy.jar", "tray", "C:\\Users\\me\\My Downloads")).render();

        assertTrue(spaced.contains("\"C:\\Program Files\\Java\\bin\\javaw.exe\""), spaced);
        assertTrue(spaced.contains("\"C:\\Users\\me\\My Downloads\""), spaced);
        assertTrue(spaced.contains(" -jar "), "没有特殊字符的参数不该加引号: " + spaced);
        assertTrue(spaced.contains("\\my tools\\filetidy.jar"), "反斜杠在 .cmd 里不能转义: " + spaced);
    }

    @Test
    void macPlistIsWellFormedAndEscapesValues() {
        AutostartManager manager = withCommand(AutostartManager.Platform.MACOS,
                List.of("/usr/bin/java", "-jar", "/tmp/a&b<c>.jar", "tray", "/Users/me/Downloads"));

        String plist = manager.render();

        assertTrue(plist.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"), plist);
        assertTrue(plist.contains("<key>Label</key>"), plist);
        assertTrue(plist.contains("<string>dev.filetidy</string>"), plist);
        assertTrue(plist.contains("<key>RunAtLoad</key>"), plist);
        assertTrue(plist.contains("<string>/tmp/a&amp;b&lt;c&gt;.jar</string>"), plist);
        assertFalse(plist.contains("a&b<c>.jar"), "XML 特殊字符必须转义");
    }

    @Test
    void linuxDesktopEntryHasRequiredKeys() {
        String entry = manager(AutostartManager.Platform.LINUX).render();

        assertTrue(entry.startsWith("[Desktop Entry]"), entry);
        assertTrue(entry.contains("Type=Application"), entry);
        assertTrue(entry.contains("Exec=/usr/bin/java -jar /opt/filetidy/filetidy.jar tray /home/me/Downloads"), entry);
        assertTrue(entry.contains("Terminal=false"), entry);
    }

    @Test
    void linuxDesktopEntryEscapesQuotedValues() {
        AutostartManager manager = withCommand(AutostartManager.Platform.LINUX,
                List.of("/usr/bin/java", "-jar", "/opt/my tools/filetidy.jar", "tray", "/home/me/My Downloads"));

        String entry = manager.render();

        assertTrue(entry.contains("\"/opt/my tools/filetidy.jar\""), entry);
        assertTrue(entry.contains("\"/home/me/My Downloads\""), entry);
    }

    @Test
    void enableWritesFileAndDisableRemovesIt() throws IOException {
        AutostartManager manager = manager(AutostartManager.Platform.LINUX);

        assertFalse(manager.isEnabled());
        Path written = manager.enable();

        assertTrue(manager.isEnabled());
        assertTrue(Files.isRegularFile(written));
        assertTrue(Files.readString(written).contains("tray"));

        assertTrue(manager.disable());
        assertFalse(manager.isEnabled(), "取消后入口文件不该还在");
        assertFalse(manager.disable(), "重复取消应返回 false 而不是报错");
    }

    @Test
    void enableCreatesMissingParentDirectories() throws IOException {
        Path nested = dir.resolve("a/b/c").resolve("filetidy.desktop");
        AutostartManager manager = new AutostartManager(
                AutostartManager.Platform.LINUX, nested, PLAIN_COMMAND);

        manager.enable();

        assertTrue(Files.isRegularFile(nested));
    }

    @Test
    void defaultEntryFilesLiveInPerUserLocations() {
        assertTrue(normalized(AutostartManager.Platform.WINDOWS)
                .endsWith("Microsoft/Windows/Start Menu/Programs/Startup/filetidy.cmd"));
        assertTrue(normalized(AutostartManager.Platform.MACOS).endsWith("Library/LaunchAgents/dev.filetidy.plist"));
        assertTrue(normalized(AutostartManager.Platform.LINUX).endsWith(".config/autostart/filetidy.desktop"));
    }

    private String normalized(AutostartManager.Platform platform) {
        return AutostartManager.defaultEntryFile(platform).toString().replace('\\', '/');
    }

    @Test
    void currentPlatformMatchesOperatingSystem() {
        String os = System.getProperty("os.name", "").toLowerCase();
        AutostartManager.Platform platform = AutostartManager.currentPlatform();

        if (os.contains("win")) {
            assertEquals(AutostartManager.Platform.WINDOWS, platform);
        } else if (os.contains("mac")) {
            assertEquals(AutostartManager.Platform.MACOS, platform);
        } else {
            assertEquals(AutostartManager.Platform.LINUX, platform);
        }
    }
}
