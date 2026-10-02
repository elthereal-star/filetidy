package dev.filetidy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

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

    private final CliHarness cli = new CliHarness();

    private String out() {
        return cli.out();
    }

    private String err() {
        return cli.err();
    }

    private int run(String... args) {
        return cli.run(args);
    }

    private void stamp(Path file, String instant) throws IOException {
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse(instant)));
    }

    private Path writeConfig(String yaml) throws IOException {
        Path file = dir.resolve("cli-rules.yml");
        Files.writeString(file, yaml);
        return file;
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
        Files.writeString(dir.resolve("broken.yml"), "rules: []\nfallback: \"\"\n");

        int code = run("organize", dir.toString(), "-c", dir.resolve("broken.yml").toString());

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

        assertEquals(Main.EXIT_OK, run("config", "--help"));
        assertTrue(out().contains("生效"), out());
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
        assertTrue(out().contains("整理计划"), out());
        assertTrue(out().contains("不会移动任何文件"), out());
        assertTrue(out().contains("->"), "预览应列出每个文件的去向: " + out());
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

    @Test
    void organizeGroupsResultByTargetFolder() throws Exception {
        Files.writeString(dir.resolve("a.png"), "x");
        Files.writeString(dir.resolve("b.png"), "y");
        Files.writeString(dir.resolve("c.pdf"), "z");

        run("organize", dir.toString());

        assertTrue(out().contains("01-图片"), out());
        assertTrue(out().contains("02-文档"), out());
    }

    @Test
    void quietSuppressesSuccessfulOutput() throws Exception {
        Files.writeString(dir.resolve("a.png"), "x");

        int code = run("organize", dir.toString(), "--quiet");

        assertEquals(Main.EXIT_OK, code);
        assertEquals("", out(), "静默模式不应输出任何内容");
    }

    @Test
    void quietStillReportsErrors() {
        int code = run("organize", dir.resolve("missing").toString(), "--quiet");

        assertEquals(Main.EXIT_ERROR, code);
        assertTrue(err().contains("目录不存在或不是目录"), err());
    }

    @Test
    void organizeWithDatePatternCreatesMonthFolder() throws Exception {
        Path file = dir.resolve("a.png");
        Files.writeString(file, "x");
        stamp(file, "2026-08-15T12:00:00Z");

        int code = run("organize", dir.toString(), "-c", writeConfig("""
                rules:
                  - name: images
                    target: 01-图片
                    extensions: [png]
                    datePattern: yyyy-MM
                fallback: 99-其他
                """).toString());

        assertEquals(Main.EXIT_OK, code);
        assertTrue(out().contains("01-图片/2026-08"), out());
        assertTrue(Files.exists(dir.resolve("01-图片").resolve("2026-08").resolve("a.png")));
    }

    @Test
    void organizeReportsDuplicateGroups() throws Exception {
        Files.writeString(dir.resolve("a.png"), "identical");
        Files.writeString(dir.resolve("b.png"), "identical");

        int code = run("organize", dir.toString(), "-c", writeConfig("""
                rules:
                  - name: images
                    target: 01-图片
                    extensions: [png]
                duplicates:
                  action: report
                fallback: 99-其他
                """).toString());

        assertEquals(Main.EXIT_OK, code);
        assertTrue(out().contains("发现 1 组内容重复的文件"), out());
        assertTrue(Files.exists(dir.resolve("01-图片").resolve("a.png")), "report 模式不应改变归档行为");
        assertTrue(Files.exists(dir.resolve("01-图片").resolve("b.png")), "report 模式不应改变归档行为");
    }

    @Test
    void noDuplicatesFlagSkipsDetection() throws Exception {
        Files.writeString(dir.resolve("a.png"), "identical");
        Files.writeString(dir.resolve("b.png"), "identical");

        run("organize", dir.toString(), "--no-duplicates", "-c", writeConfig("""
                rules:
                  - name: images
                    target: 01-图片
                    extensions: [png]
                duplicates:
                  action: report
                fallback: 99-其他
                """).toString());

        assertFalse(out().contains("重复"), out());
    }

    @Test
    void undoListShowsBatches() throws Exception {
        Files.writeString(dir.resolve("a.png"), "x");
        run("organize", dir.toString());

        int code = run("undo", dir.toString(), "--list");

        assertEquals(Main.EXIT_OK, code);
        assertTrue(out().contains("历史整理批次"), out());
        assertTrue(out().contains("#1"), out());
    }

    @Test
    void undoListOnFreshDirectorySaysSo() {
        assertEquals(Main.EXIT_OK, run("undo", dir.toString(), "--list"));
        assertTrue(out().contains("没有历史整理记录"), out());
    }

    @Test
    void undoDryRunLeavesEverythingInPlace() throws Exception {
        Files.writeString(dir.resolve("a.png"), "x");
        run("organize", dir.toString());

        int code = run("undo", dir.toString(), "--dry-run");

        assertEquals(Main.EXIT_OK, code);
        assertTrue(out().contains("将撤销最近一次整理"), out());
        assertTrue(out().contains("未做任何改动"), out());
        assertTrue(Files.exists(dir.resolve("01-图片").resolve("a.png")), "预览不应真的回滚");
    }

    @Test
    void configPrintShowsEffectiveRules() {
        int code = run("config");

        assertEquals(Main.EXIT_OK, code);
        assertTrue(out().contains("内置规则 rules-default.yml"), out());
        assertTrue(out().contains("01-图片"), out());
        assertTrue(out().contains("largeFiles"), out());
        assertTrue(out().contains("duplicates"), out());
    }

    @Test
    void configPrintReflectsCustomFile() throws Exception {
        Path config = writeConfig("""
                rules:
                  - name: movies
                    target: 影片
                    extensions: [mkv]
                largeFiles:
                  threshold: 3MB
                  target: 大文件
                duplicates:
                  action: move
                  keep: newest
                fallback: 其他
                """);

        int code = run("config", "-c", config.toString());

        assertEquals(Main.EXIT_OK, code);
        assertTrue(out().contains("movies"), out());
        assertTrue(out().contains("3 MB"), out());
        assertTrue(out().contains("keep=newest"), out());
    }
}
