package dev.filetidy.autostart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchCommandTest {

    // Path.toString() 会按平台改写分隔符，期望值也用 Path 生成，避免测试绑死某个平台
    private static final String DOWNLOADS = Path.of("/home/me/Downloads").toString();
    private static final String RULES = Path.of("/home/me/rules.yml").toString();

    @Test
    void composeBuildsJavaDashJarInvocation() {
        List<String> command = LaunchCommand.compose("/opt/java/bin/java", "/opt/filetidy.jar",
                Path.of("/home/me/Downloads"), null);

        assertEquals(List.of("/opt/java/bin/java", "-jar", "/opt/filetidy.jar", "tray", DOWNLOADS), command);
    }

    @Test
    void configPathIsAppendedWhenProvided() {
        List<String> command = LaunchCommand.compose("/opt/java/bin/java", "/opt/filetidy.jar",
                Path.of("/home/me/Downloads"), Path.of("/home/me/rules.yml"));

        assertEquals(List.of("/opt/java/bin/java", "-jar", "/opt/filetidy.jar",
                "tray", DOWNLOADS, "-c", RULES), command);
    }

    @Test
    void argsAlwaysStartWithTraySubcommand() {
        List<String> args = LaunchCommand.args(Path.of("/tmp/x"), null);

        assertEquals("tray", args.getFirst());
        assertFalse(args.contains("-c"));
        assertTrue(args.contains(Path.of("/tmp/x").toString()));
    }

    @Test
    void canonicalKeepsNullAsNull() {
        assertNull(LaunchCommand.canonical(null));
    }

    @Test
    void canonicalResolvesExistingDirectory(@TempDir Path dir) throws IOException {
        Path nested = Files.createDirectory(dir.resolve("子目录"));

        assertEquals(nested.toRealPath(), LaunchCommand.canonical(nested));
        assertTrue(LaunchCommand.canonical(nested).isAbsolute());
    }

    @Test
    void canonicalFallsBackForMissingPath() {
        Path missing = Path.of("no", "such", "dir").toAbsolutePath().resolve("nope");

        // 路径不存在时不能抛异常，退回绝对路径即可
        assertEquals(missing.normalize(), LaunchCommand.canonical(missing));
    }
}
