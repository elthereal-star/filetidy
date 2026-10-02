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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateDetectorTest {

    @TempDir
    Path dir;

    private void stamp(Path file, String instant) throws IOException {
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse(instant)));
    }

    private TidyConfig configWith(String action, String keep) {
        TidyConfig config = TidyConfig.defaultConfig();
        TidyConfig.Duplicates duplicates = new TidyConfig.Duplicates();
        duplicates.setAction(action);
        duplicates.setKeep(keep);
        duplicates.setTarget("98-重复文件");
        config.setDuplicates(duplicates);
        return config;
    }

    private DetectResult detect(TidyConfig config) throws IOException {
        List<MovePlan> plans = new RuleEngine(config).plan(dir);
        return new DuplicateDetector().detect(dir, config, plans);
    }

    @Test
    void reportsDuplicatesWithoutTouchingThemWhenActionIsReport() throws IOException {
        Files.writeString(dir.resolve("a.png"), "same-content");
        Files.writeString(dir.resolve("b.png"), "same-content");

        DetectResult result = detect(configWith("report", "oldest"));

        assertEquals(1, result.groups().size());
        assertEquals(1, result.groups().getFirst().redundant().size());
        assertEquals(2, result.plans().size(), "report 模式下计划不变");
        assertTrue(result.plans().stream()
                        .noneMatch(p -> p.target().toString().contains("98-重复文件")),
                "report 模式不应改写任何计划");
    }

    @Test
    void movesRedundantCopyKeepingOldest() throws IOException {
        Files.writeString(dir.resolve("old.png"), "same-content");
        Files.writeString(dir.resolve("new.png"), "same-content");
        stamp(dir.resolve("old.png"), "2026-01-01T00:00:00Z");
        stamp(dir.resolve("new.png"), "2026-06-01T00:00:00Z");

        DetectResult result = detect(configWith("move", "oldest"));

        DuplicateGroup group = result.groups().getFirst();
        assertEquals("old.png", group.keeper().getFileName().toString());
        assertEquals("new.png", group.redundant().getFirst().getFileName().toString());
        MovePlan moved = result.plans().stream()
                .filter(p -> p.ruleName().equals(DuplicateDetector.RULE_NAME))
                .findFirst()
                .orElseThrow();
        assertEquals("new.png", moved.source().getFileName().toString());
        assertTrue(moved.target().toString().contains("98-重复文件"), moved.target().toString());
    }

    @Test
    void keepsNewestWhenConfigured() throws IOException {
        Files.writeString(dir.resolve("old.png"), "same-content");
        Files.writeString(dir.resolve("new.png"), "same-content");
        stamp(dir.resolve("old.png"), "2026-01-01T00:00:00Z");
        stamp(dir.resolve("new.png"), "2026-06-01T00:00:00Z");

        DetectResult result = detect(configWith("move", "newest"));

        assertEquals("new.png", result.groups().getFirst().keeper().getFileName().toString());
        assertTrue(result.plans().stream()
                .anyMatch(p -> p.ruleName().equals(DuplicateDetector.RULE_NAME)
                        && p.source().getFileName().toString().equals("old.png")));
    }

    @Test
    void detectsDuplicatesAgainstAlreadyArchivedFiles() throws IOException {
        Path images = dir.resolve("01-图片");
        Files.createDirectories(images);
        Files.writeString(images.resolve("已有的.png"), "same-content");
        stamp(images.resolve("已有的.png"), "2026-01-01T00:00:00Z");
        Files.writeString(dir.resolve("刚下载的.png"), "same-content");
        stamp(dir.resolve("刚下载的.png"), "2026-06-01T00:00:00Z");

        DetectResult result = detect(configWith("move", "oldest"));

        assertEquals(1, result.groups().size(), "新文件与已归档文件内容相同应被发现");
        assertTrue(result.plans().stream()
                .anyMatch(p -> p.ruleName().equals(DuplicateDetector.RULE_NAME)
                        && p.source().getFileName().toString().equals("刚下载的.png")));
    }

    @Test
    void neverMovesAlreadyArchivedFiles() throws IOException {
        Path images = dir.resolve("01-图片");
        Files.createDirectories(images);
        // 已归档的反而更新，于是它成了"冗余副本"
        Files.writeString(images.resolve("archived.png"), "same-content");
        stamp(images.resolve("archived.png"), "2026-12-31T00:00:00Z");
        Files.writeString(dir.resolve("fresh.png"), "same-content");
        stamp(dir.resolve("fresh.png"), "2026-01-01T00:00:00Z");

        DetectResult result = detect(configWith("move", "oldest"));

        assertEquals(1, result.groups().size());
        assertEquals("fresh.png", result.groups().getFirst().keeper().getFileName().toString());
        assertTrue(result.plans().stream().noneMatch(p -> p.source().getFileName().toString().equals("archived.png")),
                "已归档的文件不能被移动");
        assertTrue(result.plans().stream().anyMatch(p -> p.target().toString().contains("01-图片")),
                "新文件仍应正常归档");
    }

    @Test
    void ignoresEmptyFilesAndSameSizeDifferentContent() throws IOException {
        Files.write(dir.resolve("empty1.png"), new byte[0]);
        Files.write(dir.resolve("empty2.png"), new byte[0]);
        Files.write(dir.resolve("a.png"), new byte[]{1, 2, 3, 4});
        Files.write(dir.resolve("b.png"), new byte[]{5, 6, 7, 8});

        DetectResult result = detect(configWith("report", "oldest"));

        assertFalse(result.hasDuplicates(), "空文件与同大小不同内容都不算重复");
    }

    @Test
    void doesNotRescanItsOwnDuplicateFolder() throws IOException {
        Path duplicates = dir.resolve("98-重复文件");
        Files.createDirectories(duplicates);
        Files.writeString(duplicates.resolve("already-moved.png"), "same-content");
        Files.writeString(dir.resolve("fresh.png"), "same-content");

        DetectResult result = detect(configWith("move", "oldest"));

        assertFalse(result.hasDuplicates(), "重复目录里的历史副本不应被反复报告");
    }

    @Test
    void detectionIsSkippedWhenNotConfigured() throws IOException {
        Files.writeString(dir.resolve("a.png"), "same");
        Files.writeString(dir.resolve("b.png"), "same");

        DetectResult result = detect(TidyConfig.defaultConfig());

        assertFalse(result.hasDuplicates());
        assertEquals(2, result.plans().size());
    }

    @Test
    void sha256MatchesKnownVector() throws IOException {
        Path file = dir.resolve("hello.txt");
        Files.writeString(file, "abc");

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                DuplicateDetector.sha256(file));
    }
}
