package dev.filetidy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 命令行层行为测试：确保用户级错误得到一行可读提示、退出码正确，
 * 且不会把 Java 堆栈甩到用户脸上。
 */
class CliTest {

    @TempDir
    Path dir;

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    private int run(String... args) {
        PrintStream previousOut = System.out;
        PrintStream previousErr = System.err;
        stdout.reset();
        stderr.reset();
        PrintStream outStream = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        PrintStream errStream = new PrintStream(stderr, true, StandardCharsets.UTF_8);
        try {
            // 子命令直接写 System.out，这里必须同时替换标准流与 picocli 的输出流
            System.setOut(outStream);
            System.setErr(errStream);
            return Main.createCommandLine()
                    .setOut(new PrintWriter(outStream, true))
                    .setErr(new PrintWriter(errStream, true))
                    .execute(args);
        } finally {
            System.setOut(previousOut);
            System.setErr(previousErr);
        }
    }

    private String out() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return stderr.toString(StandardCharsets.UTF_8);
    }

    @Test
    void missingDirectoryReportsReadableError() {
        int code = run("organize", dir.resolve("not-exist").toString());

        assertEquals(Main.EXIT_ERROR, code);
        assertTrue(err().contains("目录不存在或不是目录"), err());
        assertFalse(err().contains("at dev.filetidy"), err());
    }

    @Test
    void missingConfigFileReportsReadableError() {
        int code = run("organize", dir.toString(), "-c", dir.resolve("nope.yml").toString());

        assertEquals(Main.EXIT_ERROR, code);
        assertTrue(err().contains("规则配置文件不存在"), err());
        assertFalse(err().contains("at dev.filetidy"), err());
    }

    @Test
    void brokenConfigFileReportsReadableError() throws Exception {
        Path broken = dir.resolve("broken.yml");
        Files.writeString(broken, "rules: []\nfallback: \"\"\n");

        int code = run("organize", dir.toString(), "-c", broken.toString());

        assertEquals(Main.EXIT_ERROR, code);
        assertTrue(err().contains("没有配置任何 rules"), err());
    }

    @Test
    void dryRunAndWatchAreMutuallyExclusive() {
        int code = run("organize", dir.toString(), "--dry-run", "--watch");

        assertEquals(Main.EXIT_ERROR, code);
        assertTrue(err().contains("不能同时使用"), err());
    }

    @Test
    void missingRequiredParameterUsesUsageExitCode() {
        assertEquals(Main.EXIT_USAGE, run("organize"));
    }

    @Test
    void subcommandsSupportHelpWithoutPositionalArgument() {
        assertEquals(Main.EXIT_OK, run("organize", "--help"));
        assertTrue(out().contains("--dry-run"), out());

        assertEquals(Main.EXIT_OK, run("undo", "--help"));
        assertTrue(out().contains("撤销"), out());
    }

    @Test
    void undoOnUntidiedDirectoryIsNotAnError() {
        assertEquals(Main.EXIT_OK, run("undo", dir.toString()));
        assertTrue(out().contains("没有可撤销的记录"), out());
    }

    @Test
    void undoOnMissingDirectoryReportsReadableError() {
        int code = run("undo", dir.resolve("gone").toString());

        assertEquals(Main.EXIT_ERROR, code);
        assertTrue(err().contains("目录不存在或不是目录"), err());
        assertFalse(err().contains("at dev.filetidy"), err());
    }

    @Test
    void dryRunDoesNotTouchFiles() throws Exception {
        Path file = dir.resolve("a.png");
        Files.writeString(file, "x");

        int code = run("organize", dir.toString(), "--dry-run");

        assertEquals(Main.EXIT_OK, code);
        assertTrue(out().contains("[dry-run]"), out());
        assertTrue(out().contains("未做任何改动"), out());
        assertTrue(Files.exists(file), "预览模式不应该移动文件");
    }

    @Test
    void organizeMovesFilesAndReportsCount() throws Exception {
        Files.writeString(dir.resolve("a.png"), "x");
        Files.writeString(dir.resolve("b.xyz"), "y");

        int code = run("organize", dir.toString());

        assertEquals(Main.EXIT_OK, code);
        assertTrue(out().contains("已整理 2 个文件"), out());
        assertTrue(Files.exists(dir.resolve("01-图片").resolve("a.png")));
        assertTrue(Files.exists(dir.resolve("99-其他").resolve("b.xyz")));
    }

    @Test
    void noopRunPrintsFriendlyMessage() {
        int code = run("organize", dir.toString());

        assertEquals(Main.EXIT_OK, code);
        assertTrue(out().contains("没有需要整理的文件"), out());
    }
}
