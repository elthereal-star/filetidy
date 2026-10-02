package dev.filetidy.core;

import dev.filetidy.config.TidyConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日期归档与大文件分流的计划生成。
 */
class RuleEngineAdvancedTest {

    @TempDir
    Path dir;

    private MovePlan planOf(List<MovePlan> plans, String fileName) {
        return plans.stream()
                .filter(p -> p.source().getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少计划: " + fileName + "，实际: " + plans));
    }

    private void stamp(Path file, String instant) throws IOException {
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse(instant)));
    }

    private TidyConfig configWithDatePattern(String pattern) {
        TidyConfig config = TidyConfig.defaultConfig();
        config.getRules().stream()
                .filter(rule -> rule.getName().equals("images"))
                .forEach(rule -> rule.setDatePattern(pattern));
        return config;
    }

    @Test
    void datePatternAddsMonthSubfolder() throws IOException {
        Files.writeString(dir.resolve("a.png"), "x");
        stamp(dir.resolve("a.png"), "2026-08-15T12:00:00Z");

        Path target = planOf(new RuleEngine(configWithDatePattern("yyyy-MM")).plan(dir), "a.png").target();

        // 月中时间点在任何时区偏移下都落在同一个月，断言稳定
        assertEquals("2026-08", target.getParent().getFileName().toString(), target.toString());
        assertEquals("01-图片", target.getParent().getParent().getFileName().toString(), target.toString());
    }

    @Test
    void datePatternCanCreateNestedFolders() throws IOException {
        Files.writeString(dir.resolve("a.png"), "x");
        stamp(dir.resolve("a.png"), "2026-08-15T12:00:00Z");

        Path target = planOf(new RuleEngine(configWithDatePattern("yyyy/MM")).plan(dir), "a.png").target();

        assertTrue(target.getParent().getFileName().toString().equals("08"), target.toString());
        assertTrue(target.getParent().getParent().getFileName().toString().equals("2026"), target.toString());
    }

    @Test
    void rulesWithoutDatePatternStayFlat() throws IOException {
        Files.writeString(dir.resolve("a.pdf"), "x");

        Path target = planOf(new RuleEngine(TidyConfig.defaultConfig()).plan(dir), "a.pdf").target();

        assertEquals("02-文档", target.getParent().getFileName().toString());
    }

    @Test
    void largeFilesBypassExtensionRules() throws IOException {
        Files.writeString(dir.resolve("small.png"), "x");
        Files.write(dir.resolve("huge.png"), new byte[4096]);

        TidyConfig config = TidyConfig.defaultConfig();
        TidyConfig.LargeFiles largeFiles = new TidyConfig.LargeFiles();
        largeFiles.setThreshold("1KB");
        largeFiles.setTarget("90-大文件");
        config.setLargeFiles(largeFiles);

        List<MovePlan> plans = new RuleEngine(config).plan(dir);

        assertEquals("90-大文件", planOf(plans, "huge.png").target().getParent().getFileName().toString());
        assertEquals(RuleEngine.LARGE_FILES_RULE, planOf(plans, "huge.png").ruleName());
        assertEquals("01-图片", planOf(plans, "small.png").target().getParent().getFileName().toString());
    }

    @Test
    void largeFileThresholdIncludesBoundary() throws IOException {
        Files.write(dir.resolve("under.png"), new byte[1023]);
        Files.write(dir.resolve("exactly.png"), new byte[1024]);

        TidyConfig config = TidyConfig.defaultConfig();
        TidyConfig.LargeFiles largeFiles = new TidyConfig.LargeFiles();
        largeFiles.setThreshold("1KB");
        config.setLargeFiles(largeFiles);

        List<MovePlan> plans = new RuleEngine(config).plan(dir);

        assertEquals("01-图片", planOf(plans, "under.png").target().getParent().getFileName().toString());
        assertEquals("90-大文件", planOf(plans, "exactly.png").target().getParent().getFileName().toString(),
                "达到阈值即视为大文件");
    }

    @Test
    void largeFilesAreDisabledWhenNotConfigured() throws IOException {
        Files.write(dir.resolve("huge.png"), new byte[4096]);

        List<MovePlan> plans = new RuleEngine(TidyConfig.defaultConfig()).plan(dir);

        assertEquals("01-图片", planOf(plans, "huge.png").target().getParent().getFileName().toString());
    }
}
