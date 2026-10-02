package dev.filetidy.config;

import dev.filetidy.FiletidyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TidyConfigTest {

    @TempDir
    Path dir;

    private TidyConfig write(String yaml) throws IOException {
        Path file = dir.resolve("rules.yml");
        Files.writeString(file, yaml);
        return TidyConfig.load(file);
    }

    @Test
    void loadsDatePatternLargeFilesAndDuplicates() throws IOException {
        TidyConfig config = write("""
                rules:
                  - name: images
                    target: 01-图片
                    extensions: [png]
                    datePattern: yyyy-MM
                largeFiles:
                  threshold: 50MB
                  target: 90-大文件
                duplicates:
                  action: move
                  target: 98-重复文件
                  keep: newest
                fallback: 99-其他
                skipHidden: false
                """);

        assertEquals("yyyy-MM", config.getRules().getFirst().getDatePattern());
        assertTrue(config.largeFilesEnabled());
        assertEquals(50L * 1024 * 1024, config.getLargeFiles().thresholdBytes());
        assertTrue(config.duplicatesEnabled());
        assertTrue(config.getDuplicates().movesRedundantCopies());
        assertFalse(config.getDuplicates().keepsOldest());
        assertFalse(config.isSkipHidden());
    }

    @Test
    void optionalSectionsAreAbsentByDefault() throws IOException {
        TidyConfig config = write("""
                rules:
                  - name: images
                    target: 01-图片
                    extensions: [png]
                fallback: 99-其他
                """);

        assertFalse(config.largeFilesEnabled());
        assertFalse(config.duplicatesEnabled());
        assertTrue(config.isSkipHidden(), "skipHidden 缺省为 true");
    }

    @Test
    void rejectsInvalidDatePattern() {
        // J 是保留但未定义的格式字母，DateTimeFormatter 会直接拒绝
        FiletidyException error = assertThrows(FiletidyException.class, () -> write("""
                rules:
                  - name: images
                    target: 01-图片
                    extensions: [png]
                    datePattern: "yyyy-MM-J"
                fallback: 99-其他
                """));
        assertTrue(error.getMessage().contains("datePattern"), error.getMessage());
        assertTrue(error.getMessage().contains("不是合法的日期格式"), error.getMessage());
    }

    @Test
    void rejectsDatePatternThatEscapesTheTargetFolder() {
        FiletidyException error = assertThrows(FiletidyException.class, () -> write("""
                rules:
                  - name: images
                    target: 01-图片
                    extensions: [png]
                    datePattern: "../../etc"
                fallback: 99-其他
                """));
        assertTrue(error.getMessage().contains("不能包含 .."), error.getMessage());
    }

    @Test
    void rejectsInvalidThreshold() {
        FiletidyException error = assertThrows(FiletidyException.class, () -> write("""
                rules:
                  - name: images
                    target: 01-图片
                    extensions: [png]
                largeFiles:
                  threshold: 大文件
                  target: 90-大文件
                fallback: 99-其他
                """));
        assertTrue(error.getMessage().contains("largeFiles.threshold"), error.getMessage());
    }

    @Test
    void rejectsMissingLargeFilesTarget() {
        FiletidyException error = assertThrows(FiletidyException.class, () -> write("""
                rules:
                  - name: images
                    target: 01-图片
                    extensions: [png]
                largeFiles:
                  threshold: 10MB
                  target: ""
                fallback: 99-其他
                """));
        assertTrue(error.getMessage().contains("largeFiles 缺少 target"), error.getMessage());
    }

    @Test
    void rejectsUnknownDuplicateAction() {
        FiletidyException error = assertThrows(FiletidyException.class, () -> write("""
                rules:
                  - name: images
                    target: 01-图片
                    extensions: [png]
                duplicates:
                  action: delete
                fallback: 99-其他
                """));
        assertTrue(error.getMessage().contains("duplicates.action"), error.getMessage());
    }

    @Test
    void rejectsUnknownKeepPolicy() {
        FiletidyException error = assertThrows(FiletidyException.class, () -> write("""
                rules:
                  - name: images
                    target: 01-图片
                    extensions: [png]
                duplicates:
                  keep: random
                fallback: 99-其他
                """));
        assertTrue(error.getMessage().contains("duplicates.keep"), error.getMessage());
    }

    @Test
    void moveActionRequiresTarget() {
        FiletidyException error = assertThrows(FiletidyException.class, () -> write("""
                rules:
                  - name: images
                    target: 01-图片
                    extensions: [png]
                duplicates:
                  action: move
                  target: ""
                fallback: 99-其他
                """));
        assertTrue(error.getMessage().contains("必须配置 target"), error.getMessage());
    }

    @Test
    void unknownKeyIsReportedAsParseFailureNotStackOverflow() {
        FiletidyException error = assertThrows(FiletidyException.class, () -> write("""
                rules:
                  - name: images
                    target: 01-图片
                    extensions: [png]
                fallback: 99-其他
                typoKey: true
                """));
        assertTrue(error.getMessage().contains("解析失败"), error.getMessage());
    }

    @Test
    void builtInRulesCarryNoOptionalSections() {
        TidyConfig config = TidyConfig.defaultConfig();

        assertNotNull(config.getRules());
        assertFalse(config.largeFilesEnabled(), "内置规则默认不开大文件分流");
        assertFalse(config.duplicatesEnabled(), "内置规则默认不开重复检测");
        assertTrue(config.getRules().stream().noneMatch(rule -> rule.getDatePattern() != null),
                "内置规则默认不做日期归档");
    }
}
